from __future__ import annotations

import time

import pytest

from server.orb import settings, tools


@pytest.fixture(autouse=True)
def clean_data(tmp_path, monkeypatch):
    monkeypatch.setattr(settings, "DATA_DIR", tmp_path)
    monkeypatch.setattr(tools, "REMINDERS_PATH", tmp_path / "reminders.json")
    yield


def test_now_mentions_the_date():
    value = tools.now()
    assert "Agora:" in value
    assert "UTC" in value or "GMT" in value


def test_remember_recall_forget():
    assert "Memorizado" in tools.remember("café", "coado", "preferences")
    assert "coado" in tools.recall("café")
    assert "Removi 1" in tools.forget("café")
    assert "Nada encontrado" in tools.recall("café")


def test_reminder_lifecycle():
    when = time.strftime("%Y-%m-%dT%H:%M:%S", time.localtime(time.time() + 3600))
    created = tools.set_reminder("beber água", when)
    assert "Lembrete criado" in created
    listed = tools.list_reminders()
    assert "beber água" in listed
    reminder_id = listed.split(":")[0].replace("- ", "").strip()
    assert "cancelado" in tools.cancel_reminder(reminder_id)
    assert "Nenhum lembrete" in tools.list_reminders()


def test_reminder_rejects_bad_date():
    assert "ISO 8601" in tools.set_reminder("teste", "amanhã de manhã")


def test_due_reminders_fires_once():
    when = time.strftime("%Y-%m-%dT%H:%M:%S", time.localtime(time.time() - 60))
    tools.set_reminder("já passou", when)
    first = tools.due_reminders()
    second = tools.due_reminders()
    assert len(first) == 1
    assert second == []


def test_weather_formats_forecast(monkeypatch):
    def fake_get(url, params=None, timeout=15.0):
        if "geocoding" in url:
            return {"results": [{"name": "São Paulo", "admin1": "SP", "country": "Brasil",
                                 "latitude": -23.5, "longitude": -46.6}]}
        return {
            "current": {"temperature_2m": 24, "apparent_temperature": 25,
                        "relative_humidity_2m": 60, "precipitation": 0,
                        "weather_code": 3, "wind_speed_10m": 9},
            "daily": {"time": ["2026-11-27"], "weather_code": [61],
                      "temperature_2m_max": [26], "temperature_2m_min": [17],
                      "precipitation_probability_max": [70]},
        }

    monkeypatch.setattr(tools, "_get", fake_get)
    report = tools.get_weather("São Paulo", 1)
    assert "São Paulo" in report
    assert "24°C" in report
    assert "chuva 70%" in report


def test_unknown_tool_is_reported():
    assert "desconhecida" in tools.run("does_not_exist", {})


def test_tool_errors_do_not_raise(monkeypatch):
    def explode(**kwargs):
        raise RuntimeError("boom")

    monkeypatch.setitem(tools.HANDLERS, "now", explode)
    result = tools.run("now", {})
    assert "falhou" in result
