from fastapi import FastAPI, Request
from fastapi.responses import Response
from fastapi.middleware.cors import CORSMiddleware

from .routes import scan
from .routes import device
from .database import Base, engine

# Create ONE FastAPI app
app = FastAPI(title="BeVigil Clone - STANDARD Backend")

@app.middleware("http")
async def allow_large_uploads(request: Request, call_next):
    request.scope["client_max_size"] = 500 * 1024 * 1024   # 500 MB
    return await call_next(request)

# ---- Fix 1: Add CORS ----
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ---- Fix 2: Allow Large APK Uploads (up to 500 MB) ----
@app.middleware("http")
async def limit_body_middleware(request: Request, call_next):
    max_body_size = 500 * 1024 * 1024  # 500 MB
    content_length = request.headers.get("content-length")

    if content_length and int(content_length) > max_body_size:
        return Response("File too large", status_code=413)

    # Internal override for Starlette
    request.scope["client_max_size"] = max_body_size

    return await call_next(request)

# ---- DB Init ----
Base.metadata.create_all(bind=engine)

# ---- Routers ----
app.include_router(scan.router)
app.include_router(device.router)

# ---- Root Test ----
@app.get("/")
def home():
    return {"message": "Standard backend running"}
