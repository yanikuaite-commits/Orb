"""Test configuration.

Paths are redirected to a temporary directory *before* the server package is
imported, because settings.py resolves its data directory at import time.
"""
from __future__ import annotations

import os
import tempfile

_TMP = tempfile.mkdtemp(prefix="orb-tests-")
os.environ["ORB_DATA_DIR"] = _TMP
os.environ["ORB_CONFIG"] = os.path.join(_TMP, "config.json")
for _name in ("ORB_API_KEY", "GEMINI_API_KEY", "GOOGLE_API_KEY", "ORB_TOKEN"):
    os.environ.pop(_name, None)
