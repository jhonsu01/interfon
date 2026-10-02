"""Detector de fin de frase por energia (VAD) para audio PCM16 mono 16k.

Sin dependencias: audioop fue eliminado en Python 3.13, asi que el RMS se
calcula a mano. Diseño half-duplex: mientras el agente habla, el servidor
ignora el micro, por lo que aqui solo hay que segmentar la voz del humano.

Estrategia de umbral: absoluto (valido para micros de telefono con AGC) mas
una adaptacion lenta al ruido de fondo medida en frames sin voz.
"""
import math
import time

VOICE_FLOOR = 1200.0   # RMS minimo para considerar voz (16-bit: 0..32767)
VOICE_CEIL = 6000.0


def _rms(frame: bytes) -> float:
    n = len(frame) // 2
    if n == 0:
        return 0.0
    total = 0
    for i in range(0, len(frame) - 1, 2):
        v = frame[i] | (frame[i + 1] << 8)
        if v >= 32768:
            v -= 65536
        total += v * v
    return math.sqrt(total / n)


class UtteranceDetector:
    """Acumula chunks PCM y decide cuando el usuario termino de hablar."""

    def __init__(self, rate: int = 16000, frame_ms: int = 30,
                 silence_ms: int = 720, speech_ms: int = 180, max_s: float = 12.0):
        self.frame_bytes = int(rate * frame_ms / 1000) * 2
        self.silence_needed = max(1, int(silence_ms // frame_ms))
        self.speech_needed = max(1, int(speech_ms // frame_ms))
        self.max_s = max_s

        self._tail = b""
        self._pcm = bytearray()
        self._t0 = time.monotonic()
        self._noise = 300.0
        self._voiced_run = 0
        self._unvoiced_run = 0
        self._speaking = False
        self._speech_frames = 0

    def _threshold(self) -> float:
        return min(VOICE_CEIL, max(VOICE_FLOOR, self._noise * 4.0))

    def feed(self, pcm: bytes) -> bool:
        """Anade audio. Devuelve True cuando la frase esta completa."""
        self._tail += pcm
        self._pcm += pcm
        while len(self._tail) >= self.frame_bytes:
            f = self._tail[:self.frame_bytes]
            self._tail = self._tail[self.frame_bytes:]
            r = _rms(f)
            voiced = r > self._threshold()

            if voiced:
                self._voiced_run += 1
                self._unvoiced_run = 0
            else:
                self._unvoiced_run += 1
                self._voiced_run = 0
                self._noise = self._noise * 0.95 + r * 0.05  # adaptacion lenta

            if not self._speaking and self._voiced_run >= self.speech_needed:
                self._speaking = True
                self._speech_frames = 0

            if self._speaking:
                self._speech_frames += 1

            elapsed = time.monotonic() - self._t0
            if self._speaking and (self._unvoiced_run >= self.silence_needed
                                   or elapsed >= self.max_s):
                return True
            if not self._speaking and elapsed >= 20.0:
                # Silencio largo sin habla: descartar buffer para no crecer.
                self._t0 = time.monotonic()
                self._pcm.clear()
                self._tail = b""
        return False

    def take(self) -> bytes:
        """Entrega el audio acumulado y reinicia el detector."""
        pcm = bytes(self._pcm)
        self.__init__(max_s=self.max_s)
        return pcm
