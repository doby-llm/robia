#!/usr/bin/env python3
"""Builds the garment-review redesign mockups (HTML) from one place.

    python3 build.py                 # writes index.html + finalist pages next to this file
    python3 build.py --accent indigo # same pages with the pending indigo accent instead of Earth & Steel
    python3 build.py --shots         # also screenshots every page with headless chromium (PNG beside the HTML)

Tokens come from app/.../core/designsystem/Color.kt (Earth & Steel). The app has no dark scheme yet, so the dark
tokens below are a derived proposal (tonal inversion of the same palette), marked as such on every page.
Every visible string goes through t(); the table under each phone lists exactly the keys that phone used.
"""
import argparse
import html
import re
import shutil
import subprocess
import sys
from pathlib import Path

OUT = Path(__file__).resolve().parent

# ---------------------------------------------------------------- tokens
LIGHT = {
    "page": "#e9e6e3", "surface": "#f9f9f9", "lowest": "#ffffff", "low": "#f3f3f4", "cont": "#efe5d9",
    "high": "#e8e8e8", "highest": "#e2e2e2", "on": "#1a1c1c", "onv": "#4f4442", "outline": "#817472",
    "ov": "#d2c3c0", "line": "#ebe4e1", "primary": "#332421", "onprimary": "#ffffff", "secc": "#f1dcc9",
    "onsecc": "#332421", "terra": "#a56639", "success": "#2e6b47", "successc": "#dcefe1", "onsuccessc": "#17432a",
    "warn": "#8a5300", "warnc": "#fbe6c4", "onwarnc": "#5a3500", "inv": "#2f3131", "oninv": "#f0f1f1",
    "invprimary": "#dac1bc", "well": "#f3f3f4", "scrim": "rgba(26,28,28,.42)", "shadow": "rgba(51,36,33,.10)",
}
DARK = {  # derived proposal: the app ships light only (inv-r3-c.md), see header note
    "page": "#e9e6e3", "surface": "#171312", "lowest": "#211c1a", "low": "#1d1817", "cont": "#2b2421",
    "high": "#2e2826", "highest": "#3a3330", "on": "#ede0dc", "onv": "#cdbfbb", "outline": "#9c8d8a",
    "ov": "#544845", "line": "#342c29", "primary": "#ead7d1", "onprimary": "#2a1c19", "secc": "#4a3c31",
    "onsecc": "#f4dfcc", "terra": "#ffb786", "success": "#8fd3a5", "successc": "#1e3b2a", "onsuccessc": "#bdeccb",
    "warn": "#f3c27a", "warnc": "#45320f", "onwarnc": "#ffdcae", "inv": "#ede0dc", "oninv": "#2a1c19",
    "invprimary": "#6d5a56", "well": "#2e2826", "scrim": "rgba(0,0,0,.55)", "shadow": "rgba(0,0,0,.35)",
}
ACCENTS = {  # only the accent family changes; neutrals and semantic states stay
    "earth": ({}, {}),
    "indigo": ({"primary": "#3f4a8a", "secc": "#dfe1f9", "onsecc": "#232a5c", "invprimary": "#bac3ff"},
               {"primary": "#bac3ff", "onprimary": "#1d2560", "secc": "#373d5e", "onsecc": "#dfe1f9", "invprimary": "#3f4a8a"}),
}

# ---------------------------------------------------------------- strings (key -> EN, ES, DE)
S = {
    "review_color_title": ("Review colours", "Revisar colores", "Farben prüfen"),
    "review_tag_title": ("Review garments", "Revisar prendas", "Kleidungsstücke prüfen"),
    "review_color_context": ("Palette updated", "Paleta actualizada", "Palette aktualisiert"),
    "review_color_found": ("%1$d garments could use a new colour", "%1$d prendas podrían cambiar de color",
                           "%1$d Kleidungsstücke könnten eine neue Farbe bekommen"),
    "review_tag_context": ("New occasion: %1$s", "Nueva ocasión: %1$s", "Neuer Anlass: %1$s"),
    "review_tag_found": ("%1$d garments might fit", "%1$d prendas podrían encajar", "%1$d Kleidungsstücke könnten passen"),
    "review_checklist_hint": ("Untick anything you’d rather keep.", "Desmarca lo que prefieras dejar igual.",
                              "Entferne das Häkchen bei allem, was bleiben soll."),
    "review_section_choose": ("Needs your choice", "Elige tú", "Deine Auswahl nötig"),
    "review_section_suggested": ("Suggested", "Sugeridos", "Vorgeschlagen"),
    "review_was": ("was", "antes", "vorher"),
    "review_role_main": ("Main", "Principal", "Haupt"),
    "review_role_second": ("Second", "Secundario", "Zweit"),
    "review_badge_edited": ("Edited", "Editado", "Geändert"),
    "review_badge_kept": ("Kept", "Igual", "Bleibt"),
    "review_choose_color": ("Choose a colour", "Elige un color", "Farbe wählen"),
    "review_color_removed": ("%1$s was removed", "%1$s se eliminó", "%1$s wurde entfernt"),
    "review_apply_n": ("Apply %1$d changes", "Aplicar %1$d cambios", "%1$d Änderungen übernehmen"),
    "review_kept_n": ("%1$d kept as is", "%1$d se quedan igual", "%1$d bleiben unverändert"),
    "review_unchosen_n": ("%1$d without a choice stays as is", "%1$d sin elegir se queda igual",
                          "%1$d ohne Auswahl bleibt unverändert"),
    "review_scanning": ("Checking %1$d of %2$d…", "Revisando %1$d de %2$d…", "Prüfe %1$d von %2$d…"),
    "review_found_so_far": ("%1$d found", "%1$d encontradas", "%1$d gefunden"),
    "review_wait_button": ("Checking garments…", "Revisando prendas…", "Kleidungsstücke werden geprüft…"),
    "review_picker_title": ("Main colour · %1$s", "Color principal · %1$s", "Hauptfarbe · %1$s"),
    "review_picker_keep": ("Keep current", "Dejar como está", "Beibehalten"),
    "review_picker_hint": ("Tap a colour to use it.", "Toca un color para usarlo.", "Tippe auf eine Farbe, um sie zu verwenden."),
    "review_suggested": ("Suggested", "Sugerido", "Vorschlag"),
    "review_your_choice": ("Your choice", "Tu elección", "Deine Wahl"),
    "review_now": ("Now", "Ahora", "Jetzt"),
    "review_robia_suggested": ("Robia suggested %1$s", "Robia sugirió %1$s", "Robia schlug %1$s vor"),
    "review_use_suggestion": ("Use suggestion", "Usar sugerencia", "Vorschlag übernehmen"),
    "review_use_choice": ("Use my choice", "Usar mi elección", "Meine Wahl übernehmen"),
    "review_keep_current": ("Keep current", "Dejar como está", "Beibehalten"),
    "review_change": ("Change…", "Cambiar…", "Ändern…"),
    "review_accept_all": ("Accept all", "Aceptar todo", "Alle übernehmen"),
    "review_accept_all_n": ("Accept all (%1$d)", "Aceptar todo (%1$d)", "Alle übernehmen (%1$d)"),
    "review_progress_of": ("%1$d of %2$d", "%1$d de %2$d", "%1$d von %2$d"),
    "review_ready_n": ("%1$d ready", "%1$d listas", "%1$d bereit"),
    "review_snack_applied": ("Applied to %1$s", "Aplicado a %1$s", "Übernommen: %1$s"),
    "undo": ("Undo", "Deshacer", "Rückgängig"),
    "review_accept": ("Accept", "Aceptar", "Übernehmen"),
    "review_accepted_row": ("Accepted", "Aceptado", "Übernommen"),
    "review_main_colour": ("Main colour", "Color principal", "Hauptfarbe"),
    "review_occasions": ("Occasions", "Ocasiones", "Anlässe"),
    "review_none": ("None", "Ninguna", "Keine"),
    "review_no_color": ("No colour", "Sin color", "Keine Farbe"),
    "review_complete_colors": ("Colours updated on %1$d garments", "Colores actualizados en %1$d prendas",
                               "Farben bei %1$d Kleidungsstücken aktualisiert"),
    "review_complete_tags": ("Tags updated on %1$d garments", "Etiquetas actualizadas en %1$d prendas",
                             "Tags bei %1$d Kleidungsstücken aktualisiert"),
    "review_complete_kept": ("%1$d kept as they were", "%1$d se quedaron igual", "%1$d blieben unverändert"),
    "review_complete_edit": ("Edit changes", "Editar cambios", "Änderungen bearbeiten"),
    "done": ("Done", "Listo", "Fertig"),
    "edit_photo_action": ("Edit photo", "Editar foto", "Foto bearbeiten"),
    "gallery": ("Gallery", "Galería", "Galerie"),
    "add_title": ("Add garment", "Añadir prenda", "Kleidungsstück hinzufügen"),
    "photo_ready": ("Photo ready", "Foto lista", "Foto bereit"),
    "review_current_hdr": ("Garment scan", "Escaneo de prendas", "Kleidungs-Scan"),
    "review_discard": ("Discard", "Descartar", "Verwerfen"),
    "review_keep_option": ("Current", "Actual", "Aktuell"),
}
LANGS = ("en", "es", "de")
LANG = "en"
USED = []


def t(key, *args):
    if key not in USED:
        USED.append(key)
    tpl = S[key][LANGS.index(LANG)]
    return re.sub(r"%(\d)\$[sd]", lambda m: str(args[int(m.group(1)) - 1]), tpl)


def e(text):
    return html.escape(str(text))


