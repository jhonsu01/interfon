# 📞 Interfon — An intercom with your AI agent

[Español](../../README.md) · **English** · [中文](README.zh.md) · [Português](README.pt.md) · [한국어](README.ko.md) · [Русский](README.ru.md) · [日本語](README.ja.md) · [Français](README.fr.md)

**Interfon** turns your PC and your Android phone into a voice intercom: the agent can
**call you**, **talk with you** and **send you audio messages**; you can call it back or use
**walkie-talkie** mode. Everything runs **locally** (your WiFi network) against your own
inference API (Unsloth Studio), with no third-party services.

```
   ┌───────────────────────── PC (Windows) ─────────────────────────┐
   │                                                                │
   │  Agent (CLI) ──┐                                               │
   │                ▼                                               │
   │  ┌──────────────────────────┐    ┌───────────────────────────┐  │
   │  │  Interfon server         │    │  Unsloth Studio API       │  │
   │  │  (FastAPI + WebSocket)   │───▶│  http://127.0.0.1:8888    │  │
   │  │  :8765                   │    │  · LLM  gemma-4-E2B       │  │
   │  │  · VAD / STT / TTS       │    │  · STT  qwen3-asr-0.6b    │  │
   │  │  · Piper voice (Daniela) │    └───────────────────────────┘  │
   │  └───────────┬──────────────┘                                   │
   └──────────────┼──────────────────────────────────────────────────┘
                  │ WebSocket (JSON + audio), local WiFi — one or several PCs
                  ▼
   ┌───────────────────────── Android ──────────────────────────────┐
   │  Interfon app (Compose, dark mode)                             │
   │  · Receives calls (rings over the lock screen)                 │
   │  · Voice conversation with the agent (live transcript)         │
   │  · Walkie-talkie: hold to talk                                 │
   │  · Receives agent audio even when closed                       │
   └─────────────────────────────────────────────────────────────────┘
```

## Features

| Feature | How it works |
| --- | --- |
| 📲 Incoming calls | The agent runs `call_user.py` → the phone **rings** (full screen, vibration, ringtone) → you answer and talk. |
| 📞 Outgoing calls | "Call the agent" button in the app → instant voice conversation. |
| 🎙️ Walkie-talkie | Hold the button, release to send; the agent replies by voice. |
| 📻 Push audio | `send_audio.py "text"` → the app plays the audio even in the background (queued while disconnected). |
| 🔤 Speech to text | `qwen3-asr-0.6b` through your local API (~1.6 s per sentence). |
| 🧠 Brain | `gemma-4-E2B-it` (configurable) with `enable_thinking:false` for short spoken answers. |
| 🗣️ Agent voice | **Piper `es_AR-daniela-high`** with SAPI fallback (Sabina es-MX). Other voices: `python scripts/download_voice.py --list`. |
| 🌓 Dark mode | Forced, Material 3, green on blue-black theme. |
| ✍️ Transcript | Everything said (by both sides) shows on screen and is logged to `server/logs/`. |
| 📡 Auto-discovery | The app scans the local network and adds **every** server it finds by itself (no IP setup). |
| 🖧 Multiple servers | Simultaneous connection to several PCs. Any of them can call you or send audio; your calls and the walkie go through the first connected server in the list and, if it drops, the next one takes over (failover). Each server has a **custom name**. |
| 📋 Collapsible list | On Home, the servers card collapses and shows a `connected/total` counter; expanded, the status of each one. |
| 🌐 8 languages | Español, English, 中文, Português, 한국어, Русский, 日本語 and Français. Chosen on first launch and changeable in **Settings → Language** (on Android 13+ also in *System settings → App language*). |

## Getting started

### 1. PC: server

```powershell
cd Interfon
.\scripts\setup_server.ps1          # venv + dependencies + Piper voice (~114 MB from HuggingFace)
# Optional (as admin) to accept phone connections over WiFi:
.\scripts\setup_server.ps1 -Firewall

cd server
.\.venv\Scripts\python.exe serve.py
```

Requirements: **Unsloth Studio** (or any OpenAI-compatible API) running on `127.0.0.1:8888`
without a password. Set base URL and models in `server/.env`.

You can repeat this on several PCs: the app connects to all of them at once.

### 2. Android: the app

Install the APK from the latest [release](https://github.com/jhonsu01/interfon/releases)
(`Interfon-vX.Y.Z.apk`, signed). When you open it:

1. Choose the language (first time only).
2. Grant microphone and notifications.
3. **That's it**: the app discovers the servers by itself (scans the local network, checks
   `/api/status` and connects to all of them). If none is connected it searches again
   automatically. You can also force it with **🔎 Search for servers on the network**.
4. The **Servers** card shows `connected/total`; tap it to collapse or expand it.

#### Multiple servers

In **Settings → Servers** you can:

- **Add** a server by hand (`192.168.1.50`, `192.168.1.50:8765` or the full URL).
- **Rename** it (e.g. "Office PC", "Laptop") or change its URL with ✏️.
- **Raise its priority** with ⬆️: the first connected server in the list handles your outgoing
  calls and the walkie-talkie; if it goes down, the app moves to the next one on its own.
- **Delete** it from the same edit dialog.

All servers can call you and send you audio at the same time. If you are on a call with one
and another tries to call you, the app declines it as "busy".

> Over USB, `adb reverse tcp:8765 tcp:8765` plus adding the server
> `http://127.0.0.1:8765` also works.

### 3. Talk

