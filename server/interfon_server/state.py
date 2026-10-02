"""Estado global del servidor: clientes, sesion de llamada, bandeja y logs."""
import asyncio
import json
import time
import uuid
from datetime import datetime

from .config import CFG
from .vad import UtteranceDetector


class CallSession:
    def __init__(self, mode: str):
        self.id = uuid.uuid4().hex[:12]
        self.mode = mode            # "call"
        self.state = "ringing"      # ringing | listening | thinking | speaking | ended
        self.history: list = []     # [{"role": "user"|"assistant", "content": str}]
        self.detector = UtteranceDetector(max_s=CFG.max_utterance_s)
        self.speak_first: str | None = None
        self.created = time.time()
        self.ended_reason: str | None = None

    def trim(self) -> None:
        self.history = self.history[-(CFG.history_turns * 2):]


class State:
    def __init__(self):
        self.sockets: set = set()
        self.session: CallSession | None = None
        self.outbox: list = []      # mensajes pendientes hasta que el telefono conecte
        self.started = time.time()
        self.walkie_history: list = []
        self._log_lock = asyncio.Lock()

    @property
    def phone_connected(self) -> bool:
        return bool(self.sockets)

    def new_session(self, mode: str = "call") -> CallSession:
        if self.session and self.session.state != "ended":
            self.session.state = "ended"
        s = CallSession(mode)
        self.session = s
        return s

    async def broadcast(self, obj: dict) -> None:
        data = json.dumps(obj, ensure_ascii=False)
        dead = []
        for ws in list(self.sockets):
            try:
                await ws.send_text(data)
            except Exception:
                dead.append(ws)
        for ws in dead:
            self.sockets.discard(ws)

    async def log_event(self, kind: str, **fields) -> None:
        rec = {"ts": datetime.now().isoformat(timespec="seconds"), "kind": kind, **fields}
        line = json.dumps(rec, ensure_ascii=False)
        async with self._log_lock:
            path = CFG.logs_dir / f"transcripts-{datetime.now():%Y-%m-%d}.jsonl"
            path.write_text(path.read_text() + line + "\n", encoding="utf-8") \
                if path.exists() else path.write_text(line + "\n", encoding="utf-8")
