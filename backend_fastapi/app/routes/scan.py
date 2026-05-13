# app/routes/scan.py
import os, uuid
from fastapi import APIRouter, UploadFile, File, Form, HTTPException
from fastapi.responses import JSONResponse
from app.workers.celery_worker import celery_app
from app.workers.tasks import analyze_apk_task

router = APIRouter(prefix="/scan", tags=["Scan"])
UPLOAD_DIR = "uploads_std"
os.makedirs(UPLOAD_DIR, exist_ok=True)

@router.post("/apk")
async def scan_apk(
    apk: UploadFile = File(...),
    device_id: str | None = Form(None),
):
    if not apk.filename.endswith(".apk"):
        raise HTTPException(status_code=400, detail="Only .apk files allowed")
    unique_name = f"{uuid.uuid4()}_{apk.filename}"
    file_path = os.path.join(UPLOAD_DIR, unique_name)
    with open(file_path, "wb") as f:
        f.write(await apk.read())

    task = analyze_apk_task.delay(file_path=file_path, device_id=device_id)
    return {"message": "Scan started", "task_id": task.id, "device_id": device_id}

@router.get("/result/{task_id}")
def get_result(task_id: str):
    """
    Return stable JSON:
    {
      "status": "PENDING|STARTED|SUCCESS|FAILURE",
      "result": {...} or null,
      "error": null or "message",
      "task_id": "..."
    }
    """
    res = celery_app.AsyncResult(task_id)
    status = res.status

    # Default safe payload
    payload = {"status": status, "result": None, "error": None, "task_id": task_id}

    # If successful, ensure we return only JSON-serializable data
    try:
        if status == "SUCCESS":
            raw = res.result
            # convert exceptions / non-serializable to string if necessary
            if raw is None:
                payload["result"] = None
            else:
                payload["result"] = raw  # tasks guarantee JSON-serializable dict
        elif status == "FAILURE":
            # .result may be exception object; convert to string
            try:
                payload["error"] = str(res.result)
            except Exception:
                payload["error"] = "Task failed (unknown error)"
        else:
            # PENDING / STARTED / RETRY - no result yet
            payload["result"] = None
    except Exception as e:
        payload["status"] = "ERROR"
        payload["error"] = f"Result retrieval error: {e}"
        payload["result"] = None

    return JSONResponse(content=payload)
