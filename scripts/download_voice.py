"""Descarga voces de Piper para el agente desde HuggingFace.

Uso:
  python scripts/download_voice.py            # es_AR-daniela-high (por defecto)
  python scripts/download_voice.py --list     # ver voces en espanol disponibles
  python scripts/download_voice.py --voice es/es_MX/claude/high
"""
import argparse
import json
import pathlib
import sys
import urllib.request

REPO = "rhasspy/piper-voices"
API = f"https://huggingface.co/api/models/{REPO}/tree/main"
DL = f"https://huggingface.co/{REPO}/resolve/main"
VOICES = pathlib.Path(__file__).resolve().parent.parent / "server" / "voices"

KNOWN = {
    "daniela": ("es/es_AR/daniela/high", "Voz femenina argentina (alta calidad)"),
    "claude": ("es/es_MX/claude/high", "Voz masculina mexicana (alta calidad)"),
}


def list_spanish():
    out = {}
    for code in ("es_AR", "es_ES", "es_MX"):
        try:
            with urllib.request.urlopen(f"{API}/es/{code}", timeout=20) as r:
                dirs = json.load(r)
            for d in dirs:
                if d["type"] == "directory":
                    out[d["path"]] = None
        except Exception as e:
            print(f"  (no se pudo listar {code}: {e})")
    print("Voces en espanol (ruta -> usar con --voice):")
    for p in sorted(out):
        tag = " <-- recomendada" if p.endswith("daniela/high") else ""
        print(f"  {p}{tag}")


def download(path: str):
    name = path.split("/")[-1]
    VOICES.mkdir(parents=True, exist_ok=True)
    for ext in (".onnx", ".onnx.json"):
        url = f"{DL}/{path}/{name}{ext}"
        dst = VOICES / f"{name}{ext}"
        if dst.exists() and dst.stat().st_size > 1000:
            print(f"Ya existe: {dst}")
            continue
        print(f"Descargando {url} …")
        with urllib.request.urlopen(url, timeout=120) as r, open(dst, "wb") as f:
            total = int(r.headers.get("Content-Length", 0))
            done = 0
            while True:
                chunk = r.read(1 << 20)
                if not chunk:
                    break
                f.write(chunk)
                done += len(chunk)
                if total:
                    print(f"\r  {done/1e6:.0f}/{total/1e6:.0f} MB", end="", flush=True)
        print()
    print(f"Listo: {VOICES / name}.onnx")
    print(f"En server/.env usa:  PIPER_MODEL=voices/{name}.onnx")


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--voice", help="ruta completa (es/es_AR/daniela/high) o atajo (daniela, claude)")
    ap.add_argument("--list", action="store_true", help="listar voces en espanol")
    args = ap.parse_args()

    if args.list:
        list_spanish()
        return
    voice = args.voice or "daniela"
    if voice in KNOWN:
        voice = KNOWN[voice][0]
    download(voice)


if __name__ == "__main__":
    sys.exit(main())
