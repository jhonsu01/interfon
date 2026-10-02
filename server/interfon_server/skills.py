"""Habilidades de internet del agente Interfon (todas sin API key).

- clima(texto): Open-Meteo + geocodificacion (ciudad mencionada o ubicacion por IP)
- noticias(texto): RSS de Google News (general o por tema)
- buscar(texto): Wikipedia ES primero, DuckDuckGo Lite como respaldo

Cada funcion devuelve una respuesta corta en espanol lista para TTS,
o None si no pudo.
"""
import re
import xml.etree.ElementTree as ET

import requests

UA = {"User-Agent": "Interfon/1.0 (telefono interno del agente)"}
_geo_cache: dict = {}

_WCODES = {
    0: "despejado", 1: "mayormente despejado", 2: "parcialmente nublado", 3: "nublado",
    45: "con niebla", 48: "con niebla y escarcha",
    51: "con llovizna ligera", 53: "con llovizna", 55: "con llovizna intensa",
    56: "con llovizna helada", 57: "con llovizna helada intensa",
    61: "con lluvia ligera", 63: "con lluvia", 65: "con lluvia fuerte",
    66: "con aguanieve ligera", 67: "con aguanieve fuerte",
    71: "con nieve ligera", 73: "con nieve", 75: "con nieve fuerte",
    77: "con granos de nieve", 80: "con chubascos ligeros", 81: "con chubascos",
    82: "con chubascos violentos", 85: "con chubascos de nieve", 86: "con chubascos de nieve fuertes",
    95: "con tormenta", 96: "con tormenta y granizo", 99: "con tormenta y granizo fuerte",
}


def _get(url: str, timeout: float = 8.0, **kw):
    r = requests.get(url, timeout=timeout, headers=UA, **kw)
    r.raise_for_status()
    return r


# ---------------------------------------------------------------- clima

def _geo_ip():
    if "ip" not in _geo_cache:
        try:
            d = _get("http://ip-api.com/json/?fields=city,lat,lon").json()
            _geo_cache["ip"] = (d.get("city", ""), d.get("lat"), d.get("lon"))
        except Exception:
            _geo_cache["ip"] = None
    return _geo_cache["ip"]


def _geo_city(city: str):
    key = city.lower()
    if key not in _geo_cache:
        try:
            d = _get("https://geocoding-api.open-meteo.com/v1/search",
                     params={"name": city, "count": 1, "language": "es"}).json()
            g = d.get("results") or []
            _geo_cache[key] = (g[0]["name"], g[0]["latitude"], g[0]["longitude"]) if g else None
        except Exception:
            _geo_cache[key] = None
    return _geo_cache[key]


def clima(texto: str) -> str | None:
    m = re.search(r"(?:en|de|para)\s+([a-záéíóúñü ]{3,40}?)(?:$|[?!.])", texto)
    lugar = m.group(1).strip() if m else None
    if lugar:
        geo = _geo_city(lugar)
    else:
        geo, lugar = _geo_ip(), None
    if not geo or geo[1] is None:
        return "No pude ubicar la ciudad para el clima."
    nombre, lat, lon = geo
    d = _get("https://api.open-meteo.com/v1/forecast", params={
        "latitude": lat, "longitude": lon,
        "current": "temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m",
        "daily": "temperature_2m_max,temperature_2m_min,precipitation_probability_max",
        "forecast_days": 1, "timezone": "auto",
    }).json()
    c, dy = d["current"], d["daily"]
    desc = _WCODES.get(c.get("weather_code"), "")
    partes = [f"En {nombre} hay {c['temperature_2m']:.0f} grados {desc}",
              f"humedad {c['relative_humidity_2m']:.0f} por ciento",
              f"viento {c['wind_speed_10m']:.0f} kilometros por hora",
              f"maxima {dy['temperature_2m_max'][0]:.0f} y minima "
              f"{dy['temperature_2m_min'][0]:.0f}",
              f"probabilidad de lluvia {dy['precipitation_probability_max'][0]:.0f} por ciento"]
    return ", ".join(partes) + "."


