"""Servidor Interfon: puente de voz entre el agente (PC) y el telefono Android.

Protocolo WebSocket (JSON para control, binario para audio):
  App -> Servidor:
    {"type":"hello"}                                   saludo inicial
    {"type":"ping"}                                    keepalive
    {"type":"call_outgoing"}                           la app inicia la llamada
    {"type":"call_answer","call_id":...}               contesta llamada entrante
    {"type":"call_decline","call_id":...}              rechaza
    {"type":"hangup"}                                  colgar
    binario PCM16 16k mono (en llamada, mientras se escucha)
    binario WAV completo (clip de walkie)

  Servidor -> App:
    {"type":"welcome",...}
    {"type":"pong"}
    {"type":"incoming_call","call_id","from","text"}   suena el telefono
    {"type":"call_started","call_id"}
    {"type":"call_state","state":"listening|thinking|speaking"}
    {"type":"transcript","role":"user|agent","text","ctx"}
    {"type":"agent_audio","kind":"call|walkie|message","text"} + binario WAV
    {"type":"agent_audio_end"}
    {"type":"walkie_done"} / {"type":"walkie_empty"}
    {"type":"call_end","reason"}
"""
import asyncio
import json
import logging
import time
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, WebSocket, WebSocketDisconnect
from fastapi.concurrency import run_in_threadpool
from pydantic import BaseModel

from . import __version__
from .config import CFG
from .providers import UnslothAPI, sapi_tts, tts_synthesize
from .state import State
from .wavutil import parse_wav, pcm_to_wav

