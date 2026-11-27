"""Orb Mobile — FastAPI server.

Serves the installable mobile web app and the assistant's HTTP/WebSocket API.
The brain lives here; the phone is the face and the microphone.
"""
from __future__ import annotations

import asyncio
import base64
import os
import time
from contextlib import asynccontextmanager
from typing import Any, Optional

from fastapi import (
    Depends, FastAPI, File, HTTPException, Request, UploadFile, WebSocket,
    WebSocketDisconnect,
)
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, Response
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field

from .orb import agent as agent_module
from .orb import llm, memory, settings, tools

VERSION = "1.0.0"
MAX_AUDIO_BYTES = 15 * 1024 * 1024
AUDIO_TYPES = {
    "audio/wav", "audio/x-wav", "audio/wave", "audio/mpeg", "audio/mp3",
    "audio/ogg", "audio/flac", "audio/aac", "audio/aiff", "audio/mp4",
}


class Hub:
    """Connected phones, for reminders and future proactive messages."""

    def __init__(self) -> None:
        self.clients: set[WebSocket] = set()

    async def broadcast(self, payload: dict[str, Any]) -> None:
        dead: list[WebSocket] = []
        for socket in list(self.clients):
            try:
                await socket.send_json(payload)
            except Exception:
                dead.append(socket)
        for socket in dead:
            self.clients.discard(socket)


hub = Hub()
agent = agent_module.Agent()


async def _reminder_loop() -> None:
    while True:
        try:
            for reminder in tools.due_reminders():
                await hub.broadcast({"type": "reminder", "reminder": reminder})
        except Exception as exc:
            print(f"[reminders] loop error: {exc}")
        await asyncio.sleep(20)


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings.ensure_dirs()
    task = asyncio.create_task(_reminder_loop())
    try:
        yield
    finally:
        task.cancel()


app = FastAPI(title="Orb Mobile", version=VERSION, lifespan=lifespan)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


def _token() -> str:
    return os.environ.get("ORB_TOKEN", "").strip()


async def require_token(request: Request) -> None:
    expected = _token()
    if not expected:
        return
    header = request.headers.get("authorization", "")
    supplied = header[7:].strip() if header.lower().startswith("bearer ") else ""
    supplied = supplied or request.query_params.get("token", "")
    if supplied != expected:
        raise HTTPException(status_code=401, detail="token inválido")


class ChatRequest(BaseModel):
    message: str = Field(..., min_length=1, max_length=8000)


class TTSRequest(BaseModel):
    text: str = Field(..., min_length=1, max_length=2000)
    voice: str = ""


class MemoryRequest(BaseModel):
    key: str = Field(..., min_length=1, max_length=120)
    value: str = Field(..., min_length=1, max_length=1200)
    category: str = "notes"


class ConfigRequest(BaseModel):
    assistant_name: Optional[str] = None
    user_name: Optional[str] = None
    language: Optional[str] = None
    voice: Optional[str] = None
    tts_engine: Optional[str] = None
    persona: Optional[str] = None
    api_key: Optional[str] = None


@app.get("/api/health")
async def health() -> dict[str, Any]:
    return {
        "status": "ok",
        "name": settings.assistant_name(),
        "version": VERSION,
        "api_key_set": bool(settings.api_key()),
        "auth_required": bool(_token()),
    }


@app.get("/api/config", dependencies=[Depends(require_token)])
async def get_config() -> dict[str, Any]:
    return settings.public_settings()


@app.put("/api/config", dependencies=[Depends(require_token)])
async def put_config(body: ConfigRequest) -> dict[str, Any]:
    values = {k: v for k, v in body.model_dump().items() if v is not None}
    if "tts_engine" in values and values["tts_engine"] not in ("browser", "gemini"):
        raise HTTPException(status_code=400, detail="tts_engine deve ser browser ou gemini")
    if "api_key" in values:
        values["api_key"] = str(values["api_key"]).strip()
    settings.save(values)
    return settings.public_settings()


@app.post("/api/chat", dependencies=[Depends(require_token)])
async def chat(body: ChatRequest) -> dict[str, Any]:
    result = await asyncio.to_thread(agent.chat, body.message.strip())
    return result


