"""Enviar un mensaje por Telegram al chat autorizado:  python telegram_send.py "texto"

(El agente del PC usa esto para escribirle al usuario por Telegram.)
"""
import pathlib
import sys

import requests

from telegram_bridge import LOCK_FILE, read_env_value, send_text

SERVER_DIR = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(SERVER_DIR))


def main() -> int:
    if len(sys.argv) < 2 or not sys.argv[1].strip():
        print('Uso: python telegram_send.py "texto"')
        return 2
    token = read_env_value("TELEGRAM_BOT_TOKEN")
    if not token:
        print("Falta TELEGRAM_BOT_TOKEN en .secrets/.env (crea el bot con @BotFather).")
        return 1
    if not LOCK_FILE.exists():
        print("Nadie ha enviado /start al bot todavia (no hay chat autorizado).")
        return 1
    chat_id = int(LOCK_FILE.read_text().strip())
    send_text(token, chat_id, " ".join(sys.argv[1:]))
    print(f"Enviado a Telegram (chat {chat_id}).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