logging.basicConfig(level=logging.INFO,
                    format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("interfon")

state = State()
api = UnslothAPI(CFG.unsloth_base, CFG.llm_model, CFG.stt_model)


async def _startup_tasks() -> None:
    def warm() -> None:
        try:
            api.ensure_llm_loaded()
        except Exception as e:  # la primera llamada lo cargara con reintentos
            log.warning("No se pudo precargar el LLM (%s); se reintentara al chatear.", e)
        try:
            sapi_tts("Interfon listo", CFG.tts_voice_sapi)
            log.info("TTS SAPI verificado con la voz '%s'.", CFG.tts_voice_sapi)
        except Exception as e:
            log.warning("TTS SAPI no verificado: %s", e)
    await asyncio.get_event_loop().run_in_executor(None, warm)


@asynccontextmanager
async def lifespan(app: FastAPI):
    log.info("Interfon v%s | API %s | LLM %s | STT %s | TTS %s",
             __version__, CFG.unsloth_base, CFG.llm_model, CFG.stt_model, CFG.tts_engine)
    asyncio.create_task(_startup_tasks())
    yield


app = FastAPI(title="Interfon Server", version=__version__, lifespan=lifespan)


# ============================================================
# REST (para los scripts del agente)
# ============================================================

class CallBody(BaseModel):
    text: str | None = None      # frase inicial que el agente dira al contestar


class MessageBody(BaseModel):
    text: str
    reply: bool = False          # True: el LLM redacta la respuesta en vez de leer el texto


@app.get("/api/status")
async def status():
    llm_loaded = await run_in_threadpool(api.loaded_models)
    return {
        "ok": True,
        "server": "interfon",
        "version": __version__,
        "agent": CFG.agent_name,
        "uptime_s": round(time.time() - state.started, 1),
        "phone_connected": state.phone_connected,
        "llm": {"base": CFG.unsloth_base, "model": CFG.llm_model,
                "loaded": bool(llm_loaded)},
        "stt_model": CFG.stt_model,
        "tts_engine": CFG.tts_engine,
        "session": {"active": bool(state.session and state.session.state != "ended"),
                    "state": state.session.state if state.session else None,
                    "mode": state.session.mode if state.session else None},
        "pending_outbox": len(state.outbox),
    }


@app.post("/api/call")
async def place_call(body: CallBody):
    if not state.phone_connected:
        raise HTTPException(409, detail="El telefono no esta conectado al servidor.")
    session = state.new_session("call")
    session.speak_first = (body.text or "").strip() or None
    await state.broadcast({"type": "incoming_call", "call_id": session.id,
                           "from": CFG.agent_name, "text": session.speak_first or ""})
    asyncio.create_task(_ring_timeout(session.id))
    await state.log_event("call_placed", call_id=session.id, text=session.speak_first)
    return {"call_id": session.id, "ringing": True, "timeout_s": CFG.ring_timeout}


@app.post("/api/message")
async def push_message(body: MessageBody):
    text = body.text.strip()
    if not text:
        raise HTTPException(400, detail="text vacio")
    if body.reply:
        text = await run_in_threadpool(
            api.chat,
            [{"role": "system", "content": CFG.system_prompt},
             {"role": "user", "content": text}])
    wav = await run_in_threadpool(tts_synthesize, text)
    msg = {"type": "agent_message", "title": CFG.agent_name, "text": text}
    if state.phone_connected:
        await state.broadcast(msg)
        await state.send_bytes(wav)
        await state.broadcast({"type": "agent_audio_end"})
        delivered = True
    else:
        state.outbox.append((msg, wav))
        delivered = False
    await state.log_event("message_pushed", text=text, delivered=delivered)
    return {"delivered": delivered, "text": text}


# ============================================================
# Pipeline de conversacion
# ============================================================

async def _send_audio(kind: str, text: str, wav: bytes) -> None:
    await state.broadcast({"type": "agent_audio", "kind": kind, "text": text})
    await state.send_bytes(wav)
    await state.broadcast({"type": "agent_audio_end"})


async def _speak_and_listen(session) -> None:
    session.state = "listening"
    await state.broadcast({"type": "call_state", "state": "listening"})


async def _process_utterance(session, pcm: bytes) -> None:
    try:
        session.state = "thinking"
        await state.broadcast({"type": "call_state", "state": "thinking"})
        text = await run_in_threadpool(api.stt, pcm_to_wav(pcm))
        if not text:
            await _speak_and_listen(session)
            return
        await state.broadcast({"type": "transcript", "role": "user", "text": text, "ctx": "call"})
        await state.log_event("stt", ctx="call", text=text)
        session.history.append({"role": "user", "content": text})
        session.trim()
        reply = await run_in_threadpool(
            api.chat,
            [{"role": "system", "content": CFG.system_prompt}, *session.history])
        session.history.append({"role": "assistant", "content": reply})
        session.trim()
        await state.broadcast({"type": "transcript", "role": "agent", "text": reply, "ctx": "call"})
        await state.log_event("agent", ctx="call", text=reply)
        wav = await run_in_threadpool(tts_synthesize, reply)
        session.state = "speaking"
        await state.broadcast({"type": "call_state", "state": "speaking"})
        await _send_audio("call", reply, wav)
    except Exception as e:
        log.exception("Error procesando frase: %s", e)
        try:
            wav = await run_in_threadpool(tts_synthesize, "Ocurrio un error interno.")
            await _send_audio("call", "Ocurrio un error interno.", wav)
        except Exception:
            pass
    finally:
        if state.session is session and session.state != "ended":
            await _speak_and_listen(session)


async def _end_session(reason: str) -> None:
    s = state.session
    if not s or s.state == "ended":
        return
    s.state = "ended"
    s.ended_reason = reason
    await state.broadcast({"type": "call_end", "reason": reason})
    await state.log_event("call_ended", reason=reason, duration_s=round(time.time() - s.created, 1))


async def _ring_timeout(call_id: str) -> None:
    await asyncio.sleep(CFG.ring_timeout)
    s = state.session
    if s and s.id == call_id and s.state == "ringing":
        await _end_session("sin respuesta")


async def _process_walkie(wav_bytes: bytes) -> None:
    await state.broadcast({"type": "call_state", "state": "thinking"})
    try:
        text = await run_in_threadpool(api.stt, wav_bytes)
        if not text:
            await state.broadcast({"type": "walkie_empty"})
            return
        await state.broadcast({"type": "transcript", "role": "user", "text": text, "ctx": "walkie"})
        await state.log_event("stt", ctx="walkie", text=text)
        state.walkie_history.append({"role": "user", "content": text})
        state.walkie_history = state.walkie_history[-(CFG.history_turns * 2):]
        reply = await run_in_threadpool(
            api.chat,
            [{"role": "system", "content": CFG.system_prompt}, *state.walkie_history])
        state.walkie_history.append({"role": "assistant", "content": reply})
        await state.broadcast({"type": "transcript", "role": "agent", "text": reply, "ctx": "walkie"})
        await state.log_event("agent", ctx="walkie", text=reply)
        wav = await run_in_threadpool(tts_synthesize, reply)
        await _send_audio("walkie", reply, wav)
    except Exception as e:
        log.exception("Error en walkie: %s", e)
        await state.broadcast({"type": "walkie_empty"})
    finally:
        await state.broadcast({"type": "walkie_done"})


# ============================================================
# WebSocket del telefono
# ============================================================

@app.websocket("/ws")
async def ws_endpoint(ws: WebSocket):
    await ws.accept()
    reemplazo = state.phone is not None and state.phone is not ws
    await state.replace_phone(ws)
    log.info("Telefono conectado%s.", " (reemplaza a una conexion previa)" if reemplazo else "")
    try:
        await ws.send_json({"type": "welcome", "server": "interfon",
                            "version": __version__, "agent": CFG.agent_name})
        # Entregar mensajes que llegaron sin telefono conectado
        while state.outbox:
            msg, wav = state.outbox.pop(0)
            await ws.send_json(msg)
            await ws.send_bytes(wav)
            await ws.send_json({"type": "agent_audio_end"})

        while True:
            msg = await ws.receive()
            if msg["type"] == "websocket.disconnect":
                break
            text = msg.get("text")
            data = msg.get("bytes")
            if text:
                await _on_json(ws, text)
            elif data:
                await _on_binary(data)
    except WebSocketDisconnect:
        pass
    except Exception as e:
        log.warning("WS error: %s", e)
    finally:
        if state.phone is ws:
            state.phone = None
        state.sockets.discard(ws)
        quedan = state.phone is not None or bool(state.sockets)
        log.info("Telefono desconectado%s.", "" if quedan else " (sin conexiones)")
        if not quedan and state.session and state.session.state not in ("ended", "ringing"):
            await _end_session("conexion perdida")


async def _on_json(ws: WebSocket, raw: str) -> None:
    try:
        msg = json.loads(raw)
    except ValueError:
        return
    mtype = msg.get("type")

    if mtype == "ping":
        await ws.send_json({"type": "pong"})

    elif mtype == "call_outgoing":
        session = state.new_session("call")
        await state.broadcast({"type": "call_started", "call_id": session.id})
        await state.log_event("call_outgoing", call_id=session.id)
        await _speak_and_listen(session)

    elif mtype == "call_answer":
        session = state.session
        if session and session.state == "ringing":
            await state.broadcast({"type": "call_started", "call_id": session.id})
            if session.speak_first:
                try:
                    wav = await run_in_threadpool(tts_synthesize, session.speak_first)
                    session.state = "speaking"
                    await state.broadcast({"type": "call_state", "state": "speaking"})
                    await _send_audio("call", session.speak_first, wav)
                except Exception as e:
                    log.warning("No se pudo hablar la frase inicial: %s", e)
            await _speak_and_listen(session)

    elif mtype == "call_decline":
        await _end_session("rechazada")

    elif mtype == "hangup":
        await _end_session("colgada por el usuario")


async def _on_binary(data: bytes) -> None:
    if data[:4] == b"RIFF":
        # Clip completo de walkie (WAV)
        try:
            parse_wav(data)  # validacion
        except ValueError as e:
            log.warning("WAV invalido del telefono: %s", e)
            return
        asyncio.create_task(_process_walkie(data))
        return
    # PCM16 16k mono en llamada
    session = state.session
    if not session or session.state != "listening":
        return
    det = session.detector
    if det.feed(data):
        pcm = det.take()
        log.info("VAD: frase completa (%.1fs de audio)", len(pcm) / 32000)
        if len(pcm) > 6400:  # >0.2s
            asyncio.create_task(_process_utterance(session, pcm))
