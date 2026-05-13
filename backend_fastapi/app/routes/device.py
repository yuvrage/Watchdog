from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import func
from sqlalchemy.orm import Session

from app.database import SessionLocal
from app.models.device_summary import ScanRecord

router = APIRouter(prefix="/device", tags=["Device"])


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


@router.get("/summary/{device_id}")
def get_device_summary(device_id: str, db: Session = Depends(get_db)):
    """
    Aggregate scan statistics for a given device.

    Response shape:
    {
      "device_id": "...",
      "rating": 7.1,           # 0–10, higher is safer
      "status": "SAFE",        # SAFE | MODERATE | DANGEROUS
      "totals": {
        "total_scanned": 209,
        "dangerous": 0,
        "moderate": 88,
        "safe": 121
      }
    }
    """
    records = (
        db.query(ScanRecord)
        .filter(ScanRecord.device_id == device_id)
        .order_by(ScanRecord.created_at.desc())
        .all()
    )

    if not records:
        raise HTTPException(status_code=404, detail="No scans found for this device")

    total = len(records)
    dangerous = 0
    moderate = 0
    safe = 0
    total_risk = 0.0

    def normalize_score(score):
        """Normalize old scores to 0-10 range if they're in old format."""
        score = float(score or 0.0)
        if score > 10.0:
            if score <= 18.0:
                score = (score / 18.0) * 10.0
            elif score <= 100.0:
                score = (score / 100.0) * 10.0
            else:
                score = 10.0
        return max(0.0, min(10.0, score))
    
    for r in records:
        score = normalize_score(r.risk_score)
        total_risk += score
        # Use risk_level for categorization (more reliable than score thresholds)
        level = (r.risk_level or "").upper()
        if level == "HIGH" or level == "DANGEROUS":
            dangerous += 1
        elif level == "MODERATE":
            moderate += 1
        else:  # LOW or SAFE
            safe += 1

    avg_risk = total_risk / total if total > 0 else 0.0

    # Risk score is now 0-10 scale (higher is worse)
    # Convert to safety rating 0-10 (higher is safer)
    rating = max(0.0, 10.0 - avg_risk)

    if rating >= 7.0:
        status = "SAFE"
    elif rating >= 4.0:
        status = "MODERATE"
    else:
        status = "DANGEROUS"

    return {
        "device_id": device_id,
        "rating": round(rating, 1),
        "status": status,
        "totals": {
            "total_scanned": total,
            "dangerous": dangerous,
            "moderate": moderate,
            "safe": safe,
        },
    }


@router.get("/apps/{device_id}")
def get_device_apps(device_id: str, db: Session = Depends(get_db)):
    """
    Return the latest scan record per package for this device.
    [
      {
        "package_name": "...",
        "app_name": "...",
        "risk_score": 42.0,
        "risk_level": "MODERATE"
      },
      ...
    ]
    """
    subq = (
        db.query(
            ScanRecord.package_name.label("pkg"),
            func.max(ScanRecord.id).label("max_id"),
        )
        .filter(ScanRecord.device_id == device_id)
        .group_by(ScanRecord.package_name)
        .subquery()
    )

    records = (
        db.query(ScanRecord)
        .join(subq, ScanRecord.id == subq.c.max_id)
        .order_by(ScanRecord.risk_score.desc())
        .all()
    )

    def normalize_score(score):
        """Normalize old scores to 0-10 range if they're in old format."""
        score = float(score or 0.0)
        # If score is > 10, it might be from old format (0-18 or 0-100)
        # Normalize to 0-10
        if score > 10.0:
            # If it's in 0-18 range, scale linearly
            if score <= 18.0:
                score = (score / 18.0) * 10.0
            # If it's in 0-100 range, scale linearly
            elif score <= 100.0:
                score = (score / 100.0) * 10.0
            # Cap at 10
            else:
                score = 10.0
        return max(0.0, min(10.0, score))
    
    return [
        {
            "package_name": r.package_name,
            "app_name": r.app_name,
            "risk_score": round(normalize_score(r.risk_score), 1),
            "risk_level": r.risk_level or "SAFE",
        }
        for r in records
    ]


@router.delete("/clear/{device_id}")
def clear_device_scans(device_id: str, db: Session = Depends(get_db)):
    """
    Delete all scan records for a given device.
    Used when starting a fresh "scan all device" operation.
    """
    deleted_count = (
        db.query(ScanRecord)
        .filter(ScanRecord.device_id == device_id)
        .delete()
    )
    db.commit()
    
    return {
        "message": f"Cleared {deleted_count} scan records for device {device_id}",
        "deleted_count": deleted_count,
    }

