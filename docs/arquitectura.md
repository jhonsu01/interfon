# Arquitectura y protocolo Interfon

## Flujo general

```
App Android  ⇄  WebSocket  ⇄  Servidor Interfon  ⇄  Unsloth Studio API (LLM + STT)
                                   ⇕
                            TTS local (Piper / SAPI)
```

- **Transporte:** un único WebSocket (`/ws`) por teléfono. JSON para control, binario para audio.
- **Audio:** PCM16 mono 16 kHz en llamada (streaming); WAV completo en walkie. Las respuestas
  del agente siempre llegan como WAV binario.
- **Half-duplex:** mientras el agente habla, la app pausa el micro (eco imposible). El servidor
  también descarta PCM cuando no está en estado `listening`.

## Mensajes (JSON)

### App → Servidor

| type | campos | descripción |
| --- | --- | --- |
| `hello` | `app`, `device` | saludo al conectar |
| `ping` | — | keepalive (res `pong`) |
| `call_outgoing` | — | la app inicia una llamada |
| `call_answer` | `call_id` | contesta llamada entrante |
| `call_decline` | `call_id` | rechaza |
| `hangup` | — | colgar |
| `walkie_tx` | — | anuncia que el siguiente binario es un clip WAV de walkie |
| *(binario)* | PCM16 crudo | en llamada, mientras el servidor está `listening` |
| *(binario)* | WAV (RIFF) | clip completo de walkie |

### Servidor → App

| type | campos | descripción |
| --- | --- | --- |
| `welcome` | `server`, `version`, `agent` | conexión establecida |
| `incoming_call` | `call_id`, `from`, `text` | timbrar (pantalla completa + notificación) |
| `call_started` | `call_id` | llamada activa |
| `call_state` | `state` | `listening` \| `thinking` \| `speaking` |
| `transcript` | `role`, `text`, `ctx` | texto reconocido o respuesta (`ctx`: `call`/`walkie`) |
| `agent_audio` | `kind`, `text` | precede al binario WAV (`kind`: `call`/`walkie`/`message`) |
| `agent_audio_end` | — | el audio terminó de llegar |
| `walkie_done` / `walkie_empty` | — | fin de procesamiento walkie |
| `call_end` | `reason` | llamada finalizada |

## Máquina de estados de la llamada (servidor)

```
        call_outgoing                incoming_call (REST /api/call)
             │                              │
             ▼                              ▼
          listening  ◀──answer────────  ringing ──45s──▶ ended("sin respuesta")
             │                                   │
   VAD fin de frase                     decline
             │                                   ▼
             ▼                            ended("rechazada")
          thinking
   (STT → LLM → TTS)
             │
             ▼
          speaking ──audio enviado──▶ listening
```

## VAD (detección de fin de frase)

- Frames de 30 ms, RMS sobre PCM16.
- Umbral absoluto `max(1200, ruido*4)` con `ruido` adaptado lentamente (EMA 5 %) en frames sin voz.
- Inicio de frase: 6 frames con voz consecutivos (180 ms).
- Fin de frase: 24 frames sin voz (720 ms) o tope de 12 s.

## REST del servidor (para los CLIs)

| Endpoint | cuerpo | efecto |
| --- | --- | --- |
| `GET /api/status` | — | estado completo (teléfono, LLM cargado, TTS, sesión) |
| `POST /api/call` | `{"text": "..."}` | llama al teléfono; `text` se dice al contestar |
| `POST /api/message` | `{"text": "...", "reply": false}` | push de audio (`reply:true` → el LLM redacta) |

## Firma y releases

- Keystore propio del proyecto en `.secrets/interfon-release.keystore` (Regla de Oro: un
  keystore por proyecto). `keystore.properties` inyecta la firma en `app/build.gradle.kts`;
  si no existe, compila con firma debug (repo público sin secretos).
- `scripts/publish_release.py patch|minor|major` automatiza: bump de `VERSION` y
  `versionCode/versionName` → `gradlew assembleRelease` → `dist/Interfon-vX.Y.Z.apk` →
  tag + GitHub Release.
