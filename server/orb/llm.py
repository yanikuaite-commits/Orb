"""One place where Orb's Gemini calls are made.

Inspired by the model-ladder idea in JARVIS (Mark LVII) by FatihMakes: a model
that runs out of quota rests while the next one answers, and every call has a
deadline. That behaviour is what makes a voice assistant survive a free-tier
quota. The code here is written for the mobile server; the desktop Live-session
and provider code is not used.

Every public function returns a value or ``None`` — never an unhandled
exception from the vendor SDK. Callers show the user a sentence instead of a
traceback.
"""
from __future__ import annotations

import io
import json
import os
import re
import threading
import time
import wave
from typing import Any, Optional

from google import genai
from google.genai import types

from . import settings

FAST = "fast"
SMART = "smart"
SEARCH = "search"

DEFAULT_TIMEOUT_MS = 60_000
COOLDOWN_SECONDS = 300
_MISSING = object()

_cool_until: dict[str, float] = {}
_cool_lock = threading.Lock()
_client: Optional[genai.Client] = None
_client_key = ""
_last_error = ""
_last_detail = ""
_error_lock = threading.Lock()


def last_error() -> str:
    """Machine-readable reason for the most recent failed call: key | unavailable | ''."""
    with _error_lock:
        return _last_error


def last_detail() -> str:
    """Short vendor message for the most recent failure, safe to show to the user."""
    with _error_lock:
        return _last_detail


def _set_error(value: str, detail: str = "") -> None:
    global _last_error, _last_detail
    with _error_lock:
        _last_error = value
        _last_detail = detail[:240]


def _ladder(tier: str) -> list[str]:
    cfg = settings.load()
    if tier == SEARCH:
        return list(cfg.get("smart_ladder") or settings.DEFAULT_SMART_LADDER)
    if tier == SMART:
        return list(cfg.get("smart_ladder") or settings.DEFAULT_SMART_LADDER)
    return list(cfg.get("model_ladder") or settings.DEFAULT_LADDER)


def _cooling(model: str) -> bool:
    with _cool_lock:
        return _cool_until.get(model, 0.0) > time.monotonic()


def _cool(model: str, seconds: float = COOLDOWN_SECONDS) -> None:
    with _cool_lock:
        _cool_until[model] = time.monotonic() + seconds


def _usable(tier: str) -> list[str]:
    seen: list[str] = []
    for model in _ladder(tier):
        if model not in seen and not _cooling(model):
            seen.append(model)
    return seen


def _is_quota_error(message: str) -> bool:
    lowered = message.lower()
    return any(token in lowered for token in ("429", "resource_exhausted", "quota", "rate limit"))


def _is_key_error(message: str) -> bool:
    lowered = message.lower()
    return any(token in lowered for token in ("api key", "unauthenticated", "permission denied", "401", "403"))


def client(timeout_ms: int = DEFAULT_TIMEOUT_MS) -> genai.Client:
    global _client, _client_key
    key = settings.api_key()
    if not key:
        raise RuntimeError("no Gemini API key is configured")
    base_url = os.environ.get("ORB_GEMINI_BASE_URL", "").strip()
    cache_key = f"{key}|{base_url}"
    if _client is None or _client_key != cache_key:
        options = {"timeout": timeout_ms}
        if base_url:
            options["base_url"] = base_url
        _client = genai.Client(api_key=key, http_options=types.HttpOptions(**options))
        _client_key = cache_key
    return _client


def response_text(response: Any) -> str:
    if response is None:
        return ""
    try:
        text = getattr(response, "text", None)
        if text:
            return str(text).strip()
    except Exception:
        pass
    pieces: list[str] = []
    try:
        for candidate in response.candidates or []:
            content = getattr(candidate, "content", None)
            for part in getattr(content, "parts", None) or []:
                if getattr(part, "text", None):
                    pieces.append(part.text)
    except Exception:
        pass
    return "".join(pieces).strip()


def call(contents: Any, tier: str = FAST, config: Any = None,
         timeout_ms: int = DEFAULT_TIMEOUT_MS) -> Any:
    """Run one generation, walking the ladder until a model answers."""
    if not settings.api_key():
        return None
    _set_error("")
    failure = ""
    for model in _usable(tier):
        try:
            kwargs: dict[str, Any] = {"model": model, "contents": contents}
            if config is not None:
                kwargs["config"] = config
            return client(timeout_ms).models.generate_content(**kwargs)
        except Exception as exc:  # vendor SDK raises many shapes
            message = str(exc)
            failure = message
            if _is_quota_error(message):
                _cool(model)
                print(f"[llm] {model}: quota exhausted, resting it")
            elif _is_key_error(message):
                print(f"[llm] API key refused: {message[:160]}")
                _set_error("key", message)
                break
            else:
                print(f"[llm] {model}: {type(exc).__name__}: {message[:160]}")
    if failure and last_error() != "key":
        _set_error("unavailable", failure)
    if failure:
        print(f"[llm] every model on the {tier} ladder failed")
    return None


