"""Localised UI labels straight from the app's strings.xml (EN text -> resource name -> ES/DE text)."""
import os, re, xml.etree.ElementTree as ET
RES = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "app", "src", "main", "res"))
_cache = {}
def _load(d):
    if d in _cache: return _cache[d]
    m = {}
    for e in ET.parse(f"{RES}/{d}/strings.xml").getroot().iter("string"):
        m[e.get("name")] = (e.text or "").replace("\\'", "'").replace("\\\"", "\"")
    _cache[d] = m; return m
def T(en_text, locale):
    if locale == "en": return en_text
    en = _load("values"); loc = _load({"es": "values-es", "de": "values-de"}[locale])
    names = [k for k, v in en.items() if v == en_text]
    for n in names:
        if n in loc: return loc[n]
    raise KeyError(f"no string for {en_text!r} in {locale}")