# ---------------------------------------------------------------- user data (palette colours, garments, tags)
COLORS = {  # name -> hex, EN, ES, DE (default-palette names; users can rename them)
    "black": ("#1f1f1f", "Black", "Negro", "Schwarz"),
    "charcoal": ("#45484c", "Charcoal", "Antracita", "Anthrazit"),
    "white": ("#f5f3ee", "White", "Blanco", "Weiß"),
    "cream": ("#efe4cc", "Cream", "Crema", "Creme"),
    "navy": ("#24304d", "Navy", "Azul marino", "Marineblau"),
    "denim": ("#4b6b93", "Denim blue", "Azul vaquero", "Jeansblau"),
    "bgreen": ("#3d8b82", "Bluish green", "Verde azulado", "Bläuliches Grün"),
    "lybrown": ("#c8a36d", "Light yellowish brown", "Marrón amarillento claro", "Helles gelbliches Braun"),
    "dgblue": ("#4f5a6e", "Dark greyish blue", "Azul grisáceo oscuro", "Dunkles gräuliches Blau"),
    "beige": ("#d9c7a7", "Beige", "Beige", "Beige"),
    "camel": ("#b98a55", "Camel", "Camel", "Camel"),
    "coral": ("#f07a63", "Coral", "Coral", "Koralle"),
    "olive": ("#6b6b3a", "Olive", "Oliva", "Oliv"),
    "burgundy": ("#6e2233", "Burgundy", "Burdeos", "Bordeaux"),
    "lgrey": ("#cfd0d0", "Light grey", "Gris claro", "Hellgrau"),
    "red": ("#b3302d", "Red", "Rojo", "Rot"),
    "green": ("#3f7a4a", "Green", "Verde", "Grün"),
    "pink": ("#e6a5b5", "Pink", "Rosa", "Rosa"),
}
GARMENTS = {  # id -> kind, colour, EN, ES, DE
    "coat": ("coat", "black", "Wool coat", "Abrigo de lana", "Wollmantel"),
    "shirt": ("tee", "white", "Linen shirt", "Camisa de lino", "Leinenhemd"),
    "chinos": ("pants", "bgreen", "Chinos", "Chinos", "Chinohose"),
    "jumper": ("knit", "navy", "Knit jumper", "Jersey de punto", "Strickpullover"),
    "dress": ("dress", "coral", "Summer dress", "Vestido de verano", "Sommerkleid"),
    "jeans": ("pants", "navy", "Straight jeans", "Vaqueros rectos", "Gerade Jeans"),
    "trench": ("coat", "beige", "Trench coat", "Gabardina", "Trenchcoat"),
    "longcoat": ("coat", "dgblue", "Oversized double-breasted wool coat", "Abrigo cruzado oversize de lana",
                 "Zweireihiger Oversize-Wollmantel"),
    "tee": ("tee", "olive", "Striped tee", "Camiseta de rayas", "Ringelshirt"),
    "skirt": ("dress", "burgundy", "Pleated skirt", "Falda plisada", "Faltenrock"),
}
TAGS = {
    "work": ("Work", "Trabajo", "Arbeit"), "casual": ("Casual", "Informal", "Freizeit"),
    "party": ("Party", "Fiesta", "Party"), "formal": ("Formal", "Formal", "Formell"),
    "weekend": ("Weekend away", "Escapada de fin de semana", "Wochenendausflug"),
}


def cname(c):
    return COLORS[c][1 + LANGS.index(LANG)]


def gname(g):
    return GARMENTS[g][2 + LANGS.index(LANG)]


def tname(tag):
    return TAGS[tag][LANGS.index(LANG)]


def mix(a, b, w):
    a, b = a.lstrip("#"), b.lstrip("#")
    ca = [int(a[i:i + 2], 16) for i in (0, 2, 4)]
    cb = [int(b[i:i + 2], 16) for i in (0, 2, 4)]
    return "#" + "".join(f"{round(x * (1 - w) + y * w):02x}" for x, y in zip(ca, cb))


def lum(hexv):
    h = hexv.lstrip("#")
    r, g, b = (int(h[i:i + 2], 16) / 255 for i in (0, 2, 4))
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


SHAPES = {
    "tee": "M30 18 L42 12 Q50 19 58 12 L70 18 L87 33 L77 43 L70 38 L70 88 L30 88 L30 38 L23 43 L13 33 Z",
    "knit": "M31 15 L42 10 Q50 16 58 10 L69 15 L88 70 L78 74 L70 44 L70 90 L30 90 L30 44 L22 74 L12 70 Z",
    "coat": "M33 8 L44 5 L50 14 L56 5 L67 8 L82 24 L85 92 L71 92 L69 42 L69 95 L31 95 L31 42 L29 92 L15 92 L18 24 Z",
    "pants": "M31 7 L69 7 L74 93 L56 93 L50 35 L44 93 L26 93 Z",
    "dress": "M40 7 L60 7 L58 28 L77 93 L23 93 L42 28 Z",
}


def garment_art(g, color=None):
    kind, col = GARMENTS[g][0], color or GARMENTS[g][1]
    hx = COLORS[col][0]
    if lum(hx) > 0.82:  # REQ-BR3-02: near-white garments sit on the warm neutral backdrop
        top, bot = "#e6e0d9", "#cfc5ba"
    else:
        top, bot = mix(hx, "#ffffff", 0.82), mix(hx, "#ffffff", 0.62)
    extra = '<path d="M50 14 L50 95" stroke="rgba(0,0,0,.18)" stroke-width="1.2"/>' if kind == "coat" else ""
    if g == "tee":
        extra = "".join(f'<rect x="30" y="{y}" width="40" height="4" fill="rgba(255,255,255,.55)"/>' for y in (46, 58, 70, 82))
    return (f'<div class="art" style="background:linear-gradient(180deg,{top},{bot})">'
            f'<svg viewBox="0 0 100 100" aria-hidden="true"><path d="{SHAPES[kind]}" fill="{hx}" '
            f'stroke="rgba(0,0,0,.16)" stroke-width="1"/>{extra}</svg></div>')


def sw(c, size=16, cls=""):
    hx = COLORS[c][0] if c else "transparent"
    border = "color-mix(in srgb,var(--on) 24%,transparent)"
    inner = "" if c else "×"
    return (f'<i class="sw {cls}" style="width:{size}px;height:{size}px;background:{hx};'
            f'border-color:{border}">{inner}</i>')


