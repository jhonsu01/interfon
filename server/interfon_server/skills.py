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


# ---------------------------------------------------------------- enrutador

_SKILLS = [
    (re.compile(r"\b(clima|tiempo|hace fr[ií]o|hace calor|llueve|lluvia|llover|"
                r"temperatura|pron[oó]stico)\b"), clima),
    (re.compile(r"\b(noticias?|notas?|titulares?|titular|actualidad|noticiero|"
                r"novedades|[uú]ltima hora|que pas[oó]|que esta pasando|"
                r"que sucedi[oó]|suced[ió] hoy)\b"), noticias),
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
