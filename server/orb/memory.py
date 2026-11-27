"""Long-term memory and conversation history.

Memory is a flat list of entries rather than a nested dictionary: the phone
needs to list, edit and delete individual facts, and a flat record with an id
is the shape that makes those operations obvious. A compact summary rides in
the system prompt; everything else is fetched on demand by the recall tool.
"""
from __future__ import annotations

import json
import re
import threading
import time
import uuid
from pathlib import Path
from typing import Any

from . import settings

CATEGORIES = ("identity", "preferences", "projects", "notes", "facts")
MAX_VALUE = 1200
PROMPT_SUMMARY_CHARS = 1400
HISTORY_LIMIT = 60

_TOKEN_RE = re.compile(r"[\wÀ-ÿ]+", re.UNICODE)
_STOP = {
    "a", "o", "e", "de", "da", "do", "que", "the", "and", "of", "to", "is",
    "um", "uma", "meu", "minha", "my", "i", "you", "eu", "para", "por",
}


def _now() -> float:
    return time.time()


def _tokens(text: str) -> list[str]:
    return [t.lower() for t in _TOKEN_RE.findall(text or "") if len(t) > 2 and t.lower() not in _STOP]


class _JsonFile:
    def __init__(self, path: Path, default: Any):
        self.path = path
        self.default = default
        self.lock = threading.RLock()

    def read(self) -> Any:
        with self.lock:
            try:
                return json.loads(self.path.read_text(encoding="utf-8"))
            except Exception:
                return json.loads(json.dumps(self.default))

    def write(self, data: Any) -> None:
        with self.lock:
            self.path.parent.mkdir(parents=True, exist_ok=True)
            tmp = self.path.with_suffix(".tmp")
            tmp.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
            tmp.replace(self.path)


class MemoryStore:
    def __init__(self, path: Path | None = None):
        self.file = _JsonFile(path or settings.DATA_DIR / "memory.json", {"entries": []})

    def all(self) -> list[dict[str, Any]]:
        data = self.file.read()
        entries = data.get("entries", []) if isinstance(data, dict) else []
        return [e for e in entries if isinstance(e, dict)]

    def _write(self, entries: list[dict[str, Any]]) -> None:
        self.file.write({"entries": entries})

    def add(self, key: str, value: str, category: str = "notes") -> dict[str, Any]:
        key = (key or "").strip()[:120]
        value = (value or "").strip()[:MAX_VALUE]
        category = category if category in CATEGORIES else "notes"
        if not key or not value:
            raise ValueError("key and value are required")
        entries = self.all()
        for entry in entries:
            if entry.get("category") == category and entry.get("key", "").lower() == key.lower():
                entry["value"] = value
                entry["updated_at"] = _now()
                self._write(entries)
                return entry
        entry = {
            "id": uuid.uuid4().hex[:12],
            "category": category,
            "key": key,
            "value": value,
            "created_at": _now(),
            "updated_at": _now(),
        }
        entries.append(entry)
        self._write(entries)
        return entry

    def delete(self, entry_id: str) -> bool:
        entries = self.all()
        kept = [e for e in entries if e.get("id") != entry_id]
        if len(kept) == len(entries):
            return False
        self._write(kept)
        return True

    def forget(self, key: str, category: str = "") -> int:
        key_l = (key or "").strip().lower()
        entries = self.all()
        kept = [
            e for e in entries
            if not (e.get("key", "").lower() == key_l and (not category or e.get("category") == category))
        ]
        removed = len(entries) - len(kept)
        if removed:
            self._write(kept)
        return removed

    def clear(self) -> None:
        self._write([])

    def search(self, query: str, limit: int = 8) -> list[dict[str, Any]]:
        words = _tokens(query)
        if not words:
            return self.all()[:limit]
        scored: list[tuple[int, dict[str, Any]]] = []
        for entry in self.all():
            haystack = f"{entry.get('category','')} {entry.get('key','')} {entry.get('value','')}"
            hay = set(_tokens(haystack))
            score = sum(3 for w in words if w in hay)
            score += sum(1 for w in words if w in haystack.lower())
            if score:
                scored.append((score, entry))
        scored.sort(key=lambda item: (-item[0], -float(item[1].get("updated_at", 0))))
        return [entry for _, entry in scored[:limit]]

    def summary(self, limit: int = PROMPT_SUMMARY_CHARS) -> str:
        entries = sorted(self.all(), key=lambda e: (e.get("category") != "identity", -float(e.get("updated_at", 0))))
        lines: list[str] = []
        used = 0
        for entry in entries:
            line = f"- [{entry.get('category')}] {entry.get('key')}: {entry.get('value')}"
            if used + len(line) > limit:
                break
            lines.append(line)
            used += len(line) + 1
        return "\n".join(lines)


class HistoryStore:
    def __init__(self, path: Path | None = None, limit: int = HISTORY_LIMIT):
        self.file = _JsonFile(path or settings.DATA_DIR / "history.json", {"messages": []})
        self.limit = limit

    def messages(self, limit: int | None = None) -> list[dict[str, Any]]:
        data = self.file.read()
        messages = data.get("messages", []) if isinstance(data, dict) else []
        messages = [m for m in messages if isinstance(m, dict)]
        return messages[-(limit or self.limit):]

    def append(self, role: str, text: str, **extra: Any) -> dict[str, Any]:
        data = self.file.read()
        messages = data.get("messages", []) if isinstance(data, dict) else []
        message = {"role": role, "text": text, "ts": _now(), **extra}
        messages.append(message)
        self.file.write({"messages": messages[-self.limit:]})
        return message

    def clear(self) -> None:
        self.file.write({"messages": []})