# ---------------------------------------------------------------- CSS
CSS = r"""
*{box-sizing:border-box}
body{margin:0;background:#e9e6e3;color:#1a1c1c;font:14px/20px Inter,Roboto,"Liberation Sans",Arial,sans-serif}
a{color:#6d390f}
.page{max-width:1640px;margin:0 auto;padding:24px 32px 48px}
.page>h1{margin:0 0 4px;font-size:26px;line-height:32px;color:#332421}
.lead{max-width:1080px;color:#4f4442;margin:4px 0 12px}
.note{max-width:1080px;background:#fff9;border-radius:12px;padding:12px 16px;margin:12px 0;color:#4f4442;font-size:13.5px}
.note b{color:#332421}
h2.sec{margin:36px 0 4px;font-size:20px;color:#332421}
p.secd{margin:0 0 14px;color:#4f4442;max-width:1080px}
.pairs{display:flex;flex-wrap:wrap;gap:36px 40px;align-items:flex-start}
.pair{display:flex;flex-direction:column;gap:12px;width:744px}
.pair .phones{display:flex;gap:24px}
.pair .cap{background:#fff;border-radius:12px;padding:12px 16px;font-size:13px;line-height:19px;color:#4f4442}
.pair .cap b.t{display:block;color:#332421;font-size:14.5px;margin-bottom:4px}
.pair .cap ul{margin:6px 0 0;padding-left:18px}.pair .cap li{margin:2px 0}
.lbl{font:600 11px/16px Inter,Roboto,"Liberation Sans",Arial,sans-serif;letter-spacing:.5px;text-transform:uppercase;color:#817472;margin-bottom:-4px}
table.str{border-collapse:collapse;width:100%;font-size:12px;line-height:16px;background:#fff;border-radius:12px;overflow:hidden}
table.str th{background:#efe5d9;text-align:left;padding:6px 8px;color:#332421}
table.str td{padding:5px 8px;border-top:1px solid #f0e9e6;vertical-align:top}
table.str td:first-child{font-family:"DejaVu Sans Mono",monospace;font-size:11px;color:#6d390f;white-space:nowrap}
table.cmp{border-collapse:collapse;background:#fff;border-radius:12px;overflow:hidden;font-size:13px;max-width:1300px}
table.cmp th,table.cmp td{padding:8px 12px;border-top:1px solid #f0e9e6;text-align:left;vertical-align:top}
table.cmp th{background:#efe5d9;color:#332421}
.rate{display:inline-block;min-width:44px;text-align:center;font-size:11px;font-weight:700;border-radius:999px;padding:0 6px;margin-right:4px}
.good{background:#dcefe1;color:#17432a}.mid{background:#fbe6c4;color:#5a3500}.bad{background:#ffdad6;color:#93000a}
.reco{background:#332421;color:#fff;border-radius:16px;padding:16px 20px;max-width:1080px;margin:16px 0}
.reco b{color:#f1dcc9}.reco p{margin:6px 0}
.sketches{display:flex;flex-wrap:wrap;gap:24px;align-items:flex-start}
.sketch{width:340px;background:#fff;border-radius:16px;padding:14px}
.sketch .mini{width:216px;height:444px;margin:0 auto 10px;overflow:hidden;border-radius:18px}
.sketch .mini .phone{transform:scale(.6);transform-origin:0 0}
.sketch h3{margin:0 0 4px;font-size:15px;color:#332421}.sketch p{margin:4px 0;font-size:12.5px;line-height:18px;color:#4f4442}
.verdict{display:inline-block;font:600 11px/18px sans-serif;padding:0 8px;border-radius:999px;margin-bottom:4px}
.verdict.fin{background:#dcefe1;color:#17432a}.verdict.drop{background:#ffdad6;color:#93000a}.verdict.lang{background:#f1dcc9;color:#332421}
.side{display:flex;flex-wrap:wrap;gap:28px;align-items:flex-start}
.side .col{width:360px}.side .col h3{margin:0 0 8px;font-size:15px;color:#332421}.side .col h3 small{display:block;font-weight:400;color:#4f4442}
.diag{display:flex;gap:32px;flex-wrap:wrap;align-items:flex-start}
.diag ol{max-width:620px;margin:0;padding-left:22px;color:#1a1c1c}.diag li{margin:0 0 8px}
.diag li b{color:#332421}

/* ---------------- phone */
.phone{--fs:Inter,Roboto,"Liberation Sans",Arial,sans-serif;width:360px;height:740px;flex:none;border-radius:28px;overflow:hidden;
  position:relative;display:flex;flex-direction:column;background:var(--surface);color:var(--on);font:14px/20px var(--fs);
  box-shadow:0 8px 28px rgba(51,36,33,.20)}
.phone *{font-family:var(--fs)}
.sb{height:24px;flex:none;display:flex;justify-content:space-between;align-items:center;padding:0 20px;font-size:11px;color:var(--onv)}
.ab{height:56px;flex:none;display:flex;align-items:center;padding:0 4px;gap:0}
.ab h1{flex:1;min-width:0;margin:0 4px;font:600 20px/28px var(--fs);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;color:var(--on)}
.ib{width:48px;height:48px;flex:none;display:flex;align-items:center;justify-content:center;color:var(--on);font-size:20px}
.abact{flex:none;height:40px;padding:0 12px;margin-right:4px;border-radius:999px;display:flex;align-items:center;font:600 14px/20px var(--fs);color:var(--primary);white-space:nowrap}
.scroll{flex:1;min-height:0;overflow:hidden;position:relative}
.hd{padding:4px 20px 12px}
.hd .ctx{font:600 12px/16px var(--fs);letter-spacing:.5px;text-transform:uppercase;color:var(--onv)}
.hd h2{margin:4px 0 2px;font:600 18px/24px var(--fs);color:var(--on)}
.hd p{margin:0;color:var(--onv);font-size:14px;line-height:20px}
.sech{padding:12px 20px 8px;font:600 12px/16px var(--fs);letter-spacing:.5px;text-transform:uppercase;color:var(--onv);display:flex;justify-content:space-between}
.list{margin:0 16px;background:var(--lowest);border:1px solid var(--line);border-radius:16px;overflow:hidden}
.row{display:grid;grid-template-columns:56px minmax(0,1fr) 40px;gap:12px;align-items:center;padding:10px 6px 10px 10px;border-top:1px solid var(--line);min-height:84px}
.row:first-child{border-top:0}
.thumb{width:56px;height:64px;border-radius:12px;overflow:hidden;flex:none}
.art{width:100%;height:100%;display:flex;align-items:center;justify-content:center}
.art svg{width:78%;height:78%}
.nm{display:flex;align-items:center;gap:6px;min-width:0}
.nm b{font:600 14px/20px var(--fs);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;min-width:0}
.val{display:flex;gap:8px;align-items:flex-start;margin-top:2px;font:500 14px/20px var(--fs);color:var(--on)}
.val .sw{margin-top:2px}
.clamp{display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;min-width:0}
.was{display:flex;gap:6px;align-items:center;font-size:12px;line-height:16px;color:var(--onv);margin-top:2px;min-width:0}
.was span{white-space:nowrap;overflow:hidden;text-overflow:ellipsis;min-width:0}
.was .sw{flex:none}
.sw{display:inline-flex;align-items:center;justify-content:center;border-radius:50%;border:1px solid;flex:none;font-style:normal;font-size:11px;color:var(--onv)}
.cb{width:40px;height:48px;display:flex;align-items:center;justify-content:center}
.cb i{width:22px;height:22px;border-radius:6px;border:2px solid var(--outline);display:flex;align-items:center;justify-content:center;font-style:normal;font-size:14px;font-weight:700}
.cb.on i{background:var(--primary);border-color:var(--primary);color:var(--onprimary)}
.cb.dis i{border-color:var(--ov);opacity:.6}
.row.off .val{color:var(--onv)}.row.off .val .clamp{text-decoration:line-through;text-decoration-color:var(--outline)}
.row.off .thumb{opacity:.7}
.row.hl{background:color-mix(in srgb,var(--secc) 40%,transparent)}
.badge{flex:none;display:inline-flex;align-items:center;height:20px;padding:0 6px;border-radius:6px;font:600 11px/20px var(--fs);white-space:nowrap}
.badge.edit{background:var(--secc);color:var(--onsecc)}.badge.kept{background:var(--highest);color:var(--onv)}
.badge.warn{background:var(--warnc);color:var(--onwarnc)}
.chips{display:flex;flex-wrap:wrap;gap:6px;margin-top:6px}
.chip{display:inline-flex;align-items:center;gap:4px;height:28px;padding:0 10px;border-radius:8px;border:1px solid var(--ov);font:500 13px/18px var(--fs);white-space:nowrap;color:var(--on);background:transparent}
.chip .g{font-weight:700;font-size:14px;width:10px;text-align:center}
.chip.add{background:var(--successc);color:var(--onsuccessc);border-color:transparent}
.chip.rem{color:var(--onv);border-style:dashed;border-color:var(--outline)}.chip.rem .x{text-decoration:line-through}
.chip.same{color:var(--onv)}
.chip.warn{background:var(--warnc);color:var(--onwarnc);border-color:transparent}
.chip.sm{height:24px;font-size:12px;padding:0 8px}
.bb{position:absolute;left:0;right:0;bottom:0;background:var(--lowest);border-top:1px solid var(--line);padding:10px 16px 16px;display:flex;flex-direction:column;gap:8px;box-shadow:0 -4px 14px var(--shadow)}
.bb .meta{font-size:12.5px;line-height:16px;color:var(--onv);text-align:center}
.btn{display:flex;align-items:center;justify-content:center;gap:8px;width:100%;min-height:52px;padding:12px 20px;border-radius:999px;border:0;
  font:600 15px/20px var(--fs);white-space:normal;text-align:center}
.btn.filled{background:var(--primary);color:var(--onprimary)}
.btn.tonal{background:var(--secc);color:var(--onsecc)}
.btn.text{background:transparent;color:var(--primary);min-height:48px}
.btn.dis{background:var(--highest);color:color-mix(in srgb,var(--on) 45%,transparent)}
.ibtn{display:inline-flex;align-items:center;justify-content:center;height:40px;padding:0 16px;border-radius:999px;font:600 14px/20px var(--fs);white-space:nowrap}
.ibtn.tonal{background:var(--secc);color:var(--onsecc)}.ibtn.text{color:var(--primary);padding:0 12px}
.btnrow{display:grid;grid-template-columns:1fr 1fr;gap:8px}
.prog{height:4px;border-radius:2px;background:var(--highest);overflow:hidden;margin:0 20px}
.prog i{display:block;height:100%;background:var(--primary);border-radius:2px}
.progt{display:flex;justify-content:space-between;gap:8px;padding:8px 20px 4px;font-size:13px;line-height:18px;color:var(--onv)}
.progt b{color:var(--on);font-weight:600}
.skel{display:block;border-radius:6px;background:var(--highest)}
.snack{position:absolute;left:16px;right:16px;display:flex;align-items:center;gap:12px;padding:6px 8px 6px 16px;min-height:48px;border-radius:8px;background:var(--inv);color:var(--oninv);font-size:14px;box-shadow:0 4px 12px rgba(0,0,0,.2)}
.snack span{flex:1;min-width:0;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.snack b{color:var(--invprimary);padding:0 8px;white-space:nowrap}
.scrim{position:absolute;inset:0;background:var(--scrim)}
.sheet{position:absolute;left:0;right:0;bottom:0;background:var(--lowest);border-radius:28px 28px 0 0;padding:8px 16px 20px}
.grab{width:32px;height:4px;border-radius:2px;background:var(--ov);margin:4px auto 14px}
.sheet h3{margin:0;font:600 18px/24px var(--fs);padding:0 4px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.sheet .sub{color:var(--onv);font-size:13px;padding:2px 4px 12px}
.keeprow{display:flex;align-items:center;gap:12px;min-height:56px;padding:8px 12px;border-radius:12px;border:1px solid var(--line);margin-bottom:14px}
.keeprow .t{flex:1;min-width:0}.keeprow .t b{display:block;font-weight:600}.keeprow .t span{font-size:12.5px;color:var(--onv)}
.swgrid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px 8px}
.swc{display:flex;flex-direction:column;align-items:center;gap:6px;text-align:center;font-size:12px;line-height:16px;color:var(--on)}
.swc .sw{width:44px;height:44px}
.swc.sel .sw{box-shadow:0 0 0 2px var(--lowest),0 0 0 4px var(--primary)}
.swc.sel>span:last-child{font-weight:600}
.swc>span:last-child{display:-webkit-box;-webkit-line-clamp:3;-webkit-box-orient:vertical;overflow:hidden;min-height:32px}
.swc .swwrap{position:relative}.swc .ai{position:absolute;right:-6px;top:-6px;width:20px;height:20px;border-radius:50%;background:var(--lowest);color:var(--terra);font-size:12px;line-height:20px;box-shadow:0 0 0 1px var(--line)}
.radio{width:22px;height:22px;border-radius:50%;border:2px solid var(--outline);flex:none;margin-right:8px}
.donebox{flex:1;display:flex;flex-direction:column;align-items:center;justify-content:center;padding:0 24px 96px;text-align:center}
.okc{width:72px;height:72px;border-radius:50%;background:var(--secc);color:var(--onsecc);display:flex;align-items:center;justify-content:center;font-size:34px;margin-bottom:20px}
.donebox h2{margin:0;font:600 20px/28px var(--fs);text-wrap:balance}
.donebox .ibtn{margin-top:24px}
.donebox p{margin:8px 0 0;color:var(--onv)}
.mini-list{margin:20px 0 0;display:flex;gap:6px;justify-content:center}
.mini-list .thumb{width:40px;height:46px;border-radius:10px}
/* focus */
.fphoto{margin:4px 16px 0;height:184px;border-radius:24px;overflow:hidden;flex:none}
.fname{padding:12px 20px 0;font:600 18px/24px var(--fs);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.fmeta{padding:0 20px;font-size:13px;color:var(--onv)}
.sugg{margin:12px 16px 0;padding:14px 16px;border-radius:16px;background:var(--lowest);border:1px solid var(--line)}
.sugg .lab{display:flex;align-items:center;gap:8px;font:600 12px/16px var(--fs);letter-spacing:.5px;text-transform:uppercase;color:var(--onv)}
.big{display:flex;gap:12px;align-items:center;margin-top:8px;min-height:40px}
.big .clamp{font:600 22px/28px var(--fs)}
.now{display:flex;gap:8px;align-items:center;margin-top:12px;padding-top:10px;border-top:1px solid var(--line);font-size:14px;color:var(--onv);min-width:0}
.now span{white-space:nowrap;overflow:hidden;text-overflow:ellipsis;min-width:0}
.now em{font-style:normal;font-weight:600;flex:none}
/* pairs */
.pcard{margin:0 16px 12px;padding:12px;border-radius:16px;background:var(--lowest);border:1px solid var(--line)}
.phead{display:flex;gap:12px;align-items:center}
.phead .thumb{width:44px;height:50px;border-radius:10px}
.phead .tx{flex:1;min-width:0}.phead .tx span{display:block;font-size:12px;line-height:16px;color:var(--onv)}
.prow{display:grid;grid-template-columns:minmax(0,1fr) 28px minmax(0,1fr);margin-top:10px}
.cell{border-radius:12px;padding:8px 8px 10px;display:flex;flex-direction:column;align-items:center;gap:6px;text-align:center;background:var(--well);border:1.5px solid transparent;min-width:0}
.cell .cl{font:600 11px/14px var(--fs);letter-spacing:.5px;text-transform:uppercase;color:var(--onv);white-space:nowrap}
.cell .clamp{font:500 13px/18px var(--fs);min-height:36px;display:-webkit-box;align-content:center}
.cell.after{background:var(--lowest);border-color:var(--primary)}
.cell .chips{justify-content:center;margin-top:0}
.arr{display:flex;align-items:center;justify-content:center;color:var(--onv);font-size:16px}
.pact{display:flex;justify-content:flex-end;gap:4px;margin-top:8px}
.pdone{display:flex;align-items:center;gap:10px;margin:0 16px 12px;padding:6px 6px 6px 10px;border-radius:12px;background:var(--low);font-size:13px;color:var(--onv);min-height:52px}
.pdone .thumb{width:32px;height:36px;border-radius:8px}
.pdone span{flex:1;min-width:0;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
/* swipe/segmented sketches */
.stack{position:relative;margin:8px 24px 0;height:470px}
.stack .c{position:absolute;inset:0;border-radius:24px;background:var(--lowest);border:1px solid var(--line);overflow:hidden}
.seg{display:grid;grid-template-columns:1fr 1fr;border:1px solid var(--outline);border-radius:999px;overflow:hidden;margin-top:6px;height:36px}
.seg span{display:flex;align-items:center;justify-content:center;gap:4px;font-size:12.5px;padding:0 6px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.seg span.on{background:var(--secc);font-weight:600}
.seg span+span{border-left:1px solid var(--outline)}
/* current UI recreation */
.cur .card{margin:0 24px 24px;display:flex;background:var(--cont);border:1px solid var(--ov);border-radius:12px;overflow:hidden}
.cur .ph{width:112px;flex:none}
.cur .cc{flex:1;padding:12px;display:flex;flex-direction:column;gap:10px;min-width:0}
.cur .pill{border-radius:12px;padding:6px 10px;font:500 14px/20px var(--fs);background:var(--highest);color:var(--onv)}
.cur .pill.to{background:var(--secc);color:var(--on);font-weight:600}
.cur .hb{display:flex;gap:8px}.cur .hb span{flex:1;height:48px;border-radius:999px;display:flex;align-items:center;justify-content:center;font-weight:600;font-size:14px}
.cur .hb .a{background:var(--primary);color:#fff}.cur .hb .d{border:1px solid var(--outline);color:var(--primary)}
.ann{position:absolute;width:22px;height:22px;border-radius:50%;background:#ba1a1a;color:#fff;font:700 12px/22px sans-serif;text-align:center;z-index:9;box-shadow:0 0 0 2px #fff}
.spark{position:absolute;color:#c37f50;font-size:14px}
"""


