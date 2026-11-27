# Orb — notes for coding agents

A mobile-first personal assistant derived from JARVIS (Mark LVII) by FatihMakes.
The phone runs a PWA; the brain is a Python FastAPI server that talks to Gemini.

## Run and test

```bash
pip install -r server/requirements.txt
ORB_API_KEY=... python -m server          # http://localhost:8000
python -m pytest server/tests -q
```

`python -m server` (not `python server/main.py`) is required: `main.py` uses
relative imports.

## Architecture

- `server/main.py` — HTTP/WebSocket surface only. Keep business logic out of it.
- `server/orb/agent.py` — the conversation loop: history → model → tool calls
  → model → reply. Tool execution is synchronous and runs in a worker thread via
  `asyncio.to_thread` at the route layer.
- `server/orb/llm.py` — every Gemini call. Never construct a `genai.Client`
  elsewhere. The model ladder and the five-minute cooldown are deliberate: a
  quota-exhausted model is skipped, not retried on every turn.
- `server/orb/tools.py` — one `FunctionDeclaration` plus one handler per tool.
  Adding a tool means adding both to `DECLARATIONS` and `HANDLERS`.
- `server/orb/memory.py` — flat JSON entry list, plus capped history.
- `mobile/www/` — the whole client. No build step, no framework.

## Rules that have already caused bugs

- **Paths are resolved at import time.** `settings.DATA_DIR`, `CONFIG_PATH` and
  `MOBILE_DIR` are read when the module loads. Tests must set `ORB_DATA_DIR`
  and `ORB_CONFIG` in `conftest.py` before importing `server`.
- **Route order matters.** `/sw.js` is declared before the `StaticFiles` mount at
  `/`; a new non-static route must also go above the mount.
- **PWA paths are absolute** (`/app.js`, `/icons/...`). Capacitor serves `www/`
  at the origin root, so this is intentional. Do not switch to relative paths
  without checking the native shell.
- **Audio is WAV, 16 kHz, mono, 16-bit.** The browser downsamples before upload
  because Gemini's audio input does not accept WebM/Opus. Do not change the
  client encoder without changing the server's accepted MIME list.
- **Every tool must return a string and must not raise.** `tools.run` catches
  exceptions, but a handler that lies about success is worse than one that
  reports failure.
- **Never return the API key** from `settings.public_settings()`; the UI only
  needs `api_key_set`.

## Style

- Python 3.11+, type hints on public functions, `from __future__ import annotations`.
- Comments explain *why*, never restate the code. The original JARVIS files carry
  long design notes; match that spirit, not the length.
- UI text is Portuguese (pt-BR); code and identifiers are English.
- Keep the client dependency-free. If a feature needs a library, justify it.

## Licence

Orb is licensed under the **Orb License 1.0** (see `LICENSE`): use,
modify and redistribute freely, including commercially, keeping the copyright
notice, `NOTICE` and the license text.

It is an independent implementation inspired by JARVIS (Mark LVII) by
FatihMakes. No upstream source code is included. The JARVIS project remains
under CC BY-NC 4.0, which applies only to the original project. Never copy code
or assets from it into this repository.
