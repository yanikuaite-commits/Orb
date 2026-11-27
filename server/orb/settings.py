"""Runtime settings for Orb Mobile.

Settings live in one JSON file so the phone can change the assistant's name,
language and voice without a restart. The Gemini key is read from the
environment first and only falls back to the settings file, so a Docker or
systemd deployment can keep it out of disk entirely.
"""
from __future__ import annotations

import json
import os
import threading
from pathlib import Path
from typing import Any

BASE_DIR = Path(__file__).resolve().parent.parent
DATA_DIR = Path(os.environ.get("ORB_DATA_DIR", BASE_DIR / "data"))
CONFIG_PATH = Path(os.environ.get("ORB_CONFIG", BASE_DIR / "config.json"))
MOBILE_DIR = Path(os.environ.get("ORB_MOBILE_DIR", BASE_DIR.parent / "mobile" / "www"))

DEFAULT_LADDER = [
    "gemini-3.5-flash-lite",
    "gemini-3.1-flash-lite",
    "gemini-3.6-flash",
    "gemini-2.5-flash-lite",
    "gemini-2.5-flash",
    "gemini-2.0-flash",
    "gemini-flash-latest",
]
DEFAULT_SMART_LADDER = [
    "gemini-3.6-flash",
    "gemini-3.5-flash",
    "gemini-3.5-flash-lite",
    "gemini-3.8-flash",
    "gemini-3.1-flash-lite",
    "gemini-2.5-flash",
    "gemini-2.5-pro",
    "gemini-2.0-flash",
    "gemini-flash-latest",
]
DEFAULT_TTS_MODEL = "gemini-3.8-flash-lite-tts"
DEFAULT_VOICES = ["Charon", "Kore", "Puck", "Fenrir", "Aoede", "Orus"]

DEFAULTS: dict[str, Any] = {
    "assistant_name": "Orb",
    "user_name": "",
    "language": "pt-BR",
    "voice": "Charon",
    "tts_engine": "browser",
    "persona": "",
    "model_ladder": DEFAULT_LADDER,
    "smart_ladder": DEFAULT_SMART_LADDER,
    "tts_model": DEFAULT_TTS_MODEL,
    "api_key": "",
}

_lock = threading.RLock()


def ensure_dirs() -> None:
    DATA_DIR.mkdir(parents=True, exist_ok=True)


def load() -> dict[str, Any]:
    with _lock:
        data: dict[str, Any] = {}
        if CONFIG_PATH.exists():
            try:
                data = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
            except Exception:
                data = {}
        merged = dict(DEFAULTS)
        merged.update({k: v for k, v in data.items() if k in DEFAULTS})
        return merged


def save(values: dict[str, Any]) -> dict[str, Any]:
    with _lock:
        current = load()
        for key, value in values.items():
            if key in DEFAULTS:
                current[key] = value
        CONFIG_PATH.parent.mkdir(parents=True, exist_ok=True)
        tmp = CONFIG_PATH.with_suffix(".tmp")
        tmp.write_text(json.dumps(current, ensure_ascii=False, indent=2), encoding="utf-8")
        tmp.replace(CONFIG_PATH)
        return current


def get(key: str, default: Any = None) -> Any:
    return load().get(key, DEFAULTS.get(key, default))


def api_key() -> str:
    for name in ("ORB_API_KEY", "GEMINI_API_KEY", "GOOGLE_API_KEY"):
        value = os.environ.get(name, "").strip()
        if value:
            return value
    return str(load().get("api_key") or "").strip()


def public_settings() -> dict[str, Any]:
    data = load()
    data.pop("api_key", None)
    data["api_key_set"] = bool(api_key())
    data["voices"] = DEFAULT_VOICES
    return data


def assistant_name() -> str:
    return str(get("assistant_name") or "Orb").strip() or "Orb"