def theme_css(accent):
    lo, do = ACCENTS[accent]
    light = {**LIGHT, **lo}
    dark = {**DARK, **do}
    to_vars = lambda d: ";".join(f"--{k}:{v}" for k, v in d.items())
    return f".phone[data-theme=light]{{{to_vars(light)}}}\n.phone[data-theme=dark]{{{to_vars(dark)}}}\n"


# ---------------------------------------------------------------- phone parts
def phone(theme, inner, lang="en"):
    return f'<div class="phone" data-theme="{theme}" lang="{lang}">{status()}{inner}</div>'


def status():
    return '<div class="sb"><span>9:41</span><span>▴ ◔ ▮</span></div>'


def appbar(title, action=None, back="←"):
    act = f'<span class="abact">{e(action)}</span>' if action else ("" if action == "" else '<span class="ib">⋮</span>')
    return f'<div class="ab"><span class="ib">{back}</span><h1>{e(title)}</h1>{act}</div>'


def cb(state):
    if state == "on":
        return '<span class="cb on"><i>✓</i></span>'
    if state == "dis":
        return '<span class="cb dis"><i></i></span>'
    return '<span class="cb"><i></i></span>'


def chip(kind, label, small=False):
    sm = " sm" if small else ""
    if kind == "add":
        return f'<span class="chip add{sm}"><span class="g">+</span>{e(label)}</span>'
    if kind == "rem":
        return f'<span class="chip rem{sm}"><span class="g">−</span><span class="x">{e(label)}</span></span>'
    if kind == "warn":
        return f'<span class="chip warn{sm}">{e(label)}</span>'
    return f'<span class="chip same{sm}">{e(label)}</span>'


def thumb(g, color=None, cls="thumb"):
    return f'<div class="{cls}">{garment_art(g, color)}</div>'


def done_screen(theme, kind, changed, kept, thumbs):
    title = t("review_complete_colors" if kind == "colors" else "review_complete_tags", changed)
    sub = t("review_complete_kept", kept) if kept else ""
    minis = "".join(thumb(g, c) for g, c in thumbs)
    inner = (appbar(t("review_color_title" if kind == "colors" else "review_tag_title"), action="", back="×")
             + f'<div class="donebox"><div class="okc">✓</div><h2>{e(title)}</h2>'
             + (f"<p>{e(sub)}</p>" if sub else "")
             + f'<div class="mini-list">{minis}</div><span class="ibtn tonal">✎ {e(t("review_complete_edit"))}</span></div>'
             + f'<div class="bb"><div class="btn filled">{e(t("done"))}</div></div>')
    return phone(theme, inner, LANG)


# ---------------------------------------------------------------- finalist 1: Checklist
def ck_row(g, new, old, state="on", badge=None, roles=None, color_now=None):
    """roles: list of (role_key, new, old) for multi-role rows."""
    lines = ""
    entries = roles or [(None, new, old)]
    for role, n, o in entries:
        prefix = f"{t(role)} · " if role else ""
        lines += (f'<div class="val">{sw(n)}<span class="clamp">{e(cname(n))}</span></div>'
                  f'<div class="was">{e(prefix + t("review_was"))} {sw(o, 10)}<span>{e(cname(o) if o else t("review_no_color"))}</span></div>')
    bd = ""
    if badge == "edit":
        bd = f'<span class="badge edit">✎ {e(t("review_badge_edited"))}</span>'
    elif badge == "kept":
        bd = f'<span class="badge kept">{e(t("review_badge_kept"))}</span>'
    cls = "row off" if state == "off" else "row"
    return (f'<div class="{cls}">{thumb(g, color_now)}<div style="min-width:0"><div class="nm"><b>{e(gname(g))}</b>{bd}</div>'
            f'{lines}</div>{cb(state)}</div>')


def ck_warn_row(g, removed):
    return (f'<div class="row">{thumb(g)}<div style="min-width:0"><div class="nm"><b>{e(gname(g))}</b></div>'
            f'<div class="chips">{chip("warn", "! " + t("review_choose_color"))}</div>'
            f'<div class="was"><span>{e(t("review_color_removed", cname(removed)))}</span></div></div>{cb("dis")}</div>')


def ck_tag_row(g, chips, state="on"):
    cs = "".join(chip(k, tname(x)) for k, x in chips)
    cls = "row off" if state == "off" else "row"
    return (f'<div class="{cls}">{thumb(g)}<div style="min-width:0"><div class="nm"><b>{e(gname(g))}</b></div>'
            f'<div class="chips">{cs}</div></div>{cb(state)}</div>')


def ck_color_header(n):
    return (f'<div class="hd"><div class="ctx">{e(t("review_color_context"))}</div>'
            f'<h2>{e(t("review_color_found", n))}</h2><p>{e(t("review_checklist_hint"))}</p></div>')


def ck_bottom(n, meta=None, disabled=False):
    m = f'<div class="meta">{e(meta)}</div>' if meta else ""
    cls = "btn dis" if disabled else "btn filled"
    label = t("review_wait_button") if disabled else t("review_apply_n", n)
    return f'<div class="bb">{m}<div class="{cls}">{e(label)}</div></div>'


