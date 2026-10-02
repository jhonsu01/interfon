# 📞 Interfon — Teléfono interno con tu agente de IA

**Español** · [English](docs/i18n/README.en.md) · [中文](docs/i18n/README.zh.md) · [Português](docs/i18n/README.pt.md) · [한국어](docs/i18n/README.ko.md) · [Русский](docs/i18n/README.ru.md) · [日本語](docs/i18n/README.ja.md) · [Français](docs/i18n/README.fr.md)

**Interfon** convierte tu PC y tu Android en un teléfono interno por voz: el agente puede
**llamarte**, **conversar contigo** y **enviarte audios**; tú puedes llamarlo o usar el modo
**walkie-talkie**. Todo corre **en local** (tu red WiFi) contra tu propia API de inferencia
(Unsloth Studio), sin servicios de terceros.

```
   ┌───────────────────────── PC (Windows) ─────────────────────────┐
   │                                                                │
   │  Agente (CLI) ──┐                                              │
   │                 ▼                                              │
   │  ┌──────────────────────────┐    ┌───────────────────────────┐  │
   │  │  Servidor Interfon       │    │  Unsloth Studio API       │  │
   │  │  (FastAPI + WebSocket)   │───▶│  http://127.0.0.1:8888    │  │
   │  │  :8765                   │    │  · LLM  gemma-4-E2B       │  │
   │  │  · VAD / STT / TTS       │    │  · STT  qwen3-asr-0.6b    │  │
   │  │  · voz Piper (Daniela)   │    └───────────────────────────┘  │
   │  └───────────┬──────────────┘                                   │
   └──────────────┼──────────────────────────────────────────────────┘
                  │ WebSocket (JSON + audio), WiFi local — uno o varios PCs
                  ▼
   ┌───────────────────────── Android ──────────────────────────────┐
   │  App Interfon (Compose, modo oscuro)                           │
   │  · Recibe llamadas (timbra sobre el bloqueo)                   │
   │  · Conversa por voz con el agente (transcripcion en vivo)      │
   │  · Walkie-talkie: mantén presionado para hablar                │
   │  · Recibe audios del agente aunque esté cerrada                │
   └─────────────────────────────────────────────────────────────────┘
```

## Características

| Función | Cómo funciona |
| --- | --- |
| 📲 Llamadas entrantes | El agente ejecuta `call_user.py` → el teléfono **timbra** (pantalla completa, vibración, tono) → contestas y conversas por voz. |
| 📞 Llamadas salientes | Botón "Llamar al agente" en la app → conversación de voz inmediata. |
| 🎙️ Walkie-talkie | Mantén presionado el botón, suelta para enviar; el agente responde con voz. |
| 📻 Audios push | `send_audio.py "texto"` → la app reproduce el audio aunque esté en segundo plano (queda en cola si está desconectada). |
| 🔤 Voz a texto | `qwen3-asr-0.6b` vía tu API local (~1.6 s por frase). |
| 🧠 Cerebro | `gemma-4-E2B-it` (configurable) con `enable_thinking:false` para respuestas de voz breves. |
| 🗣️ Voz del agente | **Piper `es_AR-daniela-high`** (femenina argentina) con respaldo SAPI (Sabina es-MX). Otras voces: `python scripts/download_voice.py --list`. |
| 🌓 Modo oscuro | Forzado, Material 3, tema verde sobre negro azulado. |
| ✍️ Transcripción | Todo lo dicho (por ambos) aparece en pantalla y se registra en `server/logs/`. |
| 📡 Auto-descubrimiento | La app escanea la red local y agrega sola **todos** los servidores que encuentre (sin configurar IP). |
| 🖧 Varios servidores | Conexión simultánea con varios PCs. Cualquiera puede llamarte o enviarte audios; tus llamadas y el walkie salen por el primero conectado de la lista y, si cae, toma el relevo el siguiente (failover). Cada servidor lleva un **nombre personalizable**. |
| 📋 Lista plegable | En Inicio, la tarjeta de servidores se pliega y muestra el contador `conectados/total`; desplegada, el estado de cada uno. |
| 🌐 8 idiomas | Español, English, 中文, Português, 한국어, Русский, 日本語 y Français. Se elige al primer arranque y se cambia en **Ajustes → Idioma** (en Android 13+ también en *Ajustes del sistema → Idioma de la app*). |

