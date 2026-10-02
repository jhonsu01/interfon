"""Puente Telegram <-> Interfon.

- Escucha mensajes del bot (long polling, sin dependencias extra).
- Cada texto se responde via /api/ask del servidor Interfon (mismas
  habilidades: fecha/hora/sistema, clima, noticias, busqueda, LLM).
- Con TELEGRAM_MIRROR_PHONE=true ademas envia la respuesta como audio
  al telefono (usa la TTS del servidor).

Requiere TELEGRAM_BOT_TOKEN en .secrets/.env o server/.env (crea el bot con @BotFather).
TELEGRAM_BOT_NAME (opcional) es el @usuario del bot: se verifica contra el token.
El primer chat que envie /start queda autorizado (se guarda el chat_id).
"""
import pathlib
import re
import sys
import time

import requests

SERVER_DIR = pathlib.Path(__file__).resolve().parent
LOCK_FILE = SERVER_DIR / ".telegram_chat_id"
INTERFON = "http://127.0.0.1:8765"


# Se busca primero en .secrets/.env (fuera del repo) y luego en server/.env
ENV_FILES = (SERVER_DIR.parent / ".secrets" / ".env", SERVER_DIR / ".env")


def read_env_value(key: str) -> str:
    for env in ENV_FILES:
        if not env.exists():
            continue
        m = re.search(rf"^{key}=(.*)$", env.read_text(encoding="utf-8-sig"), re.M)
        if m and m.group(1).strip():
            return m.group(1).strip()
    return ""


def tg_api(token: str) -> str:
    return f"https://api.telegram.org/bot{token}"


def send_text(token: str, chat_id: int, text: str) -> None:
    # Trocear en mensajes de <=3800 chars (limite de Telegram: 4096)
    for i in range(0, len(text), 3800):
        requests.post(tg_api(token) + "/sendMessage",
                      json={"chat_id": chat_id, "text": text[i:i + 3800]},
                      timeout=30)


def _answer(text: str) -> str:
    r = requests.post(f"{INTERFON}/api/ask", json={"text": text}, timeout=300)
    r.raise_for_status()
    return r.json()["reply"]


def run_bridge(token: str) -> None:
    """Bucle bloqueante de polling. Retorna solo ante error fatal."""
    api = tg_api(token)
    me = requests.get(api + "/getMe", timeout=15).json()["result"]
    print(f"[telegram] Puente activo como @{me.get('username')}")
    esperado = read_env_value("TELEGRAM_BOT_NAME").lstrip("@")
    if esperado and esperado.lower() != (me.get("username") or "").lower():
        print(f"[telegram] AVISO: TELEGRAM_BOT_NAME=@{esperado} pero el token es de "
              f"@{me.get('username')}; revisa .secrets/.env")

    chat_id: int | None = None
    if LOCK_FILE.exists():
        try:
            chat_id = int(LOCK_FILE.read_text().strip())
            print(f"[telegram] Chat autorizado previo: {chat_id}")
        except ValueError:
            pass

    offset = None
    while True:
        try:
            params = {"timeout": 25}
            if offset is not None:
                params["offset"] = offset
            updates = requests.get(api + "/getUpdates", params=params, timeout=35).json()
            for u in updates.get("result", []):
                offset = u["update_id"] + 1
                msg = u.get("message") or u.get("edited_message")
                if not msg:
                    continue
                cid = msg["chat"]["id"]
                text = (msg.get("text") or "").strip()

                if text.startswith("/start"):
                    if chat_id is None:
                        chat_id = cid
                        LOCK_FILE.write_text(str(cid))
                        print(f"[telegram] Chat autorizado: {cid}")
                    send_text(token, chat_id,
                              "Puente Interfon activo. Escribeme lo que quieras: "
                              "clima, noticias, busquedas o conversar. "
                              "Lo que me escribas tambien puede sonar en tu telefono.")
                    continue
                if text.startswith("/ping"):
                    send_text(token, cid, "pong")
                    continue

                if chat_id is None:
                    send_text(token, cid, "Envia /start para autorizar este chat.")
                    continue
                if cid != chat_id:
                    continue  # ignorar chats no autorizados

                if not text:
                    send_text(token, cid, "Por ahora solo proceso mensajes de texto.")
                    continue

                print(f"[telegram] > {text}")
                try:
                    reply = _answer(text)
                except Exception as e:
                    reply = f"(el servidor no respondio: {e})"
                print(f"[telegram] < {reply[:120]}")
                send_text(token, chat_id, reply)

                if read_env_value("TELEGRAM_MIRROR_PHONE").lower() == "true":
                    try:
                        requests.post(f"{INTERFON}/api/message",
                                      json={"text": reply}, timeout=300)
                    except Exception:
                        pass
        except requests.RequestException as e:
            print(f"[telegram] error de red: {e}; reintentando en 5s")
            time.sleep(5)
        except Exception as e:
            print(f"[telegram] error: {e}; reintentando en 5s")
            time.sleep(5)