def text(contents: Any, tier: str = FAST, config: Any = None,
         timeout_ms: int = DEFAULT_TIMEOUT_MS, default: str = "") -> str:
    return response_text(call(contents, tier=tier, config=config, timeout_ms=timeout_ms)) or default


def parse_json(raw: str, default: Any = None) -> Any:
    if not raw:
        return default
    cleaned = re.sub(r"(?is)<think(?:ing)?>.*?</think(?:ing)?>", "", raw)
    fence = re.search(r"```(?:json|JSON)?\s*(.*?)```", cleaned, re.S)
    if fence:
        cleaned = fence.group(1)
    cleaned = cleaned.strip()
    for candidate in (cleaned, cleaned[cleaned.find("{"):cleaned.rfind("}") + 1],
                      cleaned[cleaned.find("["):cleaned.rfind("]") + 1]):
        if not candidate:
            continue
        try:
            return json.loads(candidate)
        except Exception:
            continue
    return default


def as_json(contents: Any, tier: str = FAST, config: Any = None,
            timeout_ms: int = DEFAULT_TIMEOUT_MS, default: Any = None) -> Any:
    return parse_json(text(contents, tier=tier, config=config, timeout_ms=timeout_ms), default)


def transcribe(audio: bytes, mime_type: str = "audio/wav", language: str = "") -> str:
    """Turn a recorded question into text before the agent sees it."""
    hint = f" The speaker is likely using {language}." if language else ""
    prompt = (
        "Transcribe this recording verbatim. Return only the words spoken, with no "
        "quotation marks, labels or commentary. If there is no speech, return an "
        "empty string." + hint
    )
    contents = [
        types.Content(
            role="user",
            parts=[
                types.Part.from_bytes(data=audio, mime_type=mime_type),
                types.Part(text=prompt),
            ],
        )
    ]
    return text(contents, tier=FAST, timeout_ms=45_000)


def pcm_to_wav(pcm: bytes, rate: int = 24_000, channels: int = 1, width: int = 2) -> bytes:
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as handle:
        handle.setnchannels(channels)
        handle.setsampwidth(width)
        handle.setframerate(rate)
        handle.writeframes(pcm)
    return buffer.getvalue()


def speak(text_value: str, voice: str = "", timeout_ms: int = 45_000) -> Optional[bytes]:
    """Render speech with Gemini TTS. Returns WAV bytes, or None if unavailable."""
    cleaned = (text_value or "").strip()
    if not cleaned:
        return None
    voice_name = voice or settings.get("voice") or "Charon"
    model = settings.get("tts_model") or settings.DEFAULT_TTS_MODEL
    config = types.GenerateContentConfig(
        response_modalities=["AUDIO"],
        speech_config=types.SpeechConfig(
            voice_config=types.VoiceConfig(
                prebuilt_voice_config=types.PrebuiltVoiceConfig(voice_name=voice_name)
            )
        ),
    )
    try:
        response = client(timeout_ms).models.generate_content(
            model=model,
            contents=f"Say this naturally, in the language it is written in:\n{cleaned[:1500]}",
            config=config,
        )
        for candidate in response.candidates or []:
            for part in getattr(candidate.content, "parts", None) or []:
                inline = getattr(part, "inline_data", None)
                if inline and getattr(inline, "data", None):
                    mime = (getattr(inline, "mime_type", "") or "").lower()
                    rate_match = re.search(r"rate=(\d+)", mime)
                    rate = int(rate_match.group(1)) if rate_match else 24_000
                    return pcm_to_wav(bytes(inline.data), rate=rate)
    except Exception as exc:
        print(f"[llm] TTS failed: {type(exc).__name__}: {str(exc)[:160]}")
    return None


def grounded_search(query: str, deep: bool = False) -> dict[str, Any]:
    """Search with Gemini's Google grounding. Returns text and source URLs."""
    instruction = (
        "Research this thoroughly and answer with concrete facts, dates and figures. "
        if deep
        else "Answer this using current web results, briefly and concretely. "
    )
    config = types.GenerateContentConfig(
        tools=[types.Tool(google_search=types.GoogleSearch())],
        temperature=0.2,
    )
    response = call(
        instruction + "Question: " + query,
        tier=SEARCH if deep else FAST,
        config=config,
        timeout_ms=60_000 if deep else 30_000,
    )
    if response is None:
        return {"text": "", "sources": []}
    answer = response_text(response)
    sources: list[dict[str, str]] = []
    try:
        metadata = response.candidates[0].grounding_metadata
        for chunk in getattr(metadata, "grounding_chunks", None) or []:
            web = getattr(chunk, "web", None)
            if web and getattr(web, "uri", None):
                sources.append({"title": getattr(web, "title", "") or web.uri, "url": web.uri})
    except Exception:
        pass
    deduped: list[dict[str, str]] = []
    for source in sources:
        if source["url"] not in {s["url"] for s in deduped}:
            deduped.append(source)
    return {"text": answer, "sources": deduped[:6]}