@app.post("/api/voice", dependencies=[Depends(require_token)])
async def voice(
    audio: UploadFile = File(...),
    speak: bool = False,
) -> dict[str, Any]:
    data = await audio.read()
    if not data:
        raise HTTPException(status_code=400, detail="áudio vazio")
    if len(data) > MAX_AUDIO_BYTES:
        raise HTTPException(status_code=413, detail="áudio muito grande")
    mime = (audio.content_type or "audio/wav").lower()
    if mime not in AUDIO_TYPES:
        raise HTTPException(
            status_code=415,
            detail=f"formato {mime} não suportado; envie audio/wav",
        )

    transcript = await asyncio.to_thread(agent.transcribe, data, mime)
    if not transcript:
        return {
            "transcript": "",
            "reply": "Não consegui ouvir nada. Tente falar de novo, mais perto do microfone.",
            "tools": [],
            "ok": False,
            "audio": None,
        }

    result = await asyncio.to_thread(agent.chat, transcript)
    if speak and result.get("ok"):
        wav = await asyncio.to_thread(llm.speak, result.get("reply", ""))
        result["audio"] = base64.b64encode(wav).decode("ascii") if wav else None
    else:
        result["audio"] = None
    result["transcript"] = transcript
    return result


@app.post("/api/tts", dependencies=[Depends(require_token)])
async def tts(body: TTSRequest) -> Response:
    wav = await asyncio.to_thread(llm.speak, body.text, body.voice)
    if not wav:
        raise HTTPException(status_code=503, detail="síntese de voz indisponível")
    return Response(content=wav, media_type="audio/wav",
                    headers={"Cache-Control": "no-store"})


@app.get("/api/memory", dependencies=[Depends(require_token)])
async def list_memory() -> dict[str, Any]:
    return {"entries": agent.memory.all()}


@app.post("/api/memory", dependencies=[Depends(require_token)])
async def add_memory(body: MemoryRequest) -> dict[str, Any]:
    try:
        return {"entry": agent.memory.add(body.key, body.value, body.category)}
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))


@app.delete("/api/memory/{entry_id}", dependencies=[Depends(require_token)])
async def delete_memory(entry_id: str) -> dict[str, Any]:
    if not agent.memory.delete(entry_id):
        raise HTTPException(status_code=404, detail="lembrança não encontrada")
    return {"deleted": entry_id}


@app.delete("/api/memory", dependencies=[Depends(require_token)])
async def clear_memory() -> dict[str, Any]:
    agent.memory.clear()
    return {"cleared": True}


@app.get("/api/history", dependencies=[Depends(require_token)])
async def get_history(limit: int = 60) -> dict[str, Any]:
    return {"messages": agent.history.messages(max(1, min(limit, 200)))}


@app.delete("/api/history", dependencies=[Depends(require_token)])
async def clear_history() -> dict[str, Any]:
    agent.history.clear()
    return {"cleared": True}


@app.get("/api/reminders", dependencies=[Depends(require_token)])
async def get_reminders() -> dict[str, Any]:
    return {"reminders": [r for r in tools._read_reminders() if not r.get("fired")]}


@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket) -> None:
    expected = _token()
    if expected and websocket.query_params.get("token", "") != expected:
        await websocket.close(code=1008)
        return
    await websocket.accept()
    hub.clients.add(websocket)
    try:
        while True:
            await websocket.receive_text()
    except WebSocketDisconnect:
        pass
    finally:
        hub.clients.discard(websocket)


@app.get("/sw.js", include_in_schema=False)
async def service_worker() -> Response:
    path = settings.MOBILE_DIR / "sw.js"
    if not path.exists():
        raise HTTPException(status_code=404)
    return Response(
        content=path.read_text(encoding="utf-8"),
        media_type="application/javascript",
        headers={"Cache-Control": "no-cache", "Service-Worker-Allowed": "/"},
    )


if settings.MOBILE_DIR.exists():
    app.mount("/", StaticFiles(directory=settings.MOBILE_DIR, html=True), name="mobile")
else:  # pragma: no cover - only hit by a broken deployment
    @app.get("/")
    async def missing_mobile() -> JSONResponse:
        return JSONResponse({"error": "mobile/ não encontrado"}, status_code=500)