def ck_screens():
    out = []
    out.append(("Colour review · clear suggestions", lambda th: phone(th,
        appbar(t("review_color_title")) + '<div class="scroll">' + ck_color_header(6)
        + '<div class="list">' + ck_row("coat", "charcoal", "black") + ck_row("shirt", "cream", "white")
        + ck_row("jeans", "denim", "navy")
        + ck_row("jumper", None, None, roles=[("review_role_main", "charcoal", "navy"), ("review_role_second", "white", None)])
        + ck_row("trench", "camel", "beige") + '</div></div>' + ck_bottom(6)),
        ["Default state: every suggestion is ticked. One decision for the whole batch: <b>Apply 6 changes</b>.",
         "Tap the checkbox (48 dp target) → untick; the row stays where it is and greys out (no jump), the button count updates. Haptic: light tick (CLOCK_TICK).",
         "Tap anywhere else on the row → colour sheet for that garment (see the picker phone).",
         "Two-role garments show one line per changed role (Main / Second). Unchanged roles are not shown.",
         "Nothing is saved until Apply, so unticking is the undo. Back → existing “Discard changes?” dialog."]))
    out.append(("Colour review · long names (EN)", lambda th: phone(th,
        appbar(t("review_color_title")) + '<div class="scroll">' + ck_color_header(4)
        + '<div class="list">' + ck_row("chinos", "lybrown", "bgreen")
        + ck_row("longcoat", "charcoal", "dgblue")
        + ck_row("trench", "lybrown", "beige", state="off", badge="kept")
        + ck_row("skirt", "burgundy", "red") + '</div></div>'
        + ck_bottom(3, t("review_kept_n", 1))),
        ["“Bluish green → Light yellowish brown”: the new name wraps to 2 lines max at full width; the old name is a single muted line that ellipsizes. No boxes, so length differences never create ragged boxes.",
         "Long garment names ellipsize on one line; the badge (<i>Kept</i>) never gets pushed out (flex:none).",
         "Unticked row: thumbnail dimmed, new value struck through, <i>Kept</i> badge; the bottom bar shows “1 kept as is”."]))

    def de_long(th):
        global LANG
        prev, LANG = LANG, "de"
        r = phone(th, appbar(t("review_color_title")) + '<div class="scroll">' + ck_color_header(4)
                  + '<div class="list">' + ck_row("chinos", "lybrown", "bgreen")
                  + ck_row("longcoat", "charcoal", "dgblue")
                  + ck_row("trench", "lybrown", "beige", state="off", badge="kept")
                  + ck_row("jumper", None, None, roles=[("review_role_main", "dgblue", "navy"), ("review_role_second", "white", None)])
                  + '</div></div>' + ck_bottom(3, t("review_kept_n", 1)), "de")
        LANG = prev
        return r
    out.append(("Colour review · long German strings", de_long,
        ["Same screen in German: “Bläuliches Grün → Helles gelbliches Braun”, “Zweireihiger Oversize-Wollmantel”, “3 Änderungen übernehmen”.",
         "The full-width button allows wrapping (white-space: normal) but fits on one line here; header copy wraps naturally."]))
    out.append(("Colour review · item the user changed + needs a choice", lambda th: phone(th,
        appbar(t("review_color_title")) + '<div class="scroll">'
        + f'<div class="sech"><span>{e(t("review_section_choose"))} · 1</span></div>'
        + '<div class="list">' + ck_warn_row("dress", "coral") + '</div>'
        + f'<div class="sech"><span>{e(t("review_section_suggested"))} · 5</span></div>'
        + '<div class="list">' + ck_row("chinos", "camel", "bgreen", badge="edit")
        + ck_row("coat", "charcoal", "black") + ck_row("shirt", "cream", "white")
        + ck_row("jeans", "denim", "navy") + '</div></div>'
        + ck_bottom(5, t("review_unchosen_n", 1))),
        ["<b>Needs your choice</b> (warning tone + “!” glyph, not colour alone) sits on top: its colour was deleted and Robia has no target. Checkbox disabled until a colour is picked; tapping the row opens the sheet.",
         "<b>Edited</b>: the user picked Camel instead of the suggestion; the row shows the user’s value and the ✎ Edited badge. Re-open the sheet → “Keep current” or Robia’s suggestion to revert.",
         "Apply skips rows without a choice and says so above the button (no blocking)."]))
    out.append(("Colour picker sheet (tap a row)", lambda th: phone(th,
        appbar(t("review_color_title")) + '<div class="scroll">' + ck_color_header(6)
        + '<div class="list">' + ck_row("chinos", "lybrown", "bgreen") + ck_row("coat", "charcoal", "black") + '</div></div>'
        + '<div class="scrim"></div><div class="sheet"><div class="grab"></div>'
        + f'<h3>{e(t("review_picker_title", gname("chinos")))}</h3><div class="sub">{e(t("review_picker_hint"))}</div>'
        + f'<div class="keeprow">{sw("bgreen", 28)}<div class="t"><b>{e(t("review_picker_keep"))}</b><span>{e(cname("bgreen"))}</span></div><span class="radio"></span></div>'
        + '<div class="swgrid">' + "".join(
            f'<div class="swc{" sel" if c == "camel" else ""}"><span class="swwrap">{sw(c, 44)}{'<i class="ai">✦</i>' if c == "lybrown" else ""}</span><span>{e(cname(c))}</span></div>'
            for c in ("lybrown", "camel", "beige", "cream", "charcoal", "navy", "denim", "olive"))
        + '</div></div>'),
        ["Modal bottom sheet, one tap to choose: tapping a swatch selects it and closes the sheet (no OK button). Haptic: light tick.",
         "Swatches are 44 dp with the name under them (3 lines max, centred) — colour is never shown without its name.",
         "Robia’s suggestion is marked with a small static ✦ on its swatch; the selected colour gets a ring. “Keep current” unticks the row.",
         "Two-role garments: the sheet title switches to “Second colour · …” and adds a “No colour” cell."]))
    out.append(("Tag review · added / removed / unchanged", lambda th: phone(th,
        appbar(t("review_tag_title")) + '<div class="scroll">'
        + f'<div class="hd"><div class="ctx">{e(t("review_tag_context", tname("work")))}</div><h2>{e(t("review_tag_found", 4))}</h2><p>{e(t("review_checklist_hint"))}</p></div>'
        + '<div class="list">' + ck_tag_row("coat", [("add", "work"), ("same", "casual")])
        + ck_tag_row("shirt", [("add", "work"), ("rem", "party"), ("same", "casual")])
        + ck_tag_row("chinos", [("add", "work"), ("same", "weekend")])
        + ck_tag_row("tee", [("add", "work"), ("same", "casual")], state="off")
        + '</div></div>' + ck_bottom(3, t("review_kept_n", 1))),
        ["Diff chips: <b>+ Added</b> (success tonal), <b>− Removed</b> (dashed outline + strike-through), unchanged (plain outline, muted). Meaning is carried by the glyph and the line, not only by colour.",
         "Chip rows flex-wrap; each chip is nowrap so “Weekend away” / “Escapada de fin de semana” never breaks inside a chip.",
         "Tap row → occasion picker (multi-choice) for that garment; edited rows get the ✎ Edited badge like colours.",
         "Category reviews (single choice) render the same: “+ Knitwear  − Tops”."]))

    def tags_es(th):
        global LANG
        prev, LANG = LANG, "es"
        r = phone(th, appbar(t("review_tag_title")) + '<div class="scroll">'
                  + f'<div class="hd"><div class="ctx">{e(t("review_tag_context", tname("work")))}</div><h2>{e(t("review_tag_found", 4))}</h2><p>{e(t("review_checklist_hint"))}</p></div>'
                  + '<div class="list">' + ck_tag_row("coat", [("add", "work"), ("same", "casual")])
                  + ck_tag_row("shirt", [("add", "work"), ("rem", "party"), ("same", "casual")])
                  + ck_tag_row("chinos", [("add", "work"), ("same", "weekend")])
                  + ck_tag_row("tee", [("add", "work"), ("same", "casual")], state="off")
                  + '</div></div>' + ck_bottom(3, t("review_kept_n", 1)), "es")
        LANG = prev
        return r
    out.append(("Tag review · Spanish", tags_es, ["Same state in Spanish; “Escapada de fin de semana” wraps to its own chip line."]))
    out.append(("Batch progress (scan running)", lambda th: phone(th,
        appbar(t("review_color_title")) + '<div class="scroll">'
        + f'<div class="progt"><b>{e(t("review_scanning", 64, 120))}</b><span>{e(t("review_found_so_far", 5))}</span></div>'
        + '<div class="prog"><i style="width:53%"></i></div><div style="height:12px"></div>'
        + '<div class="list">' + ck_row("coat", "charcoal", "black") + ck_row("shirt", "cream", "white")
        + ck_row("jeans", "denim", "navy") + ck_row("trench", "camel", "beige", state="off", badge="kept")
        + ck_row("chinos", "lybrown", "bgreen")
        + '<div class="row"><span class="skel" style="width:56px;height:64px;border-radius:12px"></span><div><span class="skel" style="width:60%;height:14px"></span><span class="skel" style="width:85%;height:12px;margin-top:8px"></span></div><span></span></div>'
        + '</div></div>' + ck_bottom(0, disabled=True)),
        ["Thin 4 dp determinate bar + one line of text replace today’s bold “Batch Progress 64 / 120” header. Rows stream in at the bottom; the list never reorders under the finger.",
         "Rows are already tickable while scanning; Apply unlocks when the scan ends (label switches to “Apply N changes”). No haptic on stream-in.",
         "One static skeleton row marks “more coming” — no shimmer, no pulse."]))
    out.append(("Completion · calm optional Edit", lambda th: done_screen(th, "colors", 5, 1,
        [("coat", "charcoal"), ("shirt", "cream"), ("jeans", "denim"), ("chinos", "camel"), ("jumper", "charcoal")]),
        ["Static check in a tonal circle — no scale-in, no sparkle burst, no auto-dismiss (the screen now has actions, so it waits).",
         "<b>Edit changes</b> is a plain tonal button (no ✦, no shine, no pulse): reopens the same checklist with the applied rows, still editable.",
         "<b>Done</b> (filled, bottom = thumb zone) returns to Manage. Haptic: one CONFIRM when Apply lands, none here.",
         "Thumbnails show the first five changed garments with their new colour (decorative, contentDescription = null)."]))
    return out


# ---------------------------------------------------------------- finalist 2: Focus
def fo_screen(g, label, new, now_text, now_sw=None, counter=(3, 6), primary=None, badge=None, extra="", chips=None,
              color_now=None, title_key="review_color_title", meta_key="review_main_colour", snack=None, scan=None):
    pct = counter[0] / counter[1] * 100
    head = f'<div class="progt"><span>{e(t("review_progress_of", *counter))}</span>'
    head += f'<span>{e(scan)}</span></div>' if scan else '<span></span></div>'
    if chips:
        val = '<div class="chips" style="margin-top:10px">' + "".join(chip(k, tname(x)) for k, x in chips) + '</div>'
    else:
        val = f'<div class="big">{sw(new, 36)}<span class="clamp">{e(cname(new))}</span></div>'
    bd = f'<span class="badge edit">✎ {e(t("review_badge_edited"))}</span>' if badge else ""
    nowsw = sw(now_sw, 14) if now_sw else ""
    body = (f'<div class="scroll">{head}<div class="prog"><i style="width:{pct:.0f}%"></i></div><div style="height:12px"></div>'
            f'<div class="fphoto">{garment_art(g, color_now)}</div>'
            f'<div class="fname">{e(gname(g))}</div><div class="fmeta">{e(t(meta_key))}</div>'
            f'<div class="sugg"><div class="lab">{e(label)}{bd}</div>{val}'
            f'<div class="now"><em>{e(t("review_now"))}</em>{nowsw}<span>{e(now_text)}</span></div>{extra}</div></div>')
    snk = (f'<div class="snack" style="bottom:136px"><span>{e(snack)}</span><b>{e(t("undo"))}</b></div>') if snack else ""
    bb = (f'<div class="bb"><div class="btn filled">{e(primary or t("review_use_suggestion"))}</div>'
          f'<div class="btnrow"><div class="btn text">{e(t("review_keep_current"))}</div><div class="btn text">{e(t("review_change"))}</div></div></div>')
    return appbar(t(title_key), t("review_accept_all")) + body + snk + bb


def fo_screens():
    out = []
    out.append(("Colour review · clear suggestion", lambda th: phone(th, fo_screen(
        "coat", t("review_suggested"), "charcoal", cname("black"), "black")),
        ["One garment at a time. The suggestion is the hero (36 dp swatch + 22 sp name); the current value is one muted line under a divider.",
         "<b>Use suggestion</b> (filled, thumb zone) → saves the decision and slides to the next garment (200 ms, reduced motion: cross-fade). Haptic: light tick.",
         "<b>Keep current</b> / <b>Change…</b> are equal-weight text buttons under it. Change opens the same colour sheet as the Checklist.",
         "<b>Accept all</b> in the app bar takes every remaining suggestion (confirm dialog with the count). Undo snackbar after each decision."]))
    out.append(("Colour review · long names (EN)", lambda th: phone(th, fo_screen(
        "chinos", t("review_suggested"), "lybrown", cname("bgreen"), "bgreen", counter=(4, 6))),
        ["“Light yellowish brown” wraps to two lines at 22 sp; the photo keeps its fixed height so the buttons never move between garments."]))

    def de(th):
        global LANG
        prev, LANG = LANG, "de"
        r = phone(th, fo_screen("longcoat", t("review_suggested"), "lybrown", cname("dgblue"), "dgblue", counter=(4, 6)), "de")
        LANG = prev
        return r
    out.append(("Colour review · long German strings", de,
        ["“Zweireihiger Oversize-Wollmantel” ellipsizes on one line; “Helles gelbliches Braun” wraps; “Alle übernehmen” still fits in the app bar next to the title (title ellipsizes first)."]))
    out.append(("Colour review · item the user changed", lambda th: phone(th, fo_screen(
        "chinos", t("review_your_choice"), "camel", cname("bgreen"), "bgreen", counter=(4, 6), badge=True,
        primary=t("review_use_choice"), color_now="bgreen",
        extra=f'<div class="was" style="margin-top:8px">✦ <span>{e(t("review_robia_suggested", cname("lybrown")))}</span></div>',
        snack=t("review_snack_applied", gname("trench")))),
        ["After “Change…” the hero shows the user’s pick with ✎ Edited; Robia’s original suggestion stays visible as a muted ✦ line so the user can go back.",
         "Primary label becomes <b>Use my choice</b>. The snackbar shows the previous decision with <b>Undo</b> (4 s); Undo steps back to that garment."]))
    out.append(("Tag review · added / removed / unchanged", lambda th: phone(th, fo_screen(
        "shirt", t("review_suggested"), None, ", ".join([tname("party"), tname("casual")]), counter=(2, 4),
        chips=[("add", "work"), ("rem", "party"), ("same", "casual")], title_key="review_tag_title", meta_key="review_occasions")),
        ["The hero becomes the diff chip row (+ added, − removed, unchanged). “Now” lists the current tags as plain text.",
         "Change… opens the multi-choice occasion picker for this garment."]))
    out.append(("Batch progress (scan running)", lambda th: phone(th, fo_screen(
        "coat", t("review_suggested"), "charcoal", cname("black"), "black", counter=(1, 5),
        scan=t("review_scanning", 64, 120))),
        ["The first card opens as soon as one suggestion exists; the progress line shows both the queue (1 of 5) and the scan (Checking 64 of 120…). The denominator grows as the scan finds more.",
         "If the user catches up with the scan, a calm “Checking garments…” placeholder replaces the card (no spinner loop longer than the scan)."]))
    out.append(("Completion · calm optional Edit", lambda th: done_screen(th, "colors", 4, 2,
        [("coat", "charcoal"), ("shirt", "cream"), ("jeans", "denim"), ("chinos", "camel")]),
        ["Same calm completion as the Checklist: static check, tonal <b>Edit changes</b> (opens a list of this session’s decisions), filled <b>Done</b>. No sparkle, no scale, no auto-dismiss."]))
    return out


