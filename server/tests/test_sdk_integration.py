"""End-to-end checks through the real google-genai SDK.

A local HTTP server stands in for Gemini. Nothing is mocked inside our code:
the SDK serialises the request, our agent parses the reply, runs the tool and
sends the function response back — the same path a real key takes.
"""
from __future__ import annotations

import base64
import io
import json
import threading
import wave
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import pytest

from server.orb import agent as agent_module
from server.orb import llm, settings, tools


class FakeGemini(BaseHTTPRequestHandler):
    requests: list[dict] = []
    replies: list[dict] = []

    def do_POST(self):  # noqa: N802 - http.server API
        length = int(self.headers.get("content-length", 0))
        body = json.loads(self.rfile.read(length) or b"{}")
        type(self).requests.append({"path": self.path, "body": body})
        reply = type(self).replies.pop(0) if type(self).replies else text_reply("fallback")
        payload = json.dumps(reply).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, *args):
        pass


def text_reply(text: str) -> dict:
    return {"candidates": [{"content": {"role": "model", "parts": [{"text": text}]},
                            "finishReason": "STOP"}]}


def call_reply(name: str, args: dict) -> dict:
    return {"candidates": [{"content": {"role": "model", "parts": [
        {"functionCall": {"name": name, "args": args}}
    ]}, "finishReason": "STOP"}]}


@pytest.fixture()
def fake_gemini(monkeypatch, tmp_path):
    FakeGemini.requests = []
    FakeGemini.replies = []
    server = ThreadingHTTPServer(("127.0.0.1", 0), FakeGemini)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()

    monkeypatch.setenv("ORB_GEMINI_BASE_URL", f"http://127.0.0.1:{server.server_address[1]}")
    monkeypatch.setattr(settings, "api_key", lambda: "test-key")
    monkeypatch.setattr(settings, "DATA_DIR", tmp_path)
    monkeypatch.setattr(tools, "REMINDERS_PATH", tmp_path / "reminders.json")
    monkeypatch.setattr(llm, "_client", None)
    monkeypatch.setattr(llm, "_client_key", "")

    yield FakeGemini
    server.shutdown()


def test_text_turn_serialises_prompt_and_tools(fake_gemini):
    fake_gemini.replies.append(text_reply("Olá, senhor."))
    result = agent_module.Agent().chat("oi")

    assert result["ok"] is True
    assert result["reply"] == "Olá, senhor."

    request = fake_gemini.requests[0]
    assert ":generateContent" in request["path"]
    body = request["body"]
    assert "systemInstruction" in body or "system_instruction" in body
    assert body["tools"][0]["functionDeclarations"]
    assert any("oi" in json.dumps(part) for part in body["contents"])


def test_function_call_round_trip(fake_gemini):
    fake_gemini.replies.extend([
        call_reply("now", {}),
        text_reply("Agora são quinze horas."),
    ])
    result = agent_module.Agent().chat("que horas são?")

    assert result["reply"] == "Agora são quinze horas."
    assert result["tools"][0]["name"] == "now"
    assert len(fake_gemini.requests) == 2

    second = fake_gemini.requests[1]["body"]
    serialised = json.dumps(second)
    assert "functionResponse" in serialised
    assert "Agora:" in serialised


def test_tool_result_reaches_the_model(fake_gemini):
    fake_gemini.replies.extend([
        call_reply("remember", {"key": "cidade", "value": "Recife", "category": "identity"}),
        text_reply("Anotado."),
    ])
    result = agent_module.Agent().chat("moro em Recife")

    assert result["reply"] == "Anotado."
    assert "Memorizado" in result["tools"][0]["result"]
    assert "Recife" in json.dumps(fake_gemini.requests[1]["body"])


def test_transcription_sends_audio_inline(fake_gemini):
    fake_gemini.replies.append(text_reply("que horas são"))
    transcript = llm.transcribe(b"RIFF-fake-wav", "audio/wav")

    assert transcript == "que horas são"
    body = json.dumps(fake_gemini.requests[0]["body"])
    assert "inlineData" in body
    assert "audio/wav" in body


def test_speech_is_wrapped_in_a_playable_wav(fake_gemini):
    pcm = b"\x01\x00" * 240
    fake_gemini.replies.append({"candidates": [{"content": {"role": "model", "parts": [
        {"inlineData": {"mimeType": "audio/L16;rate=24000",
                        "data": base64.b64encode(pcm).decode()}}
    ]}}]})

    wav = llm.speak("olá")

    assert wav and wav[:4] == b"RIFF"
    with wave.open(io.BytesIO(wav)) as handle:
        assert handle.getframerate() == 24_000
        assert handle.getnchannels() == 1
        assert handle.getsampwidth() == 2


def test_invalid_key_is_reported(fake_gemini, monkeypatch):
    class Unauthorized(BaseHTTPRequestHandler):
        def do_POST(self):  # noqa: N802
            payload = json.dumps({"error": {"code": 400, "message": "API key not valid. Please pass a valid API key.", "status": "INVALID_ARGUMENT"}}).encode()
            self.send_response(400)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)

        def log_message(self, *args):
            pass

    server = ThreadingHTTPServer(("127.0.0.1", 0), Unauthorized)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    monkeypatch.setenv("ORB_GEMINI_BASE_URL", f"http://127.0.0.1:{server.server_address[1]}")
    monkeypatch.setattr(llm, "_client", None)
    monkeypatch.setattr(llm, "_client_key", "")

    result = agent_module.Agent().chat("oi")
    server.shutdown()

    assert result["ok"] is False
    assert result["error"] == "invalid_api_key"
