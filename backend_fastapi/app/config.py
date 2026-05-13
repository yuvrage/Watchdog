import os

class Settings:
    REDIS_BROKER_URL = os.getenv("REDIS_BROKER_URL", "redis://localhost:6379/0")
    REDIS_BACKEND_URL = os.getenv("REDIS_BACKEND_URL", "redis://localhost:6379/1")
    DATABASE_URL = os.getenv("DATABASE_URL", "sqlite:///./scan_results.db")
    PROJECT_NAME = "BeVigil Standard"

settings = Settings()
