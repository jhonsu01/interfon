"""Proveedores de voz y cerebro del agente.

- Cerebro y STT: API local de Unsloth Studio (compatible OpenAI, sin contrasena).
- Voz (TTS): voces SAPI de Windows via PowerShell (cero dependencias) con
  Piper como opcion de mejor calidad si esta instalado.
"""
import base64
import logging
import pathlib
import subprocess
import tempfile
import threading

import requests

from .config import CFG

log = logging.getLogger("interfon.providers")

LOAD_TIMEOUT = 600  # cargar un GGUF la primera vez puede tardar ~90s


class UnslothAPI:
    """Cliente minimo de la API OpenAI-compatible de Unsloth Studio."""

    def __init__(self, base: str, llm_model: str, stt_model: str):
        self.base = base.rstrip("/")
        self.llm_model = llm_model
        self.stt_model = stt_model
        self._load_lock = threading.Lock()

    # ---------- modelos ----------
    def loaded_models(self) -> list:
        try:
            r = requests.get(f"{self.base}/api/inference/loaded-models", timeout=10)
            r.raise_for_status()
            return [m.get("id", "") for m in r.json().get("data", [])]
        except Exception:
            return []

    def ensure_llm_loaded(self) -> None:
        ids = self.loaded_models()
        if any(self.llm_model.split("/")[-1].split("-GGUF")[0] in i for i in ids):
            return
        with self._load_lock:
            ids = self.loaded_models()
            if any(self.llm_model.split("/")[-1].split("-GGUF")[0] in i for i in ids):
                return
            log.info("Cargando modelo %s en la API (puede tardar ~90s)...", self.llm_model)
            r = requests.post(f"{self.base}/v1/load",
                              json={"model_path": self.llm_model}, timeout=LOAD_TIMEOUT)
            r.raise_for_status()
            log.info("Modelo cargado.")

    # ---------- chat ----------
    def chat(self, messages: list, temperature: float = 0.6, max_tokens: int = 160) -> str:
        payload = {
            "model": self.llm_model,
            "messages": messages,
            "temperature": temperature,
            "max_tokens": max_tokens,
            "chat_template_kwargs": {"enable_thinking": False},
        }
        for attempt in (1, 2):
            r = requests.post(f"{self.base}/v1/chat/completions", json=payload, timeout=300)
            if r.status_code == 400 and "No model loaded" in r.text and attempt == 1:
                self.ensure_llm_loaded()
                continue
            r.raise_for_status()
            break
        msg = r.json()["choices"][0]["message"]
        text = (msg.get("content") or "").strip()
        if not text:
            # Modelos con razonamiento: la respuesta util va en reasoning_content.
            rc = (msg.get("reasoning_content") or "").strip()
            if "</think>" in rc:
                rc = rc.split("</think>")[-1].strip()
            text = rc
        return text or "(sin respuesta)"

    # ---------- STT ----------
    def stt(self, wav_bytes: bytes) -> str:
        files = {"file": ("audio.wav", wav_bytes, "audio/wav")}
        data = {"model": self.stt_model}
        r = requests.post(f"{self.base}/v1/audio/transcriptions",
                          files=files, data=data, timeout=180)
        r.raise_for_status()
        return r.json().get("text", "").strip()


# ============================================================
# TTS local
# ============================================================

def _ps_encode(script: str) -> str:
    return base64.b64encode(script.encode("utf-16-le")).decode("ascii")


def sapi_tts(text: str, voice: str) -> bytes:
    """Sintetiza con una voz SAPI de Windows. Devuelve bytes WAV."""
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f:
        out = f.name
    b64 = base64.b64encode(text.encode("utf-8")).decode("ascii")
    ps = (
        "Add-Type -AssemblyName System.Speech\n"
        "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer\n"
        "try { $s.SelectVoice('" + voice.replace("'", "''") + "') } catch { }\n"
        "$s.SetOutputToWaveFile('" + out.replace("'", "''") + "')\n"
        "$t = [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('" + b64 + "'))\n"
        "$s.Speak($t)\n"
        "$s.Dispose()\n"
    )
    cmd = ["powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand", _ps_encode(ps)]
    proc = subprocess.run(cmd, capture_output=True, timeout=120)
    if proc.returncode != 0:
        raise RuntimeError(f"SAPI TTS fallo: {proc.stderr.decode('utf-8', 'ignore')[:300]}")
    wav = pathlib.Path(out).read_bytes()
    pathlib.Path(out).unlink(missing_ok=True)
    if len(wav) < 100:
        raise RuntimeError("SAPI TTS produjo un wav vacio")
    return wav


_voice_cache: dict = {}


def _get_piper_voice(model_path: str):
    """Carga la voz Piper una sola vez (la carga inicial tarda ~4s)."""
    if model_path in _voice_cache:
        return _voice_cache[model_path]
    try:
        from piper import PiperVoice  # type: ignore
    except ImportError:
        return None
    model = pathlib.Path(model_path)
    if not model.exists():
        log.warning("Piper model no encontrado: %s", model)
        return None
    config = model.with_suffix(".onnx.json")
    if config.exists():
        voice = PiperVoice.load(str(model), config_path=str(config))
    else:
        voice = PiperVoice.load(str(model))
    _voice_cache[model_path] = voice
    return voice


def piper_tts(text: str, model_path: str):
    """TTS con Piper si esta instalado; devuelve bytes o None si no disponible."""
    if not model_path:
        return None
    import wave

    voice = _get_piper_voice(model_path)
    if voice is None:
        return None
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f:
        out = f.name
    try:
        with wave.open(out, "wb") as w:
            voice.synthesize_wav(text, w)
        return pathlib.Path(out).read_bytes()
    finally:
        pathlib.Path(out).unlink(missing_ok=True)


def tts_synthesize(text: str) -> bytes:
    """Sintetiza texto segun la configuracion (auto: piper si hay, si no sapi)."""
    engine = CFG.tts_engine
    if engine in ("auto", "piper"):
        wav = piper_tts(text, CFG.piper_model_path)
        if wav:
            return wav
        if engine == "piper":
            log.warning("Piper no disponible; usando SAPI.")
    return sapi_tts(text, CFG.tts_voice_sapi)
