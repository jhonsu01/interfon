"""Enviar un audio al telefono (estilo walkie):  python send_audio.py "texto" [--reply]"""
import argparse
import sys

import requests

BASE = "http://127.0.0.1:8765"


def main() -> int:
    ap = argparse.ArgumentParser(description="El agente envia un audio al telefono.")
    ap.add_argument("text", help="texto que se sintetizara y enviara")
    ap.add_argument("--reply", action="store_true",
                    help="el LLM redacta la respuesta en lugar de leer el texto tal cual")
    args = ap.parse_args()
    try:
        r = requests.post(f"{BASE}/api/message",
                          json={"text": args.text, "reply": args.reply}, timeout=300)
    except requests.ConnectionError:
        print("El servidor Interfon no responde en", BASE)
        return 2
    r.raise_for_status()
    d = r.json()
    if d["delivered"]:
        print("Audio entregado:", d["text"])
    else:
        print("Telefono desconectado; el audio quedo en cola y sonara al reconectar.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
