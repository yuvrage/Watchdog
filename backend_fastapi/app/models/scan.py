from fastapi import APIRouter
from celery.result import AsyncResult
from app.workers.celery_worker import celery_app

router = APIRouter()


@router.get("/result/{task_id}")
def get_scan_result(task_id: str):
    result = AsyncResult(task_id, app=celery_app)

    if result.state == "PENDING":
        return {
            "status": "PENDING",
            "result": None,
            "error": None,
            "task_id": task_id
        }

    if result.state == "STARTED":
        return {
            "status": "STARTED",
            "result": None,
            "error": None,
            "task_id": task_id
        }

    if result.state == "SUCCESS":
        return {
            "status": "SUCCESS",
            "result": result.result,
            "error": None,
            "task_id": task_id
        }

    if result.state == "FAILURE":
        return {
            "status": "FAILURE",
            "result": None,
            "error": str(result.result),
            "task_id": task_id
        }

    return {
        "status": result.state,
        "result": None,
        "error": None,
        "task_id": task_id
    }
