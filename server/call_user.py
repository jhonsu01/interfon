"""Llamar al telefono interno:  python call_user.py ["frase inicial"]"""
import argparse
import sys

import requests

BASE = "http://127.0.0.1:8765"


def main() -> int:
    ap = argparse.ArgumentParser(description="El agente llama al telefono Interfon.")
    ap.add_argument("text", nargs="*", help="frase inicial que se dira al contestar")
    args = ap.parse_args()
    text = " ".join(args.text).strip() or None
    try:
        r = requests.post(f"{BASE}/api/call", json={"text": text}, timeout=10)
    except requests.ConnectionError:
        print("El servidor Interfon no responde en", BASE)
        return 2
    if r.status_code == 409:
        print("El telefono no esta conectado al servidor.")
        return 1
    r.raise_for_status()
    d = r.json()
    print(f"Llamando... call_id={d['call_id']} (suena {d['timeout_s']}s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