# ---------------------------------------------------------------- finalist 3: Pairs (before → after)
def pa_cell(c, label, after=False, chips=None):
    cls = "cell after" if after else "cell"
    if chips is not None:
        inner = '<div class="chips">' + "".join(chip(k, tname(x), small=True) for k, x in chips) + '</div>'
    else:
        inner = f'{sw(c, 28)}<span class="clamp">{e(cname(c) if c else t("review_no_color"))}</span>'
    return f'<div class="{cls}"><span class="cl">{e(label)}</span>{inner}</div>'


def pa_card(g, pairs, badge=None, meta_key="review_main_colour", tags=None):
    bd = f'<span class="badge edit">✎ {e(t("review_badge_edited"))}</span>' if badge else ""
    rows = ""
    if tags:
        before, after = tags
        rows = (f'<div class="prow">{pa_cell(None, t("review_now"), chips=before)}<div class="arr">→</div>'
                f'{pa_cell(None, t("review_suggested"), True, chips=after)}</div>')
    for role, old, new in pairs:
        rl = f'<div class="was" style="margin-top:8px"><span>{e(t(role))}</span></div>' if role else ""
        rows += (f'{rl}<div class="prow" style="{"margin-top:4px" if role else ""}">{pa_cell(old, t("review_now"))}'
                 f'<div class="arr">→</div>{pa_cell(new, t("review_your_choice") if badge else t("review_suggested"), True)}</div>')
    meta = t(meta_key) if not pairs or not pairs[0][0] else ""
    return (f'<div class="pcard"><div class="phead">{thumb(g)}<div class="tx"><div class="nm"><b>{e(gname(g))}</b>{bd}</div>'
            f'<span>{e(meta)}</span></div></div>{rows}'
            f'<div class="pact"><span class="ibtn text">{e(t("review_keep_current"))}</span><span class="ibtn tonal">{e(t("review_accept"))}</span></div></div>')


def pa_done(g, c, label):
    return (f'<div class="pdone">{thumb(g, c)}<span>✓ {e(label)} · {e(gname(g))}</span>'
            f'<span class="ibtn text" style="flex:none">{e(t("undo"))}</span></div>')


def pa_bottom(n):
    return f'<div class="bb"><div class="btn filled">{e(t("review_accept_all_n", n))}</div></div>'


def pa_screens():
    out = []
    hdr = lambda n: (f'<div class="hd"><div class="ctx">{e(t("review_color_context"))}</div><h2>{e(t("review_color_found", n))}</h2></div>')
    out.append(("Colour review · clear suggestions", lambda th: phone(th,
        appbar(t("review_color_title")) + '<div class="scroll">' + hdr(6)
        + pa_card("coat", [(None, "black", "charcoal")]) + pa_card("shirt", [(None, "white", "cream")])
        + pa_card("jeans", [(None, "navy", "denim")]) + '</div>' + pa_bottom(6)),
        ["Manu’s fix taken literally: Now and Suggested live in two <b>equal, fixed-width cells</b> with centred swatch + name and a fixed 2-line text slot, joined by a centred arrow. Whatever the lengths, both cells have the same box.",
         "The Suggested cell carries the outline: it is what Accept saves. Tap it → colour sheet.",
         "Per-card actions shrink to one line, right-aligned: text <b>Keep current</b> + tonal <b>Accept</b> (no filled buttons inside cards). Accept all (filled) stays the only primary.",
         "Resolved cards collapse into a one-line summary with Undo (next phones)."]))
    out.append(("Colour review · long names (EN)", lambda th: phone(th,
        appbar(t("review_color_title")) + '<div class="scroll">' + hdr(4)
        + pa_card("chinos", [(None, "bgreen", "lybrown")])
        + pa_card("jumper", [("review_role_main", "navy", "dgblue"), ("review_role_second", None, "white")], meta_key="review_main_colour")
        + '</div>' + pa_bottom(4)),
        ["“Bluish green → Light yellowish brown” both sit in identical 2-line slots: the asymmetry Manu saw is gone by construction.",
         "Two-role garments stack two pairs with a small role label — the card gets tall fast (only ~2 cards per screen)."]))

    def de(th):
        global LANG
        prev, LANG = LANG, "de"
        r = phone(th, appbar(t("review_color_title")) + '<div class="scroll">' + hdr(4)
                  + pa_card("chinos", [(None, "bgreen", "lybrown")]) + pa_card("longcoat", [(None, "dgblue", "charcoal")])
                  + '</div>' + pa_bottom(4), "de")
        LANG = prev
        return r
    out.append(("Colour review · long German strings", de,
        ["German: “Bläuliches Grün → Helles gelbliches Braun”; “Beibehalten” + “Übernehmen” still fit on one action line."]))
    out.append(("Colour review · item the user changed", lambda th: phone(th,
        appbar(t("review_color_title")) + '<div class="scroll">' + hdr(6)
        + pa_done("coat", "charcoal", t("review_accepted_row")) + pa_done("shirt", "cream", t("review_accepted_row"))
        + pa_card("chinos", [(None, "bgreen", "camel")], badge=True) + pa_card("jeans", [(None, "navy", "denim")])
        + '</div>' + pa_bottom(4)),
        ["After picking Camel the right cell is labelled <b>Your choice</b> with ✎ Edited.",
         "Accepted cards collapse to a 52 dp summary row with <b>Undo</b> (stays until the review is committed) instead of disappearing — fixes today’s no-undo exit animation."]))
    out.append(("Tag review · added / removed / unchanged", lambda th: phone(th,
        appbar(t("review_tag_title")) + '<div class="scroll">'
        + f'<div class="hd"><div class="ctx">{e(t("review_tag_context", tname("work")))}</div><h2>{e(t("review_tag_found", 4))}</h2></div>'
        + pa_card("shirt", [], meta_key="review_occasions", tags=([("same", "party"), ("same", "casual")], [("add", "work"), ("rem", "party"), ("same", "casual")]))
        + pa_card("chinos", [], meta_key="review_occasions", tags=([("same", "weekend")], [("add", "work"), ("same", "weekend")]))
        + '</div>' + pa_bottom(4)),
        ["Tag sets in the same two cells; the Suggested cell uses diff chips so the user doesn’t have to compare sets by eye.",
         "Weak spot: with 3–5 tags the cells get tall and long tags (“Wochenendausflug”) fill a whole cell line."]))
    out.append(("Batch progress (scan running)", lambda th: phone(th,
        appbar(t("review_color_title")) + '<div class="scroll">'
        + f'<div class="progt"><b>{e(t("review_scanning", 64, 120))}</b><span>{e(t("review_found_so_far", 5))}</span></div>'
        + '<div class="prog"><i style="width:53%"></i></div><div style="height:12px"></div>'
        + pa_card("coat", [(None, "black", "charcoal")]) + pa_card("shirt", [(None, "white", "cream")])
        + '</div>' + f'<div class="bb"><div class="btn dis">{e(t("review_wait_button"))}</div></div>'),
        ["Same calm progress line as the Checklist; cards can be accepted while the scan runs; Accept all unlocks at the end."]))
    out.append(("Completion · calm optional Edit", lambda th: done_screen(th, "colors", 5, 1,
        [("coat", "charcoal"), ("shirt", "cream"), ("jeans", "denim"), ("chinos", "camel"), ("jumper", "dgblue")]),
        ["Shared calm completion: static check, tonal Edit changes, filled Done; no burst, no auto-dismiss."]))
    return out


# ---------------------------------------------------------------- sketches, current UI, shared fixes
def current_ui():
    global LANG
    LANG = "en"
    card = ('<div class="card" style="position:relative"><div class="ph">' + garment_art("shirt") + '</div><div class="cc">'
            '<div class="pill">Party, Casual</div><div style="text-align:center;color:var(--onv);margin:-4px 0">↓</div>'
            '<div class="pill to">Party, Casual, Work ▾</div>'
            '<div class="hb"><span class="a">Accept</span><span class="d">Discard</span></div></div></div>')
    card2 = ('<div class="card"><div class="ph">' + garment_art("chinos") + '</div><div class="cc">'
             '<div class="pill">Casual</div><div style="text-align:center;color:var(--onv);margin:-4px 0">↓</div>'
             '<div class="pill to">Casual, Weekend away, Work ▾</div>'
             '<div class="hb"><span class="a">Accept</span><span class="d">Discard</span></div></div></div>')
    inner = ('<div class="ab"><span class="ib">←</span><h1>Review garments</h1><span class="ib">⋮</span></div>'
             '<div class="scroll cur" style="padding-top:8px">'
             '<div style="padding:0 24px 16px"><div style="font:700 22px/28px var(--fs)">New occasion: Work</div>'
             '<div style="color:var(--onv)">4 garments may need this occasion.</div></div>'
             '<div style="padding:0 24px 24px"><div style="display:flex;justify-content:space-between;gap:8px;white-space:nowrap;font:700 20px/28px var(--fs)"><span style="color:var(--onv)">Garment scan</span><span>120 / 120 garments</span></div>'
             '<div style="height:8px;border-radius:4px;background:var(--primary);margin-top:8px"></div></div>'
             + card + card2 + '</div>'
             '<div class="bb" style="padding:24px"><div class="btn filled" style="min-height:56px">✓ Accept all (4)</div></div>'
             '<span class="ann" style="left:300px;top:250px">1</span><span class="ann" style="left:326px;top:302px">2</span>'
             '<span class="ann" style="left:138px;top:332px">3</span><span class="ann" style="left:6px;top:220px">4</span>'
             '<span class="ann" style="left:1px;top:162px">5</span><span class="ann" style="left:318px;top:664px">6</span>'
             '<span class="ann" style="left:6px;top:440px">7</span>')
    return phone("light", inner)


