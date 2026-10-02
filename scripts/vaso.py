"""Telefono de vasos: dos servidores Interfon conversan entre si y el telefono lo escucha.

Cada turno, el agente de un servidor recibe la charla hasta ese momento (via
/api/ask, piensa con SU propio LLM) y su respuesta suena en el telefono con SU
voz (/api/message). Un servidor no ve nada que el otro no le haya dicho.

Uso (desde la raiz del proyecto):
  python scripts/vaso.py "mensaje inicial" \\
      --a http://192.168.1.50:8765 --nombre-a "el punto cincuenta" \\
      --b http://192.168.1.8:8765  --nombre-b "el PC de desarrollo" \\
      --rondas 3

El mensaje inicial lo recibe A; luego A y B se alternan (una ronda = un turno de cada uno).
"""
import argparse
import json
import re
import sys
import time
import urllib.request

CHARS_PER_S = 13.0      # ritmo aproximado de la voz, para no encimar los audios
HISTORIAL_MAX = 6       # turnos previos que ve cada agente (el LLM es pequeno)

# Palabras que disparan habilidades del servidor (clima, noticias, Wikipedia,
# fecha/hora) y harian que responda la habilidad en vez del LLM. En el historial
# se cambian por sinonimos; las instrucciones del juego ya las evitan.
SINONIMOS = [
    (r"\btiempo\b", "rato"), (r"\bclima\b", "ambiente"), (r"\bnotas?\b", "apuntes"),
    (r"\bnoticias?\b", "novedad"), (r"\btitulares?\b", "anuncios"),
    (r"\bactualidad\b", "presente"), (r"\bnovedades\b", "cosas nuevas"),
    (r"\b[uú]ltima hora\b", "lo reciente"), (r"\bqu[eé] pas[oó]\b", "cómo fue"),
    (r"\bqu[eé] es\b", "cuál es"), (r"\bqui[eé]n (es|fue)\b", "cuál es"),
    (r"\bqu[eé] significa\b", "cuál es el sentido de"), (r"\bd[ií]as?\b", "jornada"),
    (r"\bfecha\b", "momento"), (r"\bhora\b", "momento"), (r"\bhoy\b", "ahora"),
    (r"\b(llueve|lluvia|llover)\b", "agua"), (r"\bbusca(r|me)?\b", "encuentra"),
    (r"\b(festivos?|feriados?|puente)\b", "descanso"), (r"\btemperatura\b", "calor"),
    (r"\bpron[oó]stico\b", "previsión"), (r"\bhace (fr[ií]o|calor)\b", "está fresco"),
]


def call(base, path, body=None, timeout=300):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(base + path, data=data,
                                 headers={"Content-Type": "application/json; charset=utf-8"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read().decode("utf-8"))


def desarmar(texto: str) -> str:
    for rx, sub in SINONIMOS:
        texto = re.sub(rx, sub, texto, flags=re.I)
    return texto


def armar_prompt(yo: str, el: str, historial: list) -> str:
    """historial: [(hablante, texto)] con hablante in {"claude", yo, el}."""
    lineas = []
    for quien, texto in historial[-HISTORIAL_MAX:]:
        etiqueta = {"claude": "Claude", yo: "Tú", el: el.capitalize()}[quien]
        lineas.append(f"- {etiqueta}: «{desarmar(texto)}»")
    return (f"Estamos jugando al teléfono de vasos entre dos agentes. Tú eres {yo} y "
            f"conversas con {el}. Esta es la charla hasta ahora:\n" + "\n".join(lineas) +
            f"\nAhora te toca a ti. Respóndele directamente a {el} en una o dos frases "
            f"cortas y habladas, sin markdown. Aporta algo nuevo (una idea, un detalle o "
            f"una pregunta) y no repitas lo que ya se dijo.")


def pensar(base: str, prompt: str) -> str:
    for _ in range(2):
        r = call(base, "/api/ask", {"text": prompt})
        if not r.get("grounded"):
            return r["reply"].strip()
        # Una habilidad se colo igual: desarmar tambien las instrucciones y reintentar
        print("   (respondio una habilidad del servidor; reintento)", flush=True)
        prompt = desarmar(prompt)
    return r["reply"].strip()


def decir(base: str, nombre: str, texto: str):
    while call(base, "/api/status", timeout=5)["session"]["active"]:
        time.sleep(3)   # half-duplex: no hablar encima de una llamada en curso
    hablado = f"Habla {nombre}: {texto}"
    d = call(base, "/api/message", {"text": hablado})
    dur = len(hablado) / CHARS_PER_S
    print(f"   [sonando en el teléfono, delivered={d['delivered']}, ~{dur:.0f}s]", flush=True)
    time.sleep(dur + 2)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("mensaje", help="primer mensaje (lo recibe el servidor A)")
    ap.add_argument("--a", required=True, help="URL del servidor A")
    ap.add_argument("--b", required=True, help="URL del servidor B")
    ap.add_argument("--nombre-a", default="el agente A")
    ap.add_argument("--nombre-b", default="el agente B")
    ap.add_argument("--rondas", type=int, default=3, help="turnos de cada agente")
    args = ap.parse_args()

    agentes = {args.nombre_a: args.a.rstrip("/"), args.nombre_b: args.b.rstrip("/")}
    for nombre, base in agentes.items():
        st = call(base, "/api/status", timeout=5)
        print(f"{nombre}: teléfono={st['phone_connected']} llm={st['llm']['loaded']}")
        if not st["phone_connected"]:
            print("  AVISO: sin teléfono conectado; sus audios quedarán en cola")

    historial = [("claude", args.mensaje)]
    print(f"\nClaude -> {args.nombre_a}: {args.mensaje}\n", flush=True)
    yo, el = args.nombre_a, args.nombre_b
    for _ in range(args.rondas * 2):
        texto = pensar(agentes[yo], armar_prompt(yo, el, historial))
        historial.append((yo, texto))
        print(f"{yo} -> {el}: {texto}", flush=True)
        decir(agentes[yo], yo, texto)
        yo, el = el, yo
    print("\nFIN del teléfono de vasos")
    return 0


if __name__ == "__main__":
    sys.exit(main())
