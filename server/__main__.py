"""Run the Orb server: ``python -m server``."""
from __future__ import annotations

import os

import uvicorn

if __name__ == "__main__":
    uvicorn.run(
        "server.main:app",
        host=os.environ.get("ORB_HOST", "0.0.0.0"),
        port=int(os.environ.get("PORT", "8000")),
        log_level=os.environ.get("ORB_LOG_LEVEL", "info"),
    )