```bash
# on the PC (from server/):
./.venv/Scripts/python.exe call_user.py "Got a minute?"                 # call the phone
./.venv/Scripts/python.exe send_audio.py "The build finished OK"        # push audio
./.venv/Scripts/python.exe send_audio.py "I'm tired" --reply            # the LLM writes the reply
./.venv/Scripts/python.exe status.py                                    # overall status
```

In the app: **Call the agent** to talk, **Walkie-Talkie** for short exchanges.

## 🤖 Guide for AI agents (session integration)

Any agent with access to the PC can use Interfon to **talk with the user**: hear what they
said by voice, reply with audio, call them or send them podcasts. Everything is HTTP against
`http://127.0.0.1:8765` (LAN, no authentication). In the examples,
`$S` = `server/.venv/Scripts/python.exe`.

### System status (always check first)

```bash
$S server/status.py        # or: curl http://127.0.0.1:8765/api/status
```

Key fields: `phone_connected` (is the app alive?), `llm.loaded`, `session.active`.

### Agent → human: send voice

| I want to | Call |
| --- | --- |
| Send audio (incoming-call effect) | `POST /api/message` `{"text": "..."}` |
| Call the phone (rings over the lock screen) | `POST /api/call` `{"text": "reason"}` — spoken when answered |
| Audio with a reply written by the local LLM | `POST /api/message` `{"text": "...", "reply": true}` |

```bash
$S server/send_audio.py "Build finished, all green"     # push audio
$S server/call_user.py "Got a minute?"                  # call
```

### Human → agent: hear what they said

Everything spoken (call, walkie) is transcribed to
`server/logs/transcripts-YYYY-MM-DD.jsonl`:

```json
{"ts": "2026-10-01T20:45:01", "kind": "stt", "ctx": "walkie", "text": "What's the grade..."}
```

`kind`: `stt` (human voice) · `agent` (spoken reply) · `message_pushed` · `telegram` · call
events. To resume a conversation: `tail` the day's file and filter `kind=stt`. Then reply with
`send_audio.py` — that is the full "talk over the phone from an agent session" loop.

### Ask without sending audio

```bash
curl -X POST http://127.0.0.1:8765/api/ask -H "Content-Type: application/json" \
     -d '{"text": "weather in medellin"}'
# → {"reply": "It's 20 degrees in Medellín...", "grounded": true}
```

Same skills as the phone: date/time/machine status, weather, news, internet/Wikipedia search,
Colombian holidays (and the LLM for everything else).

### Paper podcast (papercast)

1. Pick a recent, well-ranked paper (arXiv/trending) and summarize it in 4–8
   **colloquial parts** (~700 characters each, simple analogies, numbers intact —
   "explain it like I'm five" style).
2. Save the parts in a `.txt` separated by `---` lines.
3. Play it:

```bash
$S server/papercast.py script.txt   # each part arrives as a call, spaced out
```

### Tin-can telephone (two servers talk)

Two Interfon servers talk to each other and the phone hears every turn in the voice of whoever speaks. Each agent thinks with **its own LLM** and only knows what the other told it (it gets the recent conversation history).

```bash
python scripts/vaso.py "Hi, let's make up a story together" \
    --a http://192.168.1.50:8765 --nombre-a "the fifty" \
    --b http://192.168.1.8:8765  --nombre-b "the dev PC" --rondas 3
```

Words that trigger skills (weather, news, Wikipedia, date/time) are replaced with synonyms in the history so the LLM always answers.

### Telegram (optional)

`TELEGRAM_BOT_TOKEN` in `server/.env` (hot-detected, no restart). The user authorizes their
chat with `/start`; the bot answers with the same skills. To message them from the PC:
`$S server/telegram_send.py "text"`.

### Operating rules

- **Half-duplex**: do not send audio during an active call (`session.active`).
- Latencies: STT ~1.6 s · local LLM 2–10 s (first load ~90 s) · TTS ~1–4 s.
- Long audio: split into parts under 900 characters (better listening pace).
- The server is in charge: if you restart the PC, `serve.py` must run again.

## Measured latencies (Ryzen 5 3400G, iGPU)

| Step | Time |
| --- | --- |
| STT (~5 s sentence) | ~1.6 s |
| LLM gemma-4-E2B (short reply, warm) | ~2-10 s |
| TTS Piper daniela-high | ~1-4 s |
| **Total per exchange** | **~5-15 s** (small local model: this lag is expected) |

## Security

- Everything is **LAN-local**: no agent data leaves for the internet (only the voice download
  from HuggingFace).
- The server **has no authentication**: use it only on a trusted network.
- The signing keystore and `.env` live in `.secrets/` and are **never** pushed to the repo.

## Structure

```
Interfon/
├── android/               # Kotlin + Compose app (dark mode, adaptive icons, 8 languages)
├── server/                # FastAPI + WebSocket + providers (Unsloth API, Piper, SAPI)
│   ├── interfon_server/   #   main (protocol), providers, vad, wavutil, state, config
│   ├── serve.py           #   startup
│   ├── call_user.py       #   CLI: call the phone
│   ├── send_audio.py      #   CLI: send audio
│   └── status.py          #   CLI: status
├── scripts/
│   ├── setup_server.ps1   #   PC setup
│   ├── download_voice.py  #   Piper voices from HuggingFace
│   ├── publish_release.py #   bump + build + GitHub release
│   └── vaso.py            #   tin-can telephone between two servers
├── docs/arquitectura.md   # detailed WebSocket protocol
├── docs/i18n/             # this README in 7 more languages
└── directives/            # project operating procedure
```

## Publishing a new version

```bash
python scripts/publish_release.py patch   # or minor / major
```

Bumps `versionCode`, builds the signed APK, creates the tag and publishes the GitHub release.

## License

MIT — see [LICENSE](../../LICENSE).
