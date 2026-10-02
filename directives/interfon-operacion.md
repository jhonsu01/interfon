# Directiva: Operación de Interfon (teléfono interno agente↔humano)

## Metadata
- **Arquitectura:** HIBRIDA
- **Score:** DET: 7 | STO: 4
- **Temperatura LLM:** 0.6 (conversación), 0.0 (builds/scripts)
- **Creado:** 2026-10-01
- **Entidades:** API Unsloth Studio (127.0.0.1:8888), voz Piper es_AR-daniela-high

## Objetivo
Comunicación de voz bidireccional entre el agente del PC y el teléfono Android del usuario:
llamadas, walkie-talkie y audios push, todo local.

## Puesta en marcha (orden obligatorio)
1. Unsloth Studio corriendo (puerto 8888, sin contraseña).
2. `server/.venv/Scripts/python.exe server/serve.py` (servidor :8765).
3. App Android instalada y conectada a `http://<IP-PC>:8765`.

## Entradas
- Audio del micrófono del teléfono (PCM16 16k).
- Textos del agente vía REST (`/api/call`, `/api/message`).

## Salidas
- Voz del agente (WAV Piper/SAPI) al teléfono.
- Transcripciones en pantalla y en `server/logs/transcripts-*.jsonl`.
- APK firmado en GitHub Releases.

## Reglas de operación
- **Nunca cerrar el servidor sin verificar que no hay llamada activa** (`status.py`).
- El modelo LLM debe cargarse con `chat_template_kwargs.enable_thinking=false` o la
  respuesta llega vacía (Gemma-4 razona por defecto).
- STT: enviar SIEMPRE WAV (multipart `file=`), modelo `qwen3-asr-0.6b`.
- Compilar SIEMPRE con `.\gradlew` (wrapper), NUNCA `gradle` global.
- BOM 2026.09.00 de Compose exige AGP 9.1: **usar BOM 2026.06.01 + AGP 8.13.0**.
- Voz Piper y keystore NUNCA al repo (están en .gitignore: `server/voices/`, `.secrets/`).

## Casos borde conocidos
- Teléfono desconectado al llamar → 409; usar `send_audio.py` (queda en cola).
- Llamada sin contestar → `call_end` a los 45 s (RING_TIMEOUT).
- Primer uso del LLM tras arranque de Unsloth → hasta ~90 s de carga del GGUF.
- Firewall de Windows bloquea el 8765 entrante → `setup_server.ps1 -Firewall` (admin).

## Historial de aprendizajes
| Fecha | Problema | Solución |
| --- | --- | --- |
| 2026-10-01 | Chat devolvía vacío | `enable_thinking:false` en `chat_template_kwargs` |
| 2026-10-01 | VAD no disparaba | Calibraba ruido con voz inicial; umbral absoluto 1200 + EMA |
| 2026-10-01 | `/inference/load` daba 405 | Ruta correcta: `POST /v1/load` con `model_path` |
| 2026-10-01 | BOM 2026.09 exige AGP 9.1 | Bajar a BOM 2026.06.01 + lifecycle 2.10.0 |
| 2026-10-01 | audioop eliminado en Py 3.13+ | RMS manual en vad.py |
