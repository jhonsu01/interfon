"""Configuracion del servidor Interfon desde .env (sin dependencias externas)."""
import os
import pathlib

SERVER_DIR = pathlib.Path(__file__).resolve().parent.parent
PROJECT_DIR = SERVER_DIR.parent


def _load_env(path: pathlib.Path) -> None:
    if not path.exists():
        return
    raw = path.read_text(encoding="utf-8-sig")
    for line in raw.splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, val = line.partition("=")
        os.environ.setdefault(key.strip(), val.strip())


_load_env(SERVER_DIR / ".env")


def _get(key: str, default: str) -> str:
    return os.environ.get(key, default).strip()


class Config:
    host: str = _get("HOST", "0.0.0.0")
    port: int = int(_get("PORT", "8765"))

    unsloth_base: str = _get("UNSLOTH_BASE", "http://127.0.0.1:8888").rstrip("/")
    llm_model: str = _get("LLM_MODEL", "unsloth/gemma-4-E2B-it-GGUF")
    stt_model: str = _get("STT_MODEL", "qwen3-asr-0.6b")

    tts_engine: str = _get("TTS_ENGINE", "auto").lower()
    tts_voice_sapi: str = _get("TTS_VOICE_SAPI", "Microsoft Sabina Desktop")
    piper_model: str = _get("PIPER_MODEL", "")

    @property
    def piper_model_path(self) -> str:
        p = pathlib.Path(self.piper_model)
        if not p.is_absolute() and str(p) != "":
            p = SERVER_DIR / p
        return str(p)

    agent_name: str = _get("AGENT_NAME", "ZCode")
    system_prompt: str = _get(
        "SYSTEM_PROMPT",
        "Eres ZCode, el agente de programacion del equipo, hablando por el telefono "
        "interno Interfon. Responde SIEMPRE en espanol, breve (maximo 2 frases), "
        "natural y hablado, sin markdown ni listas.",
    )

    ring_timeout: int = int(_get("RING_TIMEOUT", "45"))
    max_utterance_s: float = float(_get("MAX_UTTERANCE_S", "12"))
    history_turns: int = int(_get("HISTORY_TURNS", "8"))

    logs_dir = SERVER_DIR / "logs"


CFG = Config()
CFG.logs_dir.mkdir(exist_ok=True)