# ---------------------------------------------------------------- noticias

def noticias(texto: str) -> str | None:
    m = re.search(r"(?:de|sobre)\s+([a-z0-9áéíóúñü ]{3,40}?)(?:$|[?!.])", texto)
    tema = m.group(1).strip() if m else None
    url, params = "https://news.google.com/rss", {"hl": "es", "gl": "CO", "ceid": "CO:es"}
    if tema:
        url += "/search"
        params["q"] = tema
    root = ET.fromstring(_get(url, params=params).content)
    titulares = []
    for it in root.iter("item"):
        t = (it.findtext("title") or "").strip()
        t = re.sub(r"\s+-\s+[^-]+$", "", t)  # quitar fuente final
        if t:
            titulares.append(t[:110])
        if len(titulares) >= 3:
            break
    if not titulares:
        return "No encontre noticias ahora mismo."
    pref = f"sobre {tema}" if tema else "de ahora"
    return f"Titulares {pref}. " + ". ".join(f"Numero {i+1}: {t}" for i, t in enumerate(titulares)) + "."


# ---------------------------------------------------------------- busqueda web

def _limpiar_query(q: str) -> str:
    q = re.sub(r"^(buscame|búscame|busca|buscar|busca en internet|busca en la web|"
               r"busca en wikipedia|qu[eé] es|qu[eé] son|qui[eé]n es|qui[eé]n fue|"
               r"qu[eé] significa|define|dime)\s+", "", q.strip())
    return re.sub(r"[?!.]+$", "", q).strip()


def buscar(texto: str) -> str | None:
    q = _limpiar_query(texto)
    if len(q) < 2:
        return None
    # 1) Wikipedia en espanol (fuente estable)
    try:
        d = _get("https://es.wikipedia.org/w/api.php", params={
            "action": "query", "list": "search", "srsearch": q,
            "format": "json", "srlimit": 1}).json()
        hits = d.get("query", {}).get("search") or []
        if hits:
            titulo = hits[0]["title"]
            s = _get("https://es.wikipedia.org/api/rest_v1/page/summary/"
                     + titulo.replace(" ", "_")).json().get("extract", "")
            if s:
                return f"Segun Wikipedia: {s[:300]}"
    except Exception:
        pass
    # 2) DuckDuckGo Lite (mejor esfuerzo)
    try:
        html = _get("https://lite.duckduckgo.com/lite/", params={"q": q}).text
        frags = re.findall(r'class="result-snippet"[^>]*>(.*?)</a>', html, re.S) or \
            re.findall(r"<td[^>]*>\s*([^<]{50,}?)\s*</td>", html)
        if frags:
            limpio = re.sub(r"<[^>]+>", "", frags[0]).strip()
            limpio = re.sub(r"\s+", " ", limpio)
            if len(limpio) > 20:
                return f"Encontre en internet: {limpio[:300]}"
    except Exception:
        pass
    return None


# ---------------------------------------------------------------- festivos CO

from datetime import date, timedelta

_DIAS_ES = ["lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"]
_MESES_ES = ["enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto",
             "septiembre", "octubre", "noviembre", "diciembre"]


def _domingo_pascua(year: int) -> date:
    a = year % 19
    b, c = divmod(year, 100)
    d, e = divmod(b, 4)
    f = (b + 8) // 25
    g = (b - f + 1) // 3
    h = (19 * a + b - d - g + 15) % 30
    i, k = divmod(c, 4)
    l = (32 + 2 * e + 2 * i - h - k) % 7
    m = (a + 11 * h + 22 * l) // 451
    mes, dia = divmod(h + l - 7 * m + 114, 31)
    return date(year, mes, dia + 1)


def _lunes_siguiente(d: date) -> date:
    return d + timedelta(days=(0 - d.weekday()) % 7)