## Puesta en marcha

### 1. PC: servidor

```powershell
cd Interfon
.\scripts\setup_server.ps1          # venv + dependencias + voz Piper (~114 MB desde HuggingFace)
# Opcional (como admin) para recibir conexiones del teléfono por WiFi:
.\scripts\setup_server.ps1 -Firewall

cd server
.\.venv\Scripts\python.exe serve.py
```

Requisitos: **Unsloth Studio** (u otra API compatible OpenAI) corriendo en `127.0.0.1:8888`
sin contraseña. Configura base/modelos en `server/.env`.

Puedes repetirlo en varios PCs: la app se conecta a todos a la vez.

### 2. Android: la app

Instala el APK de la última [release](../../releases) (`Interfon-vX.Y.Z.apk`, firmado).
Al abrirla:

1. Elige el idioma (solo la primera vez).
2. Concede micrófono y notificaciones.
3. **Nada más**: la app descubre los servidores sola (escanea la red local, valida
   `/api/status` y conecta con todos). Si no hay ninguno conectado, vuelve a buscar
   automáticamente. También puedes forzarlo con **🔎 Buscar servidores en la red**.
4. La tarjeta **Servidores** muestra `conectados/total`; tócala para plegarla o desplegarla.

#### Varios servidores

En **Ajustes → Servidores** puedes:

- **Agregar** un servidor a mano (`192.168.1.50`, `192.168.1.50:8765` o la URL completa).
- **Renombrarlo** (p. ej. "PC oficina", "Portátil") o cambiar su URL con ✏️.
- **Subir su prioridad** con ⬆️: el primero conectado de la lista atiende tus llamadas
  salientes y el walkie; si se cae, la app pasa al siguiente sin que hagas nada.
- **Eliminarlo** desde el mismo diálogo de edición.

Todos los servidores pueden llamarte y enviarte audios a la vez. Si estás en una llamada
con uno y otro intenta llamarte, la app lo rechaza como "ocupado".

> Con USB también sirve `adb reverse tcp:8765 tcp:8765` y agregar el servidor
> `http://127.0.0.1:8765`.

### 3. Hablar

```bash
# en el PC (desde server/):
./.venv/Scripts/python.exe call_user.py "¿Tenés un minuto?"           # llamar al teléfono
./.venv/Scripts/python.exe send_audio.py "La compilación terminó OK"  # audio push
./.venv/Scripts/python.exe send_audio.py "estoy cansado" --reply      # el LLM redacta
./.venv/Scripts/python.exe status.py                                  # estado general
```

Desde la app: **Llamar al agente** para conversar, **Walkie-Talkie** para intercambios cortos.

## 🤖 Guía para agentes de IA (integración de sesión)

Cualquier agente con acceso a este PC puede usar Interfon para **hablar con el usuario**:
escuchar lo que dijo por voz, responder con audios, llamarlo o mandarle podcasts.
Todo es HTTP contra `http://127.0.0.1:8765` (LAN, sin autenticación). En los
ejemplos, `$S` = `server/.venv/Scripts/python.exe`.

### Estado del sistema (verificar siempre primero)

```bash
$S server/status.py        # o: curl http://127.0.0.1:8765/api/status
```

Clave: `phone_connected` (¿la app está viva?), `llm.loaded`, `session.active`.

### Agente → humano: enviar voz

| Quiero | Llamada |
| --- | --- |
| Enviar un audio (efecto llamada entrante) | `POST /api/message` `{"text": "..."}` |
| Llamar al teléfono (timbra sobre el bloqueo) | `POST /api/call` `{"text": "motivo"}` — se dice al contestar |
| Audio con respuesta redactada por el LLM local | `POST /api/message` `{"text": "...", "reply": true}` |

```bash
$S server/send_audio.py "Terminé el build, todo verde"     # audio push
$S server/call_user.py "¿Tenés un minuto?"                 # llamada
```

### Humano → agente: escuchar lo que dijo

