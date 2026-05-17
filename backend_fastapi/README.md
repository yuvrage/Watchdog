STANDARD VERSION 
-----------------------------------------
This standard backend uses FastAPI + Celery + Redis and performs APK analysis (androguard) + secret detection.
Run:
1. python -m venv venv && source venv/bin/activate (or venv\Scripts\activate)
2. pip install -r requirements.txt
3. Start memurai: memurai
4. Start uvicorn: uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
5. Start celery: celery -A app.workers.celery_worker.celery_app worker --loglevel=info --pool=solo