def current_color_ui():
    dots = lambda a, b: (f'<span style="display:inline-flex;width:48px;justify-content:space-between">{sw(a, 28)}{sw(b, 28)}</span>')
    card = ('<div class="card"><div class="ph">' + garment_art("chinos") + '</div><div class="cc">'
            '<div style="display:flex;justify-content:space-between;align-items:center;border:1px solid var(--ov);border-radius:12px;padding:8px 10px;background:var(--surface)">'
            + dots("bgreen", "beige") + '<span>→</span>' + dots("lybrown", "beige") + '</div>'
            '<div class="hb"><span class="a">Accept</span><span class="d">Discard</span></div></div></div>')
    inner = ('<div class="ab"><span class="ib">←</span><h1>Color Mapping</h1><span class="ib">⋮</span></div>'
             '<div class="scroll cur" style="padding-top:8px">'
             '<div style="padding:0 24px 24px"><div style="display:flex;justify-content:space-between;gap:8px;white-space:nowrap;font:700 20px/28px var(--fs)"><span style="color:var(--onv)">Batch Progress</span><span>120 / 120 items</span></div>'
             '<div style="height:8px;border-radius:4px;background:var(--primary);margin-top:8px"></div></div>'
             + card + card.replace("chinos", "coat") + card + '</div>'
             '<div class="bb" style="padding:24px"><div class="btn filled" style="min-height:56px">✓ Accept all (6)</div></div>'
             '<span class="ann" style="left:170px;top:178px">8</span>')
    return phone("light", inner)


def edit_photo_shared(theme, calm):
    global LANG
    LANG = "en"
    if calm:
        btn = f'<span class="ibtn tonal">✎ {e(t("edit_photo_action"))}</span>'
    else:
        btn = (f'<span class="ibtn tonal" style="position:relative;box-shadow:0 0 0 6px color-mix(in srgb,var(--terra) 25%,transparent)">{e(t("edit_photo_action"))}'
               f'<span style="color:var(--terra);margin-left:6px">✦</span>'
               '<span class="spark" style="left:-10px;top:-14px">✦</span><span class="spark" style="right:-12px;top:-10px">✧</span>'
               '<span class="spark" style="left:40%;top:-20px">✦</span><span class="spark" style="right:-14px;bottom:-12px">✦</span></span>')
    inner = (appbar(t("add_title")) + '<div class="scroll" style="padding:0 16px">'
             + '<div style="border-radius:16px;overflow:hidden;background:var(--lowest);border:1px solid var(--line)">'
             + f'<div style="height:300px">{garment_art("jumper")}</div>'
             + f'<div style="display:flex;align-items:center;justify-content:space-between;padding:12px 16px;background:var(--cont)">'
             + f'<span class="ibtn text">▣ {e(t("gallery"))}</span>{btn}</div></div>'
             + f'<div class="was" style="padding:12px 4px"><span>✓ {e(t("photo_ready"))}</span></div></div>')
    return phone(theme, inner)


def sketch_swipe():
    global LANG
    LANG = "en"
    inner = (appbar("Review colours") + '<div class="progt"><span>3 of 6</span><span></span></div><div class="prog"><i style="width:50%"></i></div>'
             '<div class="stack"><div class="c" style="transform:translate(10px,12px) rotate(3deg)"></div>'
             '<div class="c" style="transform:rotate(-4deg) translateX(-18px)">'
             f'<div style="height:300px">{garment_art("coat")}</div><div style="padding:14px 16px">'
             f'<div class="nm"><b>Wool coat</b></div><div class="val">{sw("charcoal")}<span>Charcoal</span></div>'
             f'<div class="was">was {sw("black", 10)}<span>Black</span></div></div></div></div>'
             '<div style="display:flex;justify-content:center;gap:48px;margin-top:20px">'
             '<span style="width:64px;height:64px;border-radius:50%;border:1px solid var(--outline);display:flex;align-items:center;justify-content:center;font-size:24px">✕</span>'
             '<span style="width:64px;height:64px;border-radius:50%;background:var(--primary);color:var(--onprimary);display:flex;align-items:center;justify-content:center;font-size:24px">✓</span></div>')
    return phone("light", inner)


def sketch_segmented():
    global LANG
    LANG = "de"
    row = lambda g, o, n: (f'<div class="row" style="grid-template-columns:56px minmax(0,1fr)">{thumb(g)}<div style="min-width:0"><div class="nm"><b>{e(gname(g))}</b></div>'
                           f'<div class="seg"><span>{sw(o, 12)} {e(cname(o))}</span><span class="on">{sw(n, 12)} {e(cname(n))}</span></div></div></div>')
    inner = (appbar("Farben prüfen") + '<div class="scroll" style="padding-top:8px"><div class="list">'
             + row("chinos", "bgreen", "lybrown") + row("coat", "black", "charcoal") + row("longcoat", "dgblue", "charcoal")
             + row("jeans", "navy", "denim") + '</div></div>'
             + '<div class="bb"><div class="btn filled">Alle übernehmen (6)</div></div>')
    LANG = "en"
    return phone("light", inner)


# ---------------------------------------------------------------- pages
def head(title, accent):
    return (f'<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width">'
            f'<title>{e(title)}</title><style>{CSS}{theme_css(accent)}</style></head><body><div class="page">')


TAIL = "</div></body></html>"


def strings_table(keys):
    rows = "".join(f"<tr><td>{e(k)}</td><td>{e(S[k][0])}</td><td>{e(S[k][1])}</td><td>{e(S[k][2])}</td></tr>" for k in keys)
    return f'<table class="str"><tr><th>key</th><th>EN</th><th>ES</th><th>DE</th></tr>{rows}</table>'


def pair_block(title, fn, notes):
    global USED, LANG
    USED, LANG = [], "en"
    light = fn("light")
    dark = fn("dark")
    keys = list(USED)
    caption = "".join(f"<li>{n}</li>" for n in notes)
    return (f'<div class="pair"><div class="lbl">{e(title)} — light · dark (proposed)</div>'
            f'<div class="phones">{light}{dark}</div>'
            f'<div class="cap"><b class="t">{e(title)}</b><ul>{caption}</ul></div>'
            f'<div class="lbl">Strings used on this screen</div>{strings_table(keys)}</div>')


FINALISTS = [
    ("checklist", "Finalist 1 · Checklist (recommended)",
     "One list, everything ticked by default, one Apply button. Each row says what will change in plain text with the new value first and “was …” under it. You only touch the rows you disagree with.",
     ck_screens),
    ("focus", "Finalist 2 · Focus card",
     "One garment at a time, big photo, the suggestion large and the current value small. Use suggestion / Keep current / Change… sit in the thumb zone.",
     fo_screens),
    ("pairs", "Finalist 3 · Symmetric pairs",
     "Today’s card, fixed: Now → Suggested in two equal, centred cells with a fixed text slot, quiet per-card actions, collapsed resolved cards with Undo.",
     pa_screens),
]

DARK_NOTE = ('<div class="note"><b>Dark theme note.</b> Robia ships light only today (no dark scheme in <code>Theme.kt</code>; dark was out of scope, '
             'see <code>artifacts/inv-r3-c.md</code>). The dark phones use a derived proposal of the same Earth &amp; Steel palette, defined in '
             '<code>build.py</code> (<code>DARK</code>). Accent: the repo tokens are espresso/champagne, not indigo; '
             '<code>python3 build.py --accent indigo</code> re-renders everything with an indigo accent if the pending palette change lands that way.</div>')


def finalist_page(slug, title, pitch, screens, accent):
    blocks = "".join(pair_block(name, fn, notes) for name, fn, notes in screens())
    return (head(f"Robia · review redesign · {title}", accent)
            + f'<p><a href="index.html">← All proposals</a></p><h1>{e(title)}</h1><p class="lead">{e(pitch)}</p>'
            + DARK_NOTE + f'<div class="pairs">{blocks}</div>' + TAIL)


