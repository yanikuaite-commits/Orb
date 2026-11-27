from __future__ import annotations

import types as pytypes

import pytest
from google.genai import types

from server.orb import agent as agent_module
from server.orb import llm, memory, settings, tools


class FakeResponse:
    def __init__(self, text=None, calls=None):
        self._text = text
        self.function_calls = [
            types.FunctionCall(name=call["name"], args=call.get("args", {}))
            for call in (calls or [])
        ]
        if calls:
            parts = [types.Part(function_call=call) for call in self.function_calls]
        else:
            parts = [types.Part(text=text or "")]
        self.candidates = [pytypes.SimpleNamespace(content=types.Content(role="model", parts=parts))]

    @property
    def text(self):
        if self._text is None:
            raise ValueError("no text in this response")
        return self._text


@pytest.fixture(autouse=True)
def clean(tmp_path, monkeypatch):
    monkeypatch.setattr(settings, "DATA_DIR", tmp_path)
    monkeypatch.setattr(tools, "REMINDERS_PATH", tmp_path / "reminders.json")
    monkeypatch.setattr(settings, "api_key", lambda: "test-key")
    yield


def test_direct_answer(monkeypatch):
    monkeypatch.setattr(llm, "call", lambda *a, **k: FakeResponse(text="Olá, senhor."))
    result = agent_module.Agent().chat("oi")
    assert result["ok"] is True
    assert result["reply"] == "Olá, senhor."
    assert result["tools"] == []


def test_tool_call_then_answer(monkeypatch):
    responses = iter([
        FakeResponse(calls=[{"name": "now", "args": {}}]),
        FakeResponse(text="São quinze horas."),
    ])
    monkeypatch.setattr(llm, "call", lambda *a, **k: next(responses))
    result = agent_module.Agent().chat("que horas são?")
    assert result["reply"] == "São quinze horas."
    assert len(result["tools"]) == 1
    assert result["tools"][0]["name"] == "now"
    assert "Agora:" in result["tools"][0]["result"]


def test_tool_can_write_memory(monkeypatch):
    responses = iter([
        FakeResponse(calls=[{"name": "remember", "args": {
            "key": "cidade", "value": "São Paulo", "category": "identity"}}]),
        FakeResponse(text="Anotado."),
    ])
    monkeypatch.setattr(llm, "call", lambda *a, **k: next(responses))
    worker = agent_module.Agent()
    worker.chat("moro em São Paulo")
    entries = worker.memory.all()
    assert any(entry["key"] == "cidade" for entry in entries)


def test_model_unavailable_is_reported(monkeypatch):
    monkeypatch.setattr(llm, "call", lambda *a, **k: None)
    result = agent_module.Agent().chat("oi")
    assert result["ok"] is False
    assert result["error"] == "model_unavailable"


def test_missing_key_is_reported(monkeypatch):
    monkeypatch.setattr(settings, "api_key", lambda: "")
    result = agent_module.Agent().chat("oi")
    assert result["ok"] is False
    assert result["error"] == "missing_api_key"


def test_history_is_recorded(monkeypatch):
    monkeypatch.setattr(llm, "call", lambda *a, **k: FakeResponse(text="Certo."))
    worker = agent_module.Agent()
    worker.chat("primeira")
    messages = worker.history.messages()
    assert [m["role"] for m in messages] == ["user", "assistant"]
    assert messages[0]["text"] == "primeira"


def test_declarations_are_valid_sdk_schemas():
    tool = types.Tool(function_declarations=tools.DECLARATIONS)
    payload = tool.model_dump(exclude_none=True)
    names = [decl["name"] for decl in payload["function_declarations"]]
    assert "web_search" in names
    assert "set_reminder" in names
    config = types.GenerateContentConfig(tools=[tool])
    assert config.tools
