"""Papercast: convierte un guion en audios tipo podcast y los envia al telefono.

Uso:
  python papercast.py guion.txt          # partes separadas por lineas '---'
  echo "texto unico" | python papercast.py -

El guion lo redacta el agente (skill technical-to-colloquial sobre el paper);
esta herramienta solo sintetiza, mide duracion real y espacia los envios.
"""
import io
import sys
import time
import wave
from pathlib import Path

import requests

BASE = "http://127.0.0.1:8765"


def load_parts(src: str) -> list[str]:
    if src == "-":
        raw = sys.stdin.read()
    else:
        raw = Path(src).read_text(encoding="utf-8")
    parts = [p.strip() for p in raw.split("\n---\n") if p.strip()]
    if not parts:
        raise SystemExit("guion vacio")
    return parts


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from interfon_server.providers import tts_synthesize

    parts = load_parts(sys.argv[1])
    st = requests.get(f"{BASE}/api/status", timeout=5).json()
    if not st["phone_connected"]:
        print("TELEFONO NO CONECTADO - abri la app y reintenta")
        return 1

    print(f"Telefono conectado. Enviando {len(parts)} partes...")
    total = 0.0
    for i, part in enumerate(parts, 1):
        wav = tts_synthesize(part)
        with wave.open(io.BytesIO(wav), "rb") as w:
            dur = w.getnframes() / w.getframerate()
        total += dur
        r = requests.post(f"{BASE}/api/message", json={"text": part}, timeout=300)
        r.raise_for_status()
        print(f"  parte {i}/{len(parts)}: {dur:.0f}s de audio en camino")
        if i < len(parts):
            time.sleep(dur + 3)
    print(f"LISTO: papercast completo ({total/60:.1f} min de audio)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
