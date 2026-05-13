from datetime import datetime

from sqlalchemy import Column, DateTime, Float, Integer, String

from app.database import Base


class ScanRecord(Base):
    """
    Simple per-scan record used to build per-device summaries.

    Each APK scan can optionally be tagged with a `device_id`. We persist the
    key risk fields so the API can compute aggregates like:
    - average risk score
    - counts of dangerous / moderate / safe apps
    """

    __tablename__ = "scan_records"

    id = Column(Integer, primary_key=True, index=True)
    device_id = Column(String, index=True, nullable=False)
    package_name = Column(String, index=True, nullable=True)
    app_name = Column(String, nullable=True)
    risk_score = Column(Float, nullable=False)
    risk_level = Column(String, nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)