def index_page(accent):
    global LANG, USED
    LANG, USED = "en", []
    side = ""
    for slug, title, pitch, screens in FINALISTS:
        sc = screens()
        side += (f'<div class="col"><h3><a href="{slug}.html">{e(title)}</a><small>{e(pitch)}</small></h3>{sc[0][1]("light")}</div>')
    side_tags = ""
    for slug, title, pitch, screens in FINALISTS:
        sc = [s for s in screens() if s[0].startswith("Tag review ·") and "Spanish" not in s[0]][0]
        side_tags += f'<div class="col"><h3>{e(title)}</h3>{sc[1]("light")}</div>'
    diag = """
<ol>
<li><b>Ragged boxes (Manu’s hypothesis — confirmed).</b> Tag review stacks two full-width pills, text left-aligned: “Party, Casual” and “Party, Casual, Work ▾” end at different x, while the ↓ between them is centred. Three different alignment axes in one card.</li>
<li><b>The tap target hides in the text.</b> The “To” pill is also the picker; the only affordance is a “▾” glued to the end of the label, so it moves with the text length.</li>
<li><b>No diff.</b> “From” and “To” repeat the whole set; the user has to compare lists to find the one tag that changed (Work).</li>
<li><b>Buttons outweigh the content.</b> Every card has a filled Accept + outlined Discard at half width (48 dp), plus a filled “Accept all” at the bottom: 2N+1 heavy buttons, three of them filled on one screen. “Discard” is ambiguous (discard the garment? the suggestion?).</li>
<li><b>The header shouts after it’s done.</b> “Garment scan · 120 / 120 garments” in bold titleLarge with an 8 dp bar stays as the biggest thing on screen after the scan ends.</li>
<li><b>Slow for big batches.</b> 112 dp photo column + 24 dp gaps → ~2.5 cards per screen; 120 garments ≈ 50 screens of scrolling and 2 taps each.</li>
<li><b>No garment name, no undo.</b> Cards don’t say which garment it is; an accepted card slides away after 200 ms and can’t be brought back.</li>
<li><b>Colour review shows dots only.</b> Four 28 dp dots (below the 48 dp target) with no names: “Navy” vs “Black” or “Beige” vs “Cream” are indistinguishable, and the dots are also the picker buttons.</li>
<li><b>Completion and Edit effects are louder than the decision.</b> Sparkle burst + scale-in on completion; the optional “Edit photo” after a scan gets its own SparkleBurst and a terracotta ✦ — the loudest thing on the screen is an optional action.</li>
</ol>"""
    directions = [
        ("D · Checklist with opt-out", "fin", "Ticked list + one Apply bar. New value first in text, “was …” under it; tags as diff chips.",
         "Fastest for 100+ (0 taps if all good), one primary, list layout immune to length differences. Risk: users may Apply without looking — mitigated by readable rows and the Edit changes exit.", None),
        ("B · Focus card", "fin", "One garment per screen, suggestion large, current small; Keep current as a text action.",
         "Best for careful review and small batches, great one-handed. Slow for 100+ (one screen per garment) — needs Accept all.", None),
        ("A · Symmetric pairs", "fin", "Now → Suggested in fixed-width, centred cells with a clear arrow.",
         "Directly fixes the asymmetry and is closest to today’s code. Still ~3 cards per screen and per-card buttons.", None),
        ("C · Swipe stack", "drop", "Tinder-style stack: swipe right accept, left keep.",
         "Dropped: gestures are invisible and not accessible without buttons (so it becomes B with extra risk), accidental swipes on a 120-garment batch are costly, and swiping a wardrobe reads as playful, not calm.", sketch_swipe),
        ("E · Inline segmented choice", "drop", "Each row has a “Current | Suggested” segmented control and a sticky Apply all bar.",
         "Dropped: it re-creates Manu’s problem — two segments of equal width with unequal labels; in German both names truncate (“Bläuliches…”), and each row doubles the tap targets.", sketch_segmented),
        ("F · Diff language", "lang", "Colours as swatches with their name; tags as + added / − removed / unchanged chips.",
         "Not a layout but the value vocabulary: adopted by all three finalists.", None),
    ]
    first = {slug: screens()[0][1] for slug, _, _, screens in FINALISTS}
    FIN_SKETCH = {"D": lambda: first["checklist"]("light"), "B": lambda: first["focus"]("light"), "A": lambda: first["pairs"]("light")}
    sk = ""
    for name, kind, pitch, critique, fn in directions:
        verdict = {"fin": "finalist", "drop": "dropped", "lang": "adopted everywhere"}[kind]
        mini = f'<div class="mini">{fn()}</div>' if fn else ""
        if name[0] in FIN_SKETCH:
            mini = f'<div class="mini">{FIN_SKETCH[name[0]]()}</div>'
        sk += (f'<div class="sketch">{mini}<span class="verdict {kind}">{verdict}</span><h3>{e(name)}</h3>'
               f'<p><b>{e(pitch)}</b></p><p>{e(critique)}</p></div>')
    cmp_rows = [
        ("Speed, 120 garments", ("good", "1 tap if all good; ~7 rows/screen"), ("mid", "1 tap per garment, or Accept all"), ("bad", "~3 cards/screen, 1 tap each")),
        ("Fixes the asymmetry", ("good", "no boxes at all; text lines"), ("good", "one value is the hero"), ("good", "equal fixed cells")),
        ("Which value is applied?", ("good", "checked = new value, always first"), ("good", "button names the action"), ("mid", "outlined cell; needs reading"))
        , ("Error recovery", ("good", "nothing saved until Apply; Edit changes after"), ("good", "Undo snackbar per decision"), ("good", "collapsed rows keep Undo")),
        ("One-handed reach", ("good", "Apply bottom; checkboxes at right edge"), ("good", "all actions bottom"), ("mid", "actions inside each card")),
        ("Accessibility", ("good", "48 dp checkbox, row = picker, text labels"), ("good", "big targets, linear focus"), ("mid", "2 buttons + cell per card")),
        ("Long ES/DE strings", ("good", "wraps 2 lines, old value ellipsizes"), ("good", "22 sp wraps"), ("mid", "fits, but 2-line slot is the limit")),
        ("1–5 changes / garment", ("good", "one line per role, chips wrap"), ("good", "chips wrap in the hero"), ("bad", "cells get tall")),
        ("Dark theme", ("good", "tokens only"), ("good", "tokens only"), ("good", "tokens only")),
        ("Build cost (Compose)", ("mid", "new row + sheet; reuses commit logic"), ("mid", "new pager screen"), ("good", "reshape existing card")),
    ]
    cmp = "<table class='cmp'><tr><th></th><th>1 · Checklist</th><th>2 · Focus card</th><th>3 · Symmetric pairs</th></tr>"
    for crit, *cells in cmp_rows:
        cmp += f"<tr><th>{e(crit)}</th>" + "".join(f"<td><span class='rate {c}'>{ {'good': 'Good', 'mid': 'OK', 'bad': 'Weak'}[c] }</span> {e(txt)}</td>" for c, txt in cells) + "</tr>"
    cmp += "</table>"
    shared = (f'<div class="pair"><div class="lbl">Add garment after the scan — today vs proposed</div><div class="phones">'
              f'<div><div class="lbl" style="margin-bottom:8px">Today</div>{edit_photo_shared("light", False)}</div>'
              f'<div><div class="lbl" style="margin-bottom:8px">Proposed</div>{edit_photo_shared("light", True)}</div></div>'
              '<div class="cap"><b class="t">“Edit photo” after the scan: calm</b><ul>'
              '<li>Left (today): FilledTonalButton + terracotta ✦ + one-shot SparkleBurst when processing ends — draws the eye to an optional action.</li>'
              '<li>Right (proposed): same tonal button with a static ✎ icon, no ✦, no burst, no glow; it just becomes enabled when the photo is ready. Success is told by the plain “✓ Photo ready” line.</li>'
              '<li>Same rule on the review completion screen: tonal “Edit changes”, static check, no scale-in, no sparkle.</li>'
              '</ul></div></div>')
    LANG, USED = "en", []
    shared_dark = edit_photo_shared("dark", True)
    keys = list(USED)
    return (head("Robia · garment review redesign", accent)
            + '<h1>Garment review — redesign proposals</h1>'
            + '<p class="lead">Brief: Manu finds the garment review ugly (“el before and after, al ser palabras, tienen diferente longitud y al estar alineados a la izquierda en un chip que ocupa todo el espacio…”) and the Edit effect after a scan too loud for an optional button. Scope: tag review (category/occasion added, renamed or deleted), colour review (palette added/edited/deleted/restored), batch progress and completion. Design only — no Kotlin.</p>'
            + '<div class="reco"><p><b>Recommendation: Finalist 1 — Checklist.</b></p>'
            + '<p>The review is a confirmation task: Robia already did the work and most suggestions are right. The Checklist turns 120 garments into one decision (“Apply 118 changes”) and makes disagreeing cheap (untick, or tap the row to pick). It removes the boxes that caused the asymmetry instead of balancing them, shows the change as text with the new value first, and leaves a single filled button on screen. It has the best error recovery: nothing is saved until Apply, and “Edit changes” on the calm completion screen reopens the list.</p>'
            + '<p>Keep Finalist 2’s ideas as parts of it: the row tap opens a sheet with the suggestion large (B’s hero), and the swatch-with-name + diff chips language is shared. Finalist 3 is the fallback if we want the smallest code change this round.</p></div>'
            + DARK_NOTE
            + '<h2 class="sec">1 · What the user does today</h2><p class="secd">After a tag or palette change is saved, a prompt offers a review. Robia scans garments (progress header), then lists one card per garment with a proposal: '
            + 'tag review — current tags of that kind → proposed tags (tap to pick, multi-choice for occasions); colour review — current main/secondary colour → proposed (tap a dot to pick). Each card: Accept / Discard; a manual-choice card has no proposal. Bottom: Accept all (N) / Save N accepted / Done. '
            + 'Back → “Discard changes?” (undoes the tag/palette change too). Commit → completion screen (check + sparkles, 1.6 s auto-dismiss). Data per item: photo, (no name), before value(s), after value(s), proposal kind (suggested vs choose manually). No confidence value is shown or stored per proposal.</p>'
            + f'<h2 class="sec">2 · Why it looks wrong</h2><div class="diag">{current_ui()}{current_color_ui()}{diag}</div>'
            + f'<h2 class="sec">3 · Directions explored</h2><p class="secd">Five layouts plus one value language. Three go forward; the dropped ones are kept as sketches with the reason.</p><div class="sketches">{sk}</div>'
            + f'<h2 class="sec">4 · Finalists side by side — colour review</h2><p class="secd">Same data, light theme. Open each finalist for all states (long names EN/DE, user-changed, tags, progress, completion) in light and dark, each with its string table.</p><div class="side">{side}</div>'
            + f'<h2 class="sec">Finalists side by side — tag review</h2><div class="side">{side_tags}</div>'
            + f'<h2 class="sec">5 · Comparison</h2>{cmp}'
            + f'<h2 class="sec">6 · Shared fix: calm optional Edit</h2><div class="pairs">{shared}'
            + f'<div class="pair" style="width:auto"><div class="lbl">Proposed, dark</div><div class="phones">{shared_dark}</div>'
            + f'<div class="lbl">Strings</div>{strings_table(keys)}</div></div>'
            + '<h2 class="sec">Pages</h2><ul>' + "".join(f'<li><a href="{s}.html">{e(ti)}</a></li>' for s, ti, _, _ in FINALISTS) + '</ul>'
            + '<p class="secd">Built by <code>build.py</code> in this folder; screenshots are the PNGs next to each page. Fonts: rendered with the fallback stack (Inter/Roboto were not installed on the build machine; the fallback is wider, so strings that fit here fit on device).</p>'
            + TAIL)


def screenshot(page):
    chrome = shutil.which("chromium") or shutil.which("chromium-browser") or shutil.which("google-chrome")
    if not chrome:
        print("chromium not found: HTML only", file=sys.stderr)
        return
    png = page.with_suffix(".png")
    subprocess.run([chrome, "--headless", "--disable-gpu", "--no-sandbox", "--hide-scrollbars", "--force-device-scale-factor=1",
                    f"--screenshot={png}", "--window-size=1640,16000", page.as_uri()],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:  # trim the empty page bottom
        from PIL import Image, ImageChops
        im = Image.open(png).convert("RGB")
        bg = Image.new("RGB", im.size, im.getpixel((im.width - 1, im.height - 1)))
        box = ImageChops.difference(im, bg).getbbox()
        if box:
            im.crop((0, 0, im.width, min(im.height, box[3] + 40))).save(png)
    except ImportError:
        pass
    print("shot", png.name)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--accent", choices=sorted(ACCENTS), default="earth")
    ap.add_argument("--shots", action="store_true")
    args = ap.parse_args()
    pages = []
    for slug, title, pitch, screens in FINALISTS:
        p = OUT / f"{slug}.html"
        p.write_text(finalist_page(slug, title, pitch, screens, args.accent), encoding="utf-8")
        pages.append(p)
    p = OUT / "index.html"
    p.write_text(index_page(args.accent), encoding="utf-8")
    pages.insert(0, p)
    for p in pages:
        print("wrote", p.name)
        if args.shots:
            screenshot(p)


if __name__ == "__main__":
    main()
