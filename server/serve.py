"""Arranque del servidor Interfon:  python serve.py"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import uvicorn

from interfon_server.config import CFG

if __name__ == "__main__":
    uvicorn.run("interfon_server.main:app", host=CFG.host, port=CFG.port, log_level="info")
