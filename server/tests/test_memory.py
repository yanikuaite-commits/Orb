from __future__ import annotations

import json

import pytest

from server.orb import memory, settings


@pytest.fixture(autouse=True)
def clean_stores(tmp_path, monkeypatch):
    monkeypatch.setattr(settings, "DATA_DIR", tmp_path)
    yield


def test_add_and_list():
    store = memory.MemoryStore()
    entry = store.add("café", "coado sem açúcar", "preferences")
    assert entry["id"]
    assert store.all()[0]["value"] == "coado sem açúcar"


def test_same_key_updates_instead_of_duplicating():
    store = memory.MemoryStore()
    first = store.add("cidade", "Recife", "identity")
    second = store.add("cidade", "São Paulo", "identity")
    assert first["id"] == second["id"]
    assert len(store.all()) == 1
    assert store.all()[0]["value"] == "São Paulo"


def test_search_scores_matching_entries():
    store = memory.MemoryStore()
    store.add("café", "prefere café coado", "preferences")
    store.add("cidade", "mora em São Paulo", "identity")
    found = store.search("café")
    assert len(found) == 1
    assert found[0]["key"] == "café"


def test_delete_and_forget():
    store = memory.MemoryStore()
    entry = store.add("projeto", "Orb mobile", "projects")
    assert store.delete(entry["id"]) is True
    assert store.delete(entry["id"]) is False
    store.add("projeto", "Orb mobile", "projects")
    assert store.forget("projeto") == 1
    assert store.all() == []


def test_summary_respects_budget():
    store = memory.MemoryStore()
    for index in range(40):
        store.add(f"chave{index}", "x" * 200, "notes")
    summary = store.summary(limit=500)
    assert len(summary) <= 520


def test_history_is_capped(tmp_path):
    store = memory.HistoryStore(path=tmp_path / "history.json", limit=3)
    for index in range(6):
        store.append("user", f"msg {index}")
    messages = store.messages()
    assert [m["text"] for m in messages] == ["msg 3", "msg 4", "msg 5"]


def test_settings_round_trip(tmp_path, monkeypatch):
    config_path = tmp_path / "config.json"
    monkeypatch.setattr(settings, "CONFIG_PATH", config_path)
    monkeypatch.setattr(settings, "DATA_DIR", tmp_path)
    settings.save({"assistant_name": "Orb Prime", "language": "en-US"})
    loaded = settings.load()
    assert loaded["assistant_name"] == "Orb Prime"
    assert loaded["language"] == "en-US"
    assert json.loads(config_path.read_text())["assistant_name"] == "Orb Prime"


def test_public_settings_hides_the_key(tmp_path, monkeypatch):
    monkeypatch.setattr(settings, "CONFIG_PATH", tmp_path / "config.json")
    settings.save({"api_key": "secret-value"})
    public = settings.public_settings()
    assert "api_key" not in public
    assert public["api_key_set"] is True
