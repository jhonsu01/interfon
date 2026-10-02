"""Estado del servidor Interfon:  python status.py"""
import sys

import requests

BASE = "http://127.0.0.1:8765"

try:
    d = requests.get(f"{BASE}/api/status", timeout=5).json()
except Exception as e:
    print("Servidor caido:", e)
    sys.exit(2)

print(f"Interfon v{d['version']} | agente: {d['agent']} | uptime {d['uptime_s']}s")
print(f"  Telefono : {'CONECTADO' if d['phone_connected'] else 'desconectado'}")
print(f"  LLM      : {d['llm']['model']} ({'cargado' if d['llm']['loaded'] else 'NO cargado'}) @ {d['llm']['base']}")
print(f"  STT      : {d['stt_model']}")
print(f"  TTS      : {d['tts_engine']}")
s = d["session"]
print(f"  Sesion   : {'activa (' + str(s['state']) + ')' if s['active'] else 'inactiva'}")
if d["pending_outbox"]:
    print(f"  En cola  : {d['pending_outbox']} audio/s pendiente/s")
