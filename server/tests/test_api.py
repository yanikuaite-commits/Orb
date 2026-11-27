from __future__ import annotations

import io

import pytest
from fastapi.testclient import TestClient

import server.main as main
from server.orb import settings, tools

client = TestClient(main.app)


@pytest.fixture(autouse=True)
def reset():
    main.agent.memory.clear()
    main.agent.history.clear()
    tools._write_reminders([])
    yield


def test_health():
    response = client.get("/api/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert body["api_key_set"] is False


def test_index_and_manifest_are_served():
    assert client.get("/").status_code == 200
    assert client.get("/manifest.webmanifest").status_code == 200
    assert client.get("/sw.js").status_code == 200


def test_config_round_trip():
    response = client.put("/api/config", json={"assistant_name": "Orb Prime", "language": "en-US"})
    assert response.status_code == 200
    assert response.json()["assistant_name"] == "Orb Prime"
    assert client.get("/api/config").json()["language"] == "en-US"


def test_config_rejects_unknown_tts_engine():
    response = client.put("/api/config", json={"tts_engine": "telepathy"})
    assert response.status_code == 400


def test_memory_crud():
    created = client.post("/api/memory", json={"key": "café", "value": "coado", "category": "preferences"})
    assert created.status_code == 200
    entry_id = created.json()["entry"]["id"]
    assert len(client.get("/api/memory").json()["entries"]) == 1
    assert client.delete(f"/api/memory/{entry_id}").status_code == 200
    assert client.get("/api/memory").json()["entries"] == []


def test_chat_without_key_explains_setup():
    response = client.post("/api/chat", json={"message": "oi"})
    assert response.status_code == 200
    assert response.json()["error"] == "missing_api_key"


def test_tts_without_key_is_unavailable():
    assert client.post("/api/tts", json={"text": "olá"}).status_code == 503


def test_voice_without_key_does_not_crash():
    wav = io.BytesIO(b"RIFF" + b"\x00" * 200)
    response = client.post("/api/voice", files={"audio": ("q.wav", wav, "audio/wav")})
    assert response.status_code == 200
    assert "Não consegui ouvir" in response.json()["reply"]


def test_voice_rejects_unknown_format():
    response = client.post("/api/voice", files={"audio": ("q.txt", b"hello", "text/plain")})
    assert response.status_code == 415


def test_history_endpoints():
    main.agent.history.append("user", "oi")
    assert client.get("/api/history").json()["messages"][0]["text"] == "oi"
    assert client.delete("/api/history").status_code == 200
    assert client.get("/api/history").json()["messages"] == []


def test_token_protects_the_api(monkeypatch):
    monkeypatch.setenv("ORB_TOKEN", "s3cret")
    assert client.get("/api/memory").status_code == 401
    assert client.get("/api/memory", headers={"Authorization": "Bearer s3cret"}).status_code == 200
    assert client.get("/api/memory?token=s3cret").status_code == 200
    assert client.get("/api/health").status_code == 200
