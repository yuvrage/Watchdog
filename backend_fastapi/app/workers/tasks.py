# app/workers/tasks.py
from app.workers.celery_worker import celery_app
from app.services.apk_analyzer import analyze_apk
from app.services.secret_detector import detect_secrets_from_bytes
from app.services.risk_engine import calculate_risk_score   # <-- ADDED
from app.database import SessionLocal
from app.models.device_summary import ScanRecord

def _normalize_apk_info(apk_info):
    if not apk_info or not isinstance(apk_info, dict):
        apk_info = {"package": None, "app_name": None, "version_name": None,
                    "permissions": [], "activities": [], "services": [],
                    "receivers": [], "providers": [], "endpoints": [],
                    "trackers": [], "raw_errors": None}
    for k in ["permissions", "activities", "services", "receivers", "providers", "endpoints", "trackers"]:
        apk_info[k] = list(apk_info.get(k) or [])
    apk_info["package"] = apk_info.get("package")
    apk_info["app_name"] = apk_info.get("app_name")
    apk_info["version_name"] = apk_info.get("version_name")
    apk_info["raw_errors"] = apk_info.get("raw_errors")
    return apk_info

def _default_secrets(secrets):
    if not secrets or not isinstance(secrets, dict):
        return {"firebase": [], "aws": [], "tokens": [], "jwt": [], "stripe": []}
    return {
        "firebase": list(secrets.get("firebase") or []),
        "aws": list(secrets.get("aws") or []),
        "tokens": list(secrets.get("tokens") or []),
        "jwt": list(secrets.get("jwt") or []),
        "stripe": list(secrets.get("stripe") or []),
    }

@celery_app.task(name="app.workers.tasks.analyze_apk_task")
def analyze_apk_task(file_path: str, device_id: str | None = None) -> dict:
    """
    Celery task that performs full APK analysis + secret scan + BeVigil-style
    advanced risk scoring.
    """

    # STEP 1 — APK Analysis
    apk_info = analyze_apk(file_path)
    apk_info = _normalize_apk_info(apk_info)

    # STEP 2 — Secret Detection (Raw bytes)
    try:
        with open(file_path, "rb") as f:
            raw = f.read()
        secrets = detect_secrets_from_bytes(raw)
    except Exception as e:
        secrets = {"error": str(e)}

    secrets = _default_secrets(secrets)

    # STEP 3 — NEW ADVANCED RISK ENGINE (BeVigil Style)
    risk = calculate_risk_score(apk_info, secrets)

    apk_info["risk_score"] = risk["risk_score"]
    apk_info["risk_level"] = risk["risk_level"]
    apk_info["risk_components"] = risk["components"]

    # Persist a lightweight record for per-device summaries
    try:
        if device_id:
            db = SessionLocal()
            try:
                record = ScanRecord(
                    device_id=device_id,
                    package_name=apk_info.get("package"),
                    app_name=apk_info.get("app_name"),
                    risk_score=apk_info["risk_score"],
                    risk_level=apk_info["risk_level"],
                )
                db.add(record)
                db.commit()
            finally:
                db.close()
    except Exception:
        # Summary persistence must never break the main task
        pass

    # FINAL OUTPUT
    result = {
        "apk": apk_info,
        "secrets": secrets,
        "risk_score": apk_info["risk_score"],
        "risk_level": apk_info["risk_level"],
        "risk_components": apk_info["risk_components"],
        "meta": {"file_path": file_path, "device_id": device_id},
    }

    return result
