"""Utilidades WAV PCM16 para el intercambio de audio con la app."""
import struct


def pcm_to_wav(pcm: bytes, rate: int = 16000, channels: int = 1, bits: int = 16) -> bytes:
    """Envuelve PCM crudo en un contenedor WAV."""
    byte_rate = rate * channels * bits // 8
    block_align = channels * bits // 8
    data_size = len(pcm)
    header = b"RIFF" + struct.pack("<I", 36 + data_size) + b"WAVE"
    header += b"fmt " + struct.pack("<IHHIIHH", 16, 1, channels, rate, byte_rate, block_align, bits)
    header += b"data" + struct.pack("<I", data_size)
    return header + pcm


def parse_wav(wav: bytes) -> dict:
    """Parsea un WAV PCM. Devuelve {'pcm': bytes, 'rate': int, 'channels': int, 'bits': int}."""
    if len(wav) < 12 or wav[0:4] != b"RIFF" or wav[8:12] != b"WAVE":
        raise ValueError("no es un WAV valido")

    pos = 12
    fmt = {"channels": 1, "rate": 16000, "bits": 16}
    pcm = b""
    while pos + 8 <= len(wav):
        cid = wav[pos:pos + 4]
        (size,) = struct.unpack("<I", wav[pos + 4:pos + 8])
        body = wav[pos + 8:pos + 8 + size]
        if cid == b"fmt " and len(body) >= 16:
            _, ch, rate, _, _, bits = struct.unpack("<HHIIHH", body[:16])
            fmt = {"channels": ch, "rate": rate, "bits": bits}
        elif cid == b"data":
            pcm = body
        pos += 8 + size + (size & 1)  # padding a par
    return {"pcm": pcm, **fmt}