def _festivos_colombia(year: int) -> list:
    """Festivos oficiales de Colombia (Ley 51 de 1983, Ley Emiliani)."""
    p = _domingo_pascua(year)
    fijos = [
        (date(year, 1, 1), "Año Nuevo"),
        (p - timedelta(days=3), "Jueves Santo"),
        (p - timedelta(days=2), "Viernes Santo"),
        (date(year, 5, 1), "Día del Trabajo"),
        (date(year, 7, 20), "Día de la Independencia"),
        (date(year, 8, 7), "Batalla de Boyacá"),
        (date(year, 12, 8), "Inmaculada Concepción"),
        (date(year, 12, 25), "Navidad"),
    ]
    trasladables = [
        (date(year, 1, 6), "Reyes Magos"),
        (date(year, 3, 19), "San José"),
        (date(year, 6, 29), "San Pedro y San Pablo"),
        (date(year, 8, 15), "Asunción de la Virgen"),
        (date(year, 10, 12), "Día de la Raza"),
        (date(year, 11, 1), "Todos los Santos"),
        (date(year, 11, 11), "Independencia de Cartagena"),
        (p + timedelta(days=43), "Ascención del Señor"),
        (p + timedelta(days=64), "Corpus Christi"),
        (p + timedelta(days=71), "Sagrado Corazón de Jesús"),
    ]
    out = fijos + [(_lunes_siguiente(d) if d.weekday() != 0 else d, n)
                   for d, n in trasladables]
    return sorted(out)


def festivos(texto: str) -> str | None:
    hoy = date.today()
    meses = "|".join(_MESES_ES)
    m_mes = re.search(rf"\b({meses})\b", texto)
    m_year = re.search(r"\b(20\d\d)\b", texto)
    proximo = bool(re.search(r"pr[oó]xim|siguiente|viene", texto))
    year = int(m_year.group(1)) if m_year else hoy.year

    if proximo or (not m_mes and not m_year and "cuales" not in texto and "hay" not in texto):
        todos = [d for d in _festivos_colombia(year) + _festivos_colombia(year + 1)
                 if d[0] >= hoy]
        if not todos:
            return None
        d, n = todos[0]
        return (f"El próximo festivo es {n}: {_DIAS_ES[d.weekday()]} "
                f"{d.day} de {_MESES_ES[d.month - 1]} de {d.year}.")

    lista = _festivos_colombia(year)
    if m_mes:
        mes = _MESES_ES.index(m_mes.group(1)) + 1
        lista = [x for x in lista if x[0].month == mes]
        if not lista:
            return f"En {m_mes.group(1)} de {year} no hay días festivos oficiales en Colombia."
    else:
        lista = lista[:5] if len(lista) > 6 else lista
    partes = [f"{n}, {_DIAS_ES[d.weekday()]} {d.day} de {_MESES_ES[d.month - 1]}" for d, n in lista]
    encabezado = f"en {m_mes.group(1)} de {year}" if m_mes else f"de {year}"
    return f"Festivos en Colombia {encabezado}: " + "; ".join(partes) + "."


# ---------------------------------------------------------------- enrutador

_SKILLS = [
    (re.compile(r"\b(clima|tiempo|hace fr[ií]o|hace calor|llueve|lluvia|llover|"
                r"temperatura|pron[oó]stico)\b"), clima),
    (re.compile(r"\b(noticias?|notas?|titulares?|titular|actualidad|noticiero|"
                r"novedades|[uú]ltima hora|que pas[oó]|que esta pasando|"
                r"que sucedi[oó]|suced[ió] hoy)\b"), noticias),
    (re.compile(r"\b(festivos?|feriados?|puente|feriado nacional)\b"), festivos),
    (re.compile(r"^(buscame|b[uú]scame|busca|buscar)\b|"
                r"\b(qu[eé] es|qui[eé]n es|qui[eé]n fue|qu[eé] significa)\b"), buscar),
]


def try_skill(texto: str) -> str | None:
    """Devuelve la respuesta de la primera habilidad que aplique, o None."""
    t = texto.lower().strip()
    for rx, fn in _SKILLS:
        if rx.search(t):
            try:
                return fn(t)
            except Exception:
                return None
    return None