Todo lo hablado (llamada, walkie) queda transcrito en
`server/logs/transcripts-AAAA-MM-DD.jsonl`:

```json
{"ts": "2026-10-01T20:45:01", "kind": "stt", "ctx": "walkie", "text": "¿Cuál es la nota..."}
```

`kind`: `stt` (voz del humano) · `agent` (respuesta hablada) · `message_pushed` ·
`telegram` · eventos de llamada. Para retomar una conversación: `tail` del día y
filtrar `kind=stt`. Luego responde con `send_audio.py` — ese es el ciclo completo
de "conversar por el teléfono desde una sesión de agente".

### Preguntar sin enviar audio

```bash
curl -X POST http://127.0.0.1:8765/api/ask -H "Content-Type: application/json" \
     -d '{"text": "clima en medellin"}'
# → {"reply": "En Medellín hay 20 grados...", "grounded": true}
```

Mismas habilidades del teléfono: fecha/hora/estado del equipo, clima, noticias,
búsqueda en internet/Wikipedia, festivos de Colombia (y LLM para el resto).

### Podcast de papers (papercast)

1. Elige un paper reciente y bien rankeado (arXiv/trending) y resúmelo en 4–8
   **partes coloquiales** (~700 caracteres cada una, español con tildes,
   analogías simples, números intactos — estilo "explícalo a un niño").
2. Guarda las partes en un `.txt` separadas por líneas `---`.
3. Reproduce:

```bash
$S server/papercast.py guion.txt   # cada parte entra como llamada, espaciada
```

### Telegram (opcional)

`TELEGRAM_BOT_TOKEN` en `server/.env` (detección en caliente, sin reiniciar).
El usuario autoriza su chat con `/start`; el bot responde con las mismas
habilidades. Para escribirle desde el PC: `$S server/telegram_send.py "texto"`.

### Reglas operativas

- **Half-duplex**: no envíes audios durante una llamada activa (`session.active`).
- Latencias: STT ~1,6 s · LLM local 2–10 s (primera carga ~90 s) · TTS ~1–4 s.
- Audios largos: dividelos en partes de <900 caracteres (mejor ritmo de escucha).
- El servidor es quien manda: si reiniciás el PC, `serve.py` debe volver a correr.

## Latencias medidas (Ryzen 5 3400G, iGPU)

| Paso | Tiempo |
| --- | --- |
| STT (frase de ~5 s) | ~1.6 s |
| LLM gemma-4-E2B (respuesta corta, en caliente) | ~2-10 s |
| TTS Piper daniela-high | ~1-4 s |
| **Total por intercambio** | **~5-15 s** (modelo local pequeño: es el lag esperado) |

## Seguridad

- Todo es **LAN-local**: no salen datos del agente a internet (solo la descarga de la voz
  desde HuggingFace).
- El servidor **no tiene autenticación**: úsalo solo en tu red de confianza.
- El keystore de firma y `.env` viven en `.secrets/` y **nunca** se suben al repo.

## Estructura

```
Interfon/
├── android/               # App Kotlin + Compose (modo oscuro, iconos adaptativos, 8 idiomas)
├── server/                # FastAPI + WebSocket + proveedores (Unsloth API, Piper, SAPI)
│   ├── interfon_server/   #   main (protocolo), providers, vad, wavutil, state, config
│   ├── serve.py           #   arranque
│   ├── call_user.py       #   CLI: llamar al teléfono
│   ├── send_audio.py      #   CLI: enviar audio
│   └── status.py          #   CLI: estado
├── scripts/
│   ├── setup_server.ps1   #   preparación del PC
│   ├── download_voice.py  #   voces Piper desde HuggingFace
│   └── publish_release.py #   bump + build + release en GitHub
├── docs/arquitectura.md   # protocolo WebSocket detallado
├── docs/i18n/             # este README en 7 idiomas más
└── directives/            # POE de operación del proyecto
```

## Publicar una nueva versión

```bash
python scripts/publish_release.py patch   # o minor / major
```

Sube `versionCode`, compila el APK firmado, crea el tag y publica la release en GitHub.

## Licencia

MIT — ver [LICENSE](LICENSE).
