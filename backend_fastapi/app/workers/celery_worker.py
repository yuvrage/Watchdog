from celery import Celery

celery_app = Celery(
    "scanner",
    broker="redis://localhost:6379/0",
    backend="redis://localhost:6379/1"
)

# auto-discover tasks
celery_app.autodiscover_tasks(['app.workers'])
