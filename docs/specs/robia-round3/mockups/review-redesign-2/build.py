#!/usr/bin/env python3
"""Garment-review redesign, design round 2 (after Manu chose "Checklist" with corrections).

    python3 build.py           # writes index.html + the finalist pages next to this file
    python3 build.py --shots   # also screenshots: one PNG per page (sliced when tall) + one PNG per phone

Round 1 (../review-redesign/build.py) stays untouched: this script imports it for the shared CSS, the Earth & Steel
light tokens (Color.kt), the derived dark proposal and the garment art, and adds round-2 components on top.
Every visible string goes through t(); the table under each phone lists exactly the keys that phone used.
"""
import argparse
import html
import importlib.util
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

sys.dont_write_bytecode = True  # keep round 1's folder untouched when importing it
OUT = Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("r1", OUT.parent / "review-redesign" / "build.py")
r1 = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(r1)

# ---------------------------------------------------------------- strings (key -> EN, ES, DE)
S = {
    # existing app strings (values*/strings.xml), reused as they are
    "tag_review_title": ("Review garments", "Revisar prendas", "Kleidungsstücke prüfen"),
    "accept": ("Accept", "Aceptar", "Akzeptieren"),
    "undo": ("Undo", "Deshacer", "Rückgängig"),
    "done": ("Done", "Listo", "Fertig"),
    "cancel": ("Cancel", "Cancelar", "Abbrechen"),
    "review_card_discard": ("Discard", "Descartar", "Verwerfen"),
    "color_review_title": ("Color Mapping", "Mapeo de colores", "Farbzuordnung"),
    "color_review_batch_progress": ("Batch Progress", "Progreso del lote", "Stapel-Fortschritt"),
    "color_review_scanning_count": ("%1$d / %2$d items", "%1$d / %2$d prendas", "%1$d / %2$d Teile"),
    "color_review_accept_all": ("Accept all (%1$d)", "Aceptar todo (%1$d)", "Alle akzeptieren (%1$d)"),
    # new: colour review hint (the only change Manu asked for on the colour review)
    "color_review_tap_hint": ("Robia suggests the colours on the right. Tap one to change it before you accept.",
                              "Robia propone los colores de la derecha. Toca uno para cambiarlo antes de aceptar.",
                              "Robia schlägt die Farben rechts vor. Tippe auf eine, um sie vor dem Akzeptieren zu ändern."),
    # new: tag review
    "review_ctx_restored_occ": ("Default occasions restored", "Ocasiones predeterminadas restauradas", "Standard-Anlässe wiederhergestellt"),
    "review_ctx_restored_all": ("Default tags restored", "Etiquetas predeterminadas restauradas", "Standard-Tags wiederhergestellt"),
    "review_ctx_new_occ": ("New occasion: %1$s", "Nueva ocasión: %1$s", "Neu (Anlass): %1$s"),
    "review_hint_per_tag": ("Accept or reject each suggestion. Reviewed garments leave the list.",
                            "Acepta o rechaza cada sugerencia. Las prendas revisadas salen de la lista.",
                            "Akzeptiere oder lehne jeden Vorschlag ab. Geprüfte Kleidungsstücke verschwinden aus der Liste."),
    "review_hint_pick": ("Tap a tag to leave it out, then accept or reject the garment. Reviewed garments leave the list.",
                         "Toca una etiqueta para quitarla y luego acepta o rechaza la prenda. Las prendas revisadas salen de la lista.",
                         "Tippe auf einen Tag, um ihn wegzulassen, und akzeptiere oder lehne dann das Kleidungsstück ab. Geprüfte Kleidungsstücke verschwinden aus der Liste."),
    "review_left": ("%1$d left", "Quedan %1$d", "Noch %1$d"),
    "review_reviewed_n": ("%1$d reviewed", "%1$d revisadas", "%1$d geprüft"),
    "review_group_category": ("Category", "Categoría", "Kategorie"),
    "review_group_occasions": ("Occasions", "Ocasiones", "Anlässe"),
    "review_now": ("Now: %1$s", "Ahora: %1$s", "Jetzt: %1$s"),
    "review_none": ("none", "ninguna", "keine"),
    "review_instead_of": ("instead of %1$s", "en lugar de %1$s", "statt %1$s"),
    "review_reject": ("Reject", "Rechazar", "Ablehnen"),
    "review_accept_n": ("Accept %1$d", "Aceptar %1$d", "%1$d akzeptieren"),
    "review_reject_all": ("Reject all", "Rechazar todo", "Alle ablehnen"),
    "review_accept_all_card": ("Accept all", "Aceptar todo", "Alle akzeptieren"),
    "review_reject_rest": ("Reject the rest", "Rechazar el resto", "Rest ablehnen"),
    "review_accept_rest": ("Accept the rest (%1$d)", "Aceptar el resto (%1$d)", "Rest akzeptieren (%1$d)"),
    "review_cd_accept_tag": ("Accept %1$s", "Aceptar %1$s", "%1$s akzeptieren"),
    "review_cd_reject_tag": ("Reject %1$s", "Rechazar %1$s", "%1$s ablehnen"),
    "review_row_accepted": ("Accepted", "Aceptada", "Akzeptiert"),
    "review_row_rejected": ("Rejected", "Rechazada", "Abgelehnt"),
    "review_keep_as_is": ("Keep as is", "Dejar como está", "Unverändert lassen"),
    "review_none_selected": ("No tags selected: the garment stays as it is.", "Ninguna etiqueta elegida: la prenda se queda como está.",
                             "Keine Tags ausgewählt: Das Kleidungsstück bleibt unverändert."),
    "review_accept_remaining": ("Accept all remaining (%1$d)", "Aceptar las %1$d restantes", "Alle restlichen %1$d akzeptieren"),
    "review_snack_accepted": ("Accepted: %1$s", "Aceptado: %1$s", "Akzeptiert: %1$s"),
    "review_snack_kept": ("Kept as it was", "Se queda como estaba", "Bleibt unverändert"),
    "review_snack_bulk": ("%1$d garments accepted", "%1$d prendas aceptadas", "%1$d Kleidungsstücke akzeptiert"),
    "review_outcome_kept": ("Kept as it was", "Se queda como estaba", "Bleibt unverändert"),
    "review_outcome_added": ("Adds %1$s", "Añade %1$s", "Fügt %1$s hinzu"),
    "review_section_reviewed": ("Reviewed · %1$d", "Revisadas · %1$d", "Geprüft · %1$d"),
    "review_show": ("Show", "Mostrar", "Anzeigen"),
    "review_hide": ("Hide", "Ocultar", "Ausblenden"),
    "review_again": ("Review again", "Revisar otra vez", "Erneut prüfen"),
    "review_bulk_title": ("Accept the remaining %1$d?", "¿Aceptar las %1$d restantes?", "Die restlichen %1$d akzeptieren?"),
    "review_bulk_body": ("Robia will add %1$d tags to %2$d garments. You can undo it right after.",
                         "Robia añadirá %1$d etiquetas a %2$d prendas. Podrás deshacerlo justo después.",
                         "Robia fügt %2$d Kleidungsstücken %1$d Tags hinzu. Du kannst das gleich danach rückgängig machen."),
    "review_complete_tags": ("Tags updated on %1$d garments", "Etiquetas actualizadas en %1$d prendas",
                             "Tags bei %1$d Kleidungsstücken aktualisiert"),
    "review_complete_kept": ("%1$d kept as they were", "%1$d se quedaron igual", "%1$d blieben unverändert"),
    "review_complete_edit": ("Edit changes", "Editar cambios", "Änderungen bearbeiten"),
}
LANGS = ("en", "es", "de")
LANG = "en"
USED = []


def t(key, *args):
    if key not in USED:
        USED.append(key)
    tpl = S[key][LANGS.index(LANG)]
    return re.sub(r"%(\d)\$[sd]", lambda m: str(args[int(m.group(1)) - 1]), tpl)


e = r1.e

# ---------------------------------------------------------------- user data: real default tags (TagLocalization.kt)
TAGS = {
    "everyday": ("Everyday", "Diario", "Alltag"), "business": ("Business", "Negocios", "Business"),
    "formal": ("Formal", "Formal", "Formell"), "dressedup": ("Dressed-up", "Arreglado", "Elegant"),
    "statement": ("Statement", "Llamativo", "Statement"), "active": ("Active", "Activo", "Aktiv"),
    "coats": ("Coats", "Abrigos", "Mäntel"), "jackets": ("Jackets", "Chaquetas", "Jacken"),
    "shirts": ("Shirts", "Camisas", "Hemden"), "tops": ("Tops", "Partes de arriba", "Oberteile"),
    "knitwear": ("Knitwear", "Punto", "Strickwaren"), "trousers": ("Trousers", "Pantalones", "Hosen"),
    "dresses": ("Dresses", "Vestidos", "Kleider"), "blazers": ("Blazers", "Blazers", "Blazer"),
    "skirts": ("Skirts", "Faldas", "Röcke"),
    # a user-made occasion: custom tags are shown as typed (TagDisplayPolicy); one per language here for the long-string test
    "weekend": ("Weekend away with friends", "Escapada de fin de semana", "Wochenendausflug mit Freunden"),
}
CATEGORY_TAGS = {"coats", "jackets", "shirts", "tops", "knitwear", "trousers", "dresses", "blazers", "skirts"}

# id -> art kind, palette colour (r1.COLORS), category tag, current occasions. No name: ClothingItem.name has no UI.
GARMENTS = {
    "coat": ("coat", "black", "coats", ["everyday"]),
    "blazer": ("coat", "navy", "blazers", ["business"]),
    "shirt": ("tee", "white", "shirts", ["everyday"]),
    "knit": ("knit", "camel", "tops", []),
    "dress": ("dress", "coral", "dresses", ["everyday"]),
    "trousers": ("pants", "charcoal", "trousers", ["everyday"]),
    "skirt": ("dress", "burgundy", "skirts", []),
    "tee": ("tee", "olive", "tops", ["active"]),
    "jeans": ("pants", "denim", "trousers", ["everyday"]),
    "trench": ("coat", "beige", "jackets", ["everyday"]),
    "jumper": ("knit", "dgblue", "knitwear", ["everyday"]),
}


def tn(tag):
    return TAGS[tag][LANGS.index(LANG)]


def cn(c):
    return r1.COLORS[c][1 + LANGS.index(LANG)]


def joined(tags):
    return ", ".join(tn(x) for x in tags)


def art(g):
    kind, col = GARMENTS[g][0], GARMENTS[g][1]
    hx = r1.COLORS[col][0]
    if r1.lum(hx) > 0.82:
        top, bot = "#e6e0d9", "#cfc5ba"
    else:
        top, bot = r1.mix(hx, "#ffffff", 0.82), r1.mix(hx, "#ffffff", 0.62)
    extra = '<path d="M50 14 L50 95" stroke="rgba(0,0,0,.18)" stroke-width="1.2"/>' if kind == "coat" else ""
    return (f'<div class="art" style="background:linear-gradient(180deg,{top},{bot})">'
            f'<svg viewBox="0 0 100 100" aria-hidden="true"><path d="{r1.SHAPES[kind]}" fill="{hx}" '
            f'stroke="rgba(0,0,0,.16)" stroke-width="1"/>{extra}</svg></div>')


def thumb(g, cls="thumb"):
    return f'<div class="{cls}">{art(g)}</div>'


# ---------------------------------------------------------------- CSS (round 1 + round 2 parts)
CSS2 = r"""
.pair .phones{flex-wrap:nowrap}
.ctxb{padding:2px 20px 10px}
.ctxb .ctx{font:600 12px/16px var(--fs);letter-spacing:.5px;text-transform:uppercase;color:var(--onv)}
.ctxb p{margin:4px 0 0;color:var(--onv);font-size:13.5px;line-height:19px}
.rhead{flex:none;padding:0 20px 10px;background:var(--surface)}
.rhead .top{display:flex;align-items:center;justify-content:space-between;gap:8px;min-height:40px}
.rhead .top b{font:600 20px/28px var(--fs);color:var(--on);white-space:nowrap}
.rhead .rv{display:flex;align-items:center;min-height:48px;padding:0 0 0 12px;font:600 13px/18px var(--fs);color:var(--primary);white-space:nowrap}
.rhead .prog{margin:0}
.gcard{margin:0 16px 12px;padding:12px 12px 12px;border-radius:16px;background:var(--lowest);border:1px solid var(--line)}
.ghead{display:flex;gap:12px;align-items:center}
.ghead .thumb{width:52px;height:60px;border-radius:12px}
.gid{flex:1;min-width:0}
.gid b{display:block;font:600 14px/20px var(--fs);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.gid span{display:block;font-size:12.5px;line-height:17px;color:var(--onv);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.glab{margin:10px 0 0;font:600 11px/16px var(--fs);letter-spacing:.5px;text-transform:uppercase;color:var(--onv)}
.trow{display:flex;align-items:center;gap:4px;min-height:48px;border-top:1px solid var(--line)}
.glab+.trow,.ghead+.trow{border-top:0}
.tv{flex:1;min-width:0;display:flex;flex-direction:column;align-items:flex-start;gap:1px}
.inst{font-size:12px;line-height:16px;color:var(--onv);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:100%}
.tv .chip{max-width:100%}
.chip .lbl2{overflow:hidden;text-overflow:ellipsis}
.chip.new{background:var(--successc);color:var(--onsuccessc);border-color:transparent}
.chip.off{background:transparent;color:var(--onv);border:1px dashed var(--outline)}.chip.off .lbl2{text-decoration:line-through}
.chip.okd{background:transparent;color:var(--on);border-color:var(--ov)}
.ico{width:48px;height:48px;flex:none;display:flex;align-items:center;justify-content:center}
.ico i{width:40px;height:40px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-style:normal;font-size:17px;font-weight:700}
.ico.rej i{border:1px solid var(--outline);color:var(--onv)}
.ico.acc i{background:var(--secc);color:var(--onsecc)}
.dec{display:flex;align-items:center;flex:none;font:600 12.5px/16px var(--fs);white-space:nowrap}
.dec .ok{color:var(--success)}.dec .no{color:var(--onv)}
.dec .u{min-height:48px;display:flex;align-items:center;padding:0 4px 0 12px;color:var(--primary)}
.gfoot{display:flex;gap:8px;margin-top:8px}
.pbtn{flex:1 1 0;min-width:0;min-height:48px;padding:6px 14px;border-radius:999px;display:flex;align-items:center;justify-content:center;
  font:600 14px/18px var(--fs);text-align:center;white-space:normal;border:1px solid transparent}
.pbtn.out{border-color:var(--outline);color:var(--primary)}
.pbtn.ton{background:var(--secc);color:var(--onsecc)}
.pbtn.fill{background:var(--primary);color:var(--onprimary)}
.pbtn.press{box-shadow:0 0 0 4px color-mix(in srgb,var(--primary) 22%,transparent)}
.pbtn.press{position:relative}
.pbtn.press::after{content:"";position:absolute;right:6px;top:50%;margin-top:-22px;width:44px;height:44px;border-radius:50%;background:color-mix(in srgb,var(--terra) 30%,transparent);border:2px solid var(--terra)}
.tchips{display:flex;flex-wrap:wrap;gap:0 8px;margin-top:2px}
.tchips .tc{display:flex;align-items:center;min-height:48px;max-width:100%}
.tchips .chip{height:34px;font-size:13.5px;padding:0 12px;max-width:100%}
.tchips .inst{align-self:center}
.gout{display:flex;align-items:center;gap:12px;margin:0 16px 12px;padding:10px 12px;border-radius:16px;background:var(--low);border:1px dashed var(--ov);min-height:64px}
.gout .thumb{width:36px;height:42px;border-radius:9px;opacity:.75}
.gout span{flex:1;min-width:0;font-size:13.5px;color:var(--onv)}
.gnote{font-size:12.5px;line-height:17px;color:var(--onv);margin-top:6px}
.rsec{display:flex;align-items:center;justify-content:space-between;margin:4px 16px 0;padding:0 4px 0 8px;min-height:48px;border-top:1px solid var(--line);
  font:600 12px/16px var(--fs);letter-spacing:.5px;text-transform:uppercase;color:var(--onv)}
.rsec span:last-child{display:flex;align-items:center;min-height:48px;padding:0 8px;color:var(--primary);text-transform:none;letter-spacing:0;font-size:13px}
.rrow{display:flex;align-items:center;gap:12px;margin:0 16px;padding:6px 0 6px 8px;border-top:1px solid var(--line);min-height:60px}
.rrow .thumb{width:36px;height:42px;border-radius:9px}
.rrow .tx{flex:1;min-width:0}
.rrow .tx b{display:block;font:500 13.5px/18px var(--fs);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.rrow .tx span{display:block;font-size:12px;line-height:16px;color:var(--onv);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.rrow .ta{flex:none;min-height:48px;display:flex;align-items:center;padding:0 8px;font:600 13px/18px var(--fs);color:var(--primary);white-space:nowrap}
.dlg{position:absolute;left:24px;right:24px;top:210px;background:var(--lowest);border-radius:28px;padding:24px 24px 14px;box-shadow:0 8px 28px rgba(0,0,0,.25)}
.dlg h3{margin:0 0 12px;font:500 22px/28px var(--fs);color:var(--on)}
.dlg p{margin:0;color:var(--onv);font-size:14px;line-height:20px}
.dlg .acts{display:flex;justify-content:flex-end;gap:4px;margin-top:20px;flex-wrap:wrap}
.dlg .acts span{min-height:48px;display:flex;align-items:center;padding:0 12px;font:600 14px/20px var(--fs);color:var(--primary);white-space:nowrap}
.bb2{position:absolute;left:0;right:0;bottom:0;background:var(--surface);border-top:1px solid var(--line);padding:10px 16px 14px}
.bb2 .btn{min-height:48px}
.dim{opacity:.38}
.fade-out{opacity:.55}
/* today's colour review, recreated from ColorReviewScreen.kt + ReviewScaffold.kt */
.cr .hdr{padding:16px 24px 0}
.cr .hdr .r{display:flex;justify-content:space-between;gap:8px;white-space:nowrap;font:500 20px/28px var(--fs)}
.cr .hdr .r span:first-child{color:var(--onv)}
.cr .hdr .bar{height:8px;border-radius:4px;background:var(--primary);margin-top:8px}
.cr .hint{margin:24px 0 0;font-size:14px;line-height:20px;color:var(--onv);position:relative}
.cr .hint.new{outline:1.5px dashed var(--terra);outline-offset:4px;border-radius:4px}
.cr .card{margin:20px 24px 0;display:flex;background:var(--cont);border:1px solid var(--ov);border-radius:12px;overflow:hidden}
.cr .card .ph{width:112px;flex:none}
.cr .card .cc{flex:1;padding:12px;display:flex;flex-direction:column;gap:10px;min-width:0}
.cr .strip{display:flex;justify-content:space-between;align-items:center;border:1px solid var(--ov);border-radius:12px;padding:8px 10px;background:var(--surface);color:var(--onv)}
.cr .dots{display:inline-flex;width:48px;position:relative;height:28px}
.cr .hb{display:flex;gap:8px}
.cr .hb span{flex:1;height:48px;border-radius:999px;display:flex;align-items:center;justify-content:center;font-weight:600;font-size:14px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;padding:0 8px}
.cr .hb .a{background:var(--primary);color:var(--onprimary)}.cr .hb .d{border:1px solid var(--outline);color:var(--primary)}
.newtag{position:absolute;right:0;top:-19px;font:700 10px/14px var(--fs);letter-spacing:.5px;color:var(--terra)}
/* frames page for per-phone PNGs */
.frames{position:relative}
.frames .fr{position:absolute;width:360px;height:740px}
"""

CSS = r1.CSS + CSS2


def theme_css():
    to_vars = lambda d: ";".join(f"--{k}:{v}" for k, v in d.items())
    return f".phone[data-theme=light]{{{to_vars(r1.LIGHT)}}}\n.phone[data-theme=dark]{{{to_vars(r1.DARK)}}}\n"


# ---------------------------------------------------------------- shared phone parts
def phone(theme, inner):
    return f'<div class="phone" data-theme="{theme}" lang="{LANG}">{r1.status()}{inner}</div>'


def appbar(title, back="←", action=None):
    act = f'<span class="abact">{e(action)}</span>' if action else '<span class="ib">⋮</span>'
    return f'<div class="ab"><span class="ib">{back}</span><h1>{e(title)}</h1>{act}</div>'


def ctx_block(ctx_key, hint_key, *ctx_args):
    return f'<div class="ctxb"><div class="ctx">{e(t(ctx_key, *ctx_args))}</div><p>{e(t(hint_key))}</p></div>'


def rhead(left, reviewed, total):
    pct = (total - left) / total * 100 if total else 100
    rv = f'<span class="rv">{e(t("review_reviewed_n", reviewed))} ▾</span>' if reviewed else '<span class="rv"></span>'
    return (f'<div class="rhead"><div class="top"><b>{e(t("review_left", left))}</b>{rv}</div>'
            f'<div class="prog"><i style="width:{pct:.0f}%"></i></div></div>')


def bulk_bar(n):
    return f'<div class="bb2"><div class="btn tonal">{e(t("review_accept_remaining", n))}</div></div>'


def snack(text, bottom=86):
    return f'<div class="snack" style="bottom:{bottom}px"><span>{e(text)}</span><b>{e(t("undo"))}</b></div>'


def identity(g, show_now=True):
    _, col, cat, occ = GARMENTS[g]
    line1 = f"{tn(cat)} · {cn(col)}"
    line2 = t("review_now", joined(occ) if occ else t("review_none")) if show_now else ""
    return (f'<div class="ghead">{thumb(g)}<div class="gid"><b>{e(line1)}</b>'
            + (f"<span>{e(line2)}</span>" if line2 else "") + "</div></div>")


def chip_new(tag, state="new"):
    glyph = "+" if state != "off" else "+"
    return f'<span class="chip {state}"><span class="g">{glyph}</span><span class="lbl2">{e(tn(tag))}</span></span>'


def chip_swap(new, old, state="new"):
    return (f'<span class="chip {state}"><span class="lbl2">{e(tn(new))}</span></span>'
            f'<span class="inst">{e(t("review_instead_of", tn(old)))}</span>')


def group_label(group):
    return f'<div class="glab">{e(t("review_group_category" if group == "category" else "review_group_occasions"))}</div>'


def outcome_line(g, text):
    return f'<div class="gout">{thumb(g)}<span>{e(text)}</span></div>'


def reviewed_rows(rows, open_=True, n=None):
    head = (f'<div class="rsec"><span>{e(t("review_section_reviewed", n if n is not None else len(rows)))}</span>'
            f'<span>{e(t("review_hide" if open_ else "review_show"))} {"▴" if open_ else "▾"}</span></div>')
    if not open_:
        return head
    body = ""
    for g, outcome in rows:
        _, col, cat, _ = GARMENTS[g]
        body += (f'<div class="rrow">{thumb(g)}<div class="tx"><b>{e(outcome)}</b><span>{e(tn(cat))} · {e(cn(col))}</span></div>'
                 f'<span class="ta">{e(t("review_again"))}</span></div>')
    return head + body


def color_review(theme, hint=True):
    """Today's ColorReviewScreen, unchanged, plus the one new description line."""
    def dots(a, b):
        sec = f'<span style="position:absolute;left:20px">{r1.sw(b, 28)}</span>' if b else ""
        return f'<span class="dots"><span style="position:absolute;left:0">{r1.sw(a, 28)}</span>{sec}</span>'

    def card(g, before, after):
        return (f'<div class="card"><div class="ph">{art(g)}</div><div class="cc">'
                f'<div class="strip">{dots(*before)}<span>→</span>{dots(*after)}</div>'
                f'<div class="hb"><span class="a">{e(t("accept"))}</span><span class="d">{e(t("review_card_discard"))}</span></div></div></div>')
    hint_html = (f'<p class="hint new"><span class="newtag">NEW</span>{e(t("color_review_tap_hint"))}</p>' if hint else "")
    inner = (appbar(t("color_review_title"))
             + '<div class="scroll cr">'
             + f'<div class="hdr"><div class="r"><span>{e(t("color_review_batch_progress"))}</span><span>{e(t("color_review_scanning_count", 120, 120))}</span></div>'
             + f'<div class="bar"></div>{hint_html}</div>'
             + card("trousers", ("bgreen", None), ("lybrown", None)) + card("coat", ("black", "white"), ("charcoal", "white"))
             + card("jumper", ("navy", None), ("dgblue", None)) + '</div>'
             + f'<div class="bb" style="padding:16px 24px 24px"><div class="btn filled" style="min-height:56px">✓ {e(t("color_review_accept_all", 6))}</div></div>')
    return phone(theme, inner)


COLOR_NOTES = ["Manu: “me gusta tal y como está”. Layout, dots, Accept / Discard and Accept all are today’s ColorReviewScreen, untouched.",
               "The only change is the <b>description line</b> under the Batch Progress header (dashed outline + NEW here only to point it out; not in the app). "
               "It names which dots are tappable (“on the right” = the proposal) and when (before accepting).",
               "Today the screen has no description at all (the tag review has one; the colour review only shows the progress row), so this is one new string: "
               "<code>color_review_tap_hint</code>, body-md, onSurfaceVariant, max 3 lines.",
               "TalkBack already reads each tappable dot as “Primary colour, proposed Charcoal” (<code>color_review_chip_description</code>)."]


def done_screen(theme, changed, kept, gs):
    minis = "".join(thumb(g) for g in gs)
    inner = (appbar(t("tag_review_title"), back="×", action=" ").replace('<span class="abact"> </span>', "")
             + f'<div class="donebox"><div class="okc">✓</div><h2>{e(t("review_complete_tags", changed))}</h2>'
             + f'<p>{e(t("review_complete_kept", kept))}</p>'
             + f'<div class="mini-list">{minis}</div><span class="ibtn tonal">✎ {e(t("review_complete_edit"))}</span></div>'
             + f'<div class="bb"><div class="btn filled">{e(t("done"))}</div></div>')
    return phone(theme, inner)


DONE_NOTES = ["Reached when the last pending garment leaves the list (0 left) and the scan has ended: decisions are written in one commit, as today (auto-commit).",
              "Static check in a tonal circle; no sparkle, no scale-in, no auto-dismiss (round 1).",
              "<b>Edit changes</b>: calm tonal button, static ✎, no ✦. Reopens the review with every garment in the Reviewed section, so any of them can be reviewed again.",
              "<b>Done</b> (filled, thumb zone) returns to Manage tags. Haptic: one CONFIRM when the commit lands, none here."]


# ---------------------------------------------------------------- finalist A: Tag by tag
def a_row(group_kind, item, state=None, single=False):
    """item: ("add", tag) or ("swap", new, old). state None|acc|rej."""
    if item[0] == "swap":
        _, new, old = item
        label = tn(new)
        val = chip_swap(new, old, {"acc": "okd", "rej": "off"}.get(state, "new"))
    else:
        label = tn(item[1])
        val = chip_new(item[1], {"acc": "okd", "rej": "off"}.get(state, "new"))
    t("review_cd_accept_tag", label), t("review_cd_reject_tag", label)  # content descriptions (listed in the table)
    if single:
        right = ""
    elif state == "acc":
        right = f'<span class="dec"><span class="ok">✓ {e(t("review_row_accepted"))}</span><span class="u">{e(t("undo"))}</span></span>'
    elif state == "rej":
        right = f'<span class="dec"><span class="no">✕ {e(t("review_row_rejected"))}</span><span class="u">{e(t("undo"))}</span></span>'
    else:
        right = '<span class="ico rej"><i>✕</i></span><span class="ico acc"><i>✓</i></span>'
    return f'<div class="trow"><div class="tv">{val}</div>{right}</div>'


def a_card(g, groups, states=None, press=None, show_now=True):
    """groups: list of (group_kind, [items]). states: {tag: acc|rej}."""
    states = states or {}
    items = [(gk, it) for gk, its in groups for it in its]
    single = len(items) == 1
    show_labels = len(groups) > 1 or single is False and False
    body = ""
    for gk, its in groups:
        if len(groups) > 1:
            body += group_label(gk)
        for it in its:
            body += a_row(gk, it, states.get(it[1]), single)
    pending = [it for _, it in items if it[1] not in states]
    if single:
        foot = (f'<div class="pbtn out">{e(t("review_reject"))}</div>'
                f'<div class="pbtn ton{" press" if press == "accept" else ""}">{e(t("accept"))}</div>')
    elif len(pending) == len(items):
        foot = (f'<div class="pbtn out">{e(t("review_reject_all"))}</div>'
                f'<div class="pbtn ton{" press" if press == "accept" else ""}">{e(t("review_accept_all_card"))}</div>')
    else:
        foot = (f'<div class="pbtn out">{e(t("review_reject_rest"))}</div>'
                f'<div class="pbtn ton">{e(t("review_accept_rest", len(pending)))}</div>')
    _ = show_labels
    return f'<div class="gcard">{identity(g, show_now)}{body}<div class="gfoot">{foot}</div></div>'


A_SINGLE = [("coat", [("occasion", [("add", "business")])]),
            ("shirt", [("occasion", [("add", "formal")])]),
            ("dress", [("occasion", [("add", "dressedup")])])]
A_MULTI = ("knit", [("category", [("swap", "knitwear", "tops")]),
                    ("occasion", [("add", "everyday"), ("add", "business"), ("add", "dressedup"), ("add", "statement")])])


# ---------------------------------------------------------------- finalist B: Pick, then decide
def b_card(g, groups, off=(), press=None, show_now=True):
    off = set(off)
    body = ""
    n_on = 0
    for gk, its in groups:
        if len(groups) > 1:
            body += group_label(gk)
        chips = ""
        for it in its:
            st = "off" if it[1] in off else "new"
            n_on += st == "new"
            if it[0] == "swap":
                chips += f'<span class="tc">{chip_swap(it[1], it[2], st).replace("</span><span class=" + chr(34) + "inst", "</span>&nbsp;<span class=" + chr(34) + "inst", 1)}</span>'
            else:
                chips += f'<span class="tc">{chip_new(it[1], st)}</span>'
        body += f'<div class="tchips">{chips}</div>'
    if n_on == 0:
        foot = f'<div class="pbtn ton">{e(t("review_keep_as_is"))}</div>'
        body += f'<div class="gnote">{e(t("review_none_selected"))}</div>'
    else:
        foot = (f'<div class="pbtn out">{e(t("review_reject"))}</div>'
                f'<div class="pbtn ton{" press" if press == "accept" else ""}">{e(t("review_accept_n", n_on))}</div>')
    return f'<div class="gcard">{identity(g, show_now)}{body}<div class="gfoot">{foot}</div></div>'


# ---------------------------------------------------------------- screens (shared skeleton, per-finalist card)
def lang(code, fn):
    def run(th):
        global LANG
        prev, LANG = LANG, code
        try:
            return fn(th)
        finally:
            LANG = prev
    return run


def screens(card, hint_key, slug):
    """card(g, groups, **kw) renders one garment; the rest of each state is shared so the finalists compare 1:1."""
    A = card is a_card
    out = []

    def list_screen(th, cards, left, reviewed, total, ctx="review_ctx_restored_occ", ctx_args=(), with_ctx=True, extra="", bulk=True):
        return phone(th, appbar(t("tag_review_title")) + rhead(left, reviewed, total) + '<div class="scroll">'
                     + (ctx_block(ctx, hint_key, *ctx_args) if with_ctx else '<div style="height:4px"></div>')
                     + "".join(cards) + '</div>' + (bulk_bar(left) if bulk else "") + extra)

    # 1 single proposal
    out.append(("Single proposal per garment", lambda th: list_screen(th, [card(g, gr) for g, gr in A_SINGLE], 37, 0, 37,
                ctx="review_ctx_restored_occ"),
                (["Most common case: one suggestion per garment. The card is <b>photo + what we know</b> (category · main colour, then the garment’s current occasions). No name: the model has <code>ClothingItem.name</code> but nothing in the app sets it.",
                  "The suggestion is a chip with “+” (will be added). No before/after boxes: the current state is one quiet “Now: …” line.",
                  "One suggestion → no per-row buttons; the card footer is the decision: <b>Reject</b> (outlined) and <b>Accept</b> (tonal), equal width, 48 dp tall, at the bottom of the card.",
                  "Header: <b>37 left</b> + 4 dp bar; stays pinned while the context line scrolls away. Bottom bar: <b>Accept all remaining (37)</b>, tonal, with a confirmation."]
                 if A else
                 ["Most common case: one suggestion per garment. Card = <b>photo + what we know</b> (category · main colour, then current occasions). No name: <code>ClothingItem.name</code> exists in the model but has no UI.",
                  "The suggestion is a selected chip with “+”. Footer: <b>Reject</b> (outlined) and <b>Accept 1</b> (tonal), equal width, 48 dp. The count on Accept always says how many tags the tap adds.",
                  "Header: <b>37 left</b> + 4 dp bar, pinned while the context line scrolls away. Bottom bar: <b>Accept all remaining (37)</b>, tonal, with a confirmation."])))

    # 2 multi (restore default tags, 2 groups)
    g, gr = A_MULTI
    out.append(("4–5 proposals across 2 groups (restore default tags)", lambda th: list_screen(th, [card(g, gr), card(*A_SINGLE[0])], 37, 0, 37,
                ctx="review_ctx_restored_all"),
                (["After restoring default tags, one garment gets <b>5 suggestions in 2 groups</b>: Category (Knitwear instead of Tops) and 4 Occasions.",
                  "Each suggestion is a 48 dp row: chip on the left, <b>✕ Reject</b> and <b>✓ Accept</b> icon buttons on the right (40 dp visual, 48 dp target; TalkBack: “Accept Business”). Accept is the right-most, closest to the thumb.",
                  "Card footer = the whole garment: <b>Reject all</b> / <b>Accept all</b>. A card leaves the list only when every row is decided.",
                  "Category is a swap (Knitwear <i>instead of</i> Tops): one decision, the new value first and the old one as a quiet line, never two boxes."]
                 if A else
                 ["After restoring default tags, one garment gets <b>5 suggestions in 2 groups</b>: Category (Knitwear instead of Tops) and 4 Occasions.",
                  "All suggestions start selected. <b>Tap a chip to leave it out</b> (it turns dashed + struck through, the “+” stays so it reads “not added”). Tap again to bring it back. Chips are 34 dp visual in a 48 dp row.",
                  "One decision per garment: <b>Accept 5</b> adds the selected ones, <b>Reject</b> adds none. Accept all = one tap, Reject all = one tap, pick = tap chips then Accept.",
                  "Category is a swap (Knitwear <i>instead of</i> Tops): new value first, old one as a quiet line, never two boxes."])))

    # 3 Spanish
    out.append(("Same garment in Spanish", lang("es", lambda th: list_screen(th, [card(g, gr), card(*A_SINGLE[0])], 37, 0, 37,
                ctx="review_ctx_restored_all")),
                ["“Partes de arriba”, “Arreglado”, “Aceptar las 37 restantes”: chips never wrap inside (nowrap); rows/chip lines wrap instead.",
                 "Header “Quedan 37” and “Revisadas” fit the pinned row."]))

    # 4 long strings (DE + custom tag)
    long_groups = [("category", [("swap", "knitwear", "tops")]), ("occasion", [("add", "weekend"), ("add", "dressedup")])]
    out.append(("Long strings (German + a custom occasion)", lang("de", lambda th: list_screen(th, [card("jumper", long_groups), card("trench", [("occasion", [("add", "weekend")])])], 37, 0, 37,
                ctx="review_ctx_new_occ", ctx_args=(tn("weekend"),))),
                ([
                    "“Wochenendausflug mit Freunden” (user-made occasion) is the worst case: the chip takes the full text column and ellipsizes only if it would exceed the row; the ✕ / ✓ buttons never move or shrink (flex:none).",
                    "Footer buttons share the width and wrap their label to 2 lines if needed (white-space normal), e.g. “Alle restlichen 37 akzeptieren”."]
                  if A else
                  ["“Wochenendausflug mit Freunden” (user-made occasion) gets its own chip line; chips are nowrap and ellipsize only beyond the card width.",
                   "Footer buttons share the width and wrap to 2 lines if needed; the bottom bar “Alle restlichen 37 akzeptieren” fits on one."])))

    # 5 partially decided
    if A:
        partial = lambda th: list_screen(th, [a_card(g, gr, states={"knitwear": "acc", "business": "acc", "statement": "rej"}), a_card(*A_SINGLE[1])],
                                         37, 0, 37, with_ctx=False)
        pnotes = ["Rows decided one by one stay in place and change state: <b>✓ Accepted</b> (plain chip) or <b>✕ Rejected</b> (dashed, struck through), each with its own <b>Undo</b> (48 dp).",
                  "The garment is still pending (header still says 37 left) until its last row is decided.",
                  "The footer follows: <b>Reject the rest</b> / <b>Accept the rest (2)</b> close the card in one tap."]
    else:
        partial = lambda th: list_screen(th, [b_card(g, gr, off={"statement", "dressedup"}), b_card(*A_SINGLE[1])], 37, 0, 37, with_ctx=False)
        pnotes = ["The user left out Dressed-up and Statement: those chips are dashed + struck through; the button now says <b>Accept 3</b>.",
                  "Nothing is decided yet (the garment stays in the list) until Accept or Reject; this is a selection, not a decision, so there is no per-chip undo: tap the chip again.",
                  "Selections survive scrolling and rotation (saved state per garment)."]
    out.append(("Partially decided garment", partial, pnotes))

    # 6 empty after reject
    if A:
        empty = lambda th: phone(th, appbar(t("tag_review_title")) + rhead(36, 1, 37) + '<div class="scroll"><div style="height:4px"></div>'
                                 + outcome_line("dress", t("review_outcome_kept")) + a_card(*A_SINGLE[1]) + a_card("blazer", [("occasion", [("add", "formal"), ("add", "dressedup")])]) + '</div>'
                                 + bulk_bar(36) + snack(t("review_snack_kept")))
        enotes = ["The user rejected every row of a garment one by one. As soon as the last row is rejected the card is decided: it turns into a one-line outcome (<b>Kept as it was</b>) and leaves.",
                  "Frame shown mid-exit: outcome line (dashed, muted, 64 dp) before it collapses. Header already says 36 left.",
                  "Snackbar: “Kept as it was · Undo”. Undo brings the card back with its rows as they were before the last tap."]
    else:
        empty = lambda th: list_screen(th, [b_card("blazer", [("occasion", [("add", "formal"), ("add", "dressedup")])], off={"formal", "dressedup"}), b_card(*A_SINGLE[1])],
                                       37, 0, 37, with_ctx=False)
        enotes = ["The user left out every chip. Nothing would be added, so the two buttons collapse into one: <b>Keep as it is</b> (tonal), with a quiet line saying why.",
                  "Tapping it is the same as Reject: the garment leaves the list as “Kept as it was” with Undo. Tapping a chip back brings Reject / Accept N back."]
    out.append(("Garment empty after rejecting everything", empty, enotes))

    # 7/8 shrink frames
    c1 = card(*A_SINGLE[0], press="accept")
    rest = [card(*A_SINGLE[1]), card(*A_SINGLE[2]), card("jeans", [("occasion", [("add", "business")])])]

    def frame1(th):
        return list_screen(th, [c1] + rest[:2], 37, 0, 37, with_ctx=False)

    def frame2(th):
        return list_screen(th, rest, 36, 1, 37, with_ctx=False, extra=snack(t("review_snack_accepted", tn("business"))))
    out.append(("List shrinking · frame 1 (tap Accept)", frame1,
                ["Frame 1: the thumb taps <b>Accept</b> on the first card (pressed state).",
                 "Motion (calm): ① 0–150 ms the card content cross-fades to the one-line outcome (“Adds Business”); ② 150–400 ms the card height collapses to 0 and fades, the cards below slide up (FastOutSlowIn, no bounce, no overshoot). Haptic: one light tick.",
                 "Reduced motion (Remove animations / animator scale 0): no slide and no morph; the card fades out in 100 ms and the list settles at once.",
                 "Accessibility focus moves to the next card’s identity line; TalkBack announces “Accepted. 36 left.”"]))
    out.append(("List shrinking · frame 2 (after) + Undo snackbar", frame2,
                ["Frame 2: the card is gone, the next garment sits where the thumb is, <b>36 left</b>, the bar advanced, “1 reviewed” appears in the header.",
                 "Snackbar (inverse surface, 4 s, above the bottom bar): “Accepted: Business · <b>Undo</b>”. Undo puts the card back at its old position (reverse motion) and the counter back to 37.",
                 "Only the last action has a snackbar; earlier ones are recovered from the <b>Reviewed</b> section."]))

    # 9 bulk confirm
    def bulk(th):
        base = list_screen(th, [card(*A_SINGLE[1]), card(*A_SINGLE[2]), card("jeans", [("occasion", [("add", "business")])])], 36, 1, 37, with_ctx=False)
        dlg = (f'<div class="scrim"></div><div class="dlg"><h3>{e(t("review_bulk_title", 36))}</h3><p>{e(t("review_bulk_body", 52, 36))}</p>'
               f'<div class="acts"><span>{e(t("cancel"))}</span><span>{e(t("review_accept_n", 36))}</span></div></div>')
        return base[:-6] + dlg + "</div>"
    out.append(("Bulk: Accept all remaining (confirmation)", bulk,
                ["<b>Accept all remaining (36)</b> opens a confirmation with the real impact: 52 tags on 36 garments.",
                 ("Takes every pending row of every garment, including the rows of partially decided ones; decided rows are kept as decided." if A else
                  "Takes the current selection of every pending garment (chips the user left out stay out)."),
                 "After confirming, all cards leave together (one collapse, no stagger) and the completion screen follows; snackbar “36 garments accepted · Undo” on the completion screen for 4 s.",
                 "No bulk Reject: rejecting everything is “Back → Discard changes?” (existing dialog), which also undoes the tag change itself."]))

    # 10 reviewed section (recovery)
    def recovery(th):
        rows = [("dress", t("review_outcome_kept")), ("coat", t("review_outcome_added", tn("business"))),
                ("knit", t("review_outcome_added", joined(["knitwear", "everyday", "business"]))),
                ("shirt", t("review_outcome_added", tn("formal")))]
        return phone(th, appbar(t("tag_review_title")) + rhead(2, 35, 37) + '<div class="scroll"><div style="margin-top:-150px"></div>'
                     + card("jeans", [("occasion", [("add", "business")])]) + card("blazer", [("occasion", [("add", "formal"), ("add", "dressedup")])])
                     + reviewed_rows(rows, True, 35) + '</div>' + bulk_bar(2))
    out.append(("Recovery: Reviewed section", recovery,
                ["<b>Decision: Undo snackbar for the last action + a collapsed “Reviewed · N” section at the end of the list.</b>",
                 "Why not Undo only: with 100+ garments a mistake is often noticed minutes later, after the 4 s snackbar is gone; the only way back would be leaving the review (which discards everything).",
                 "Why at the end and collapsed: pending garments stay on top and the section costs one 48 dp row, so it never competes with what is still to do. “35 reviewed ▾” in the header jumps to it.",
                 "Each row: outcome first (“Adds Knitwear, Everyday, Business” / “Kept as it was”), category · colour under it, <b>Review again</b> puts the garment back on top of the pending list with its suggestions reset."]))

    # 11 completion
    out.append(("0 left · completion", lambda th: done_screen(th, 31, 6, ["coat", "knit", "shirt", "blazer", "jeans"]), DONE_NOTES))

    # 12 colour review
    out.append(("Colour review · today’s screen + new hint", lambda th: color_review(th), COLOR_NOTES))
    return out


# ---------------------------------------------------------------- explored directions (sketches for the index)
def sk_flat(th):
    rows = ""
    for g, tag in (("knit", "everyday"), ("knit", "business"), ("knit", "dressedup"), ("coat", "business"), ("shirt", "formal"), ("dress", "dressedup")):
        rows += (f'<div class="trow" style="margin:0 16px;padding-left:4px">{thumb(g).replace("thumb", "thumb sm")}'
                 f'<div class="tv" style="margin-left:8px">{chip_new(tag)}</div><span class="ico rej"><i>✕</i></span><span class="ico acc"><i>✓</i></span></div>')
    return phone(th, appbar(t("tag_review_title")) + rhead(58, 0, 58) + '<div class="scroll">'
                 + rows.replace('class="thumb sm"', 'class="thumb" style="width:36px;height:42px;border-radius:9px"') + '</div>' + bulk_bar(58))


def sk_focus(th):
    g, gr = A_MULTI
    body = ""
    for gk, its in gr:
        body += group_label(gk) + '<div class="tchips">' + "".join(
            f'<span class="tc">{chip_new(it[1]) if it[0] == "add" else chip_swap(it[1], it[2])}</span>' for it in its) + '</div>'
    return phone(th, appbar(t("tag_review_title")) + rhead(37, 0, 37) + '<div class="scroll">'
                 + f'<div class="fphoto" style="height:220px">{art(g)}</div><div style="padding:8px 20px">{identity(g).replace("<div class=" + chr(34) + "thumb" + chr(34) + ">", "<div class=" + chr(34) + "thumb" + chr(34) + " style=" + chr(34) + "display:none" + chr(34) + ">")}{body}</div></div>'
                 + f'<div class="bb"><div class="btnrow"><div class="pbtn out">{e(t("review_reject"))}</div><div class="pbtn fill">{e(t("review_accept_n", 5))}</div></div></div>')


def sk_compact(th):
    rows = ""
    for g, n in (("knit", 5), ("coat", 1), ("shirt", 1), ("blazer", 2), ("dress", 1), ("jeans", 1)):
        _, col, cat, _ = GARMENTS[g]
        rows += (f'<div class="rrow">{thumb(g)}<div class="tx"><b>+{n} · {e(tn(cat))}</b><span>{e(cn(col))}</span></div>'
                 f'<span class="ico rej"><i>✕</i></span><span class="ico acc"><i>✓</i></span></div>')
    return phone(th, appbar(t("tag_review_title")) + rhead(37, 0, 37) + '<div class="scroll">' + rows + '</div>' + bulk_bar(37))


def sk_bytag(th):
    def grid(gs, off=()):
        return '<div style="display:grid;grid-template-columns:repeat(5,1fr);gap:6px;margin-top:8px">' + "".join(
            f'<div class="thumb" style="width:auto;height:58px;{"opacity:.35" if g in off else ""}">{art(g)}</div>' for g in gs) + '</div>'
    sec = lambda tag, gs, off=(): (f'<div class="gcard"><div style="display:flex;justify-content:space-between;align-items:center">{chip_new(tag)}'
                                    f'<span class="inst">{len(gs) - len(off)} / {len(gs)}</span></div>{grid(gs, off)}'
                                    f'<div class="gfoot"><div class="pbtn out">{e(t("review_reject"))}</div><div class="pbtn ton">{e(t("review_accept_n", len(gs) - len(off)))}</div></div></div>')
    return phone(th, appbar(t("tag_review_title")) + rhead(4, 0, 4) + '<div class="scroll"><div style="height:4px"></div>'
                 + sec("business", ["coat", "blazer", "trousers", "shirt", "jeans", "trench", "knit", "jumper", "dress", "skirt"], off=("dress",))
                 + sec("dressedup", ["dress", "skirt", "blazer", "knit", "shirt"]) + '</div>' + bulk_bar(4))


DIRECTIONS = [
    ("A · Tag by tag", "fin", "Card per garment; one row per suggestion with ✕ / ✓; card footer Reject all / Accept all.",
     "Most literal answer to “pick individually”: every tag has its own final decision and per-row Undo. Cost: up to 2×N+2 buttons in a card (12 for the restore case), more states (partial cards linger), slower scanning. Errors are cheap (per-row Undo).", "a"),
    ("C · Pick, then decide", "fin", "Card per garment; suggestions are selected chips you can deselect; one Reject / Accept N per garment.",
     "One decision per garment, so every tap on the footer shrinks the list: the rhythm Manu described. Accept all = 1 tap, Reject all = 1 tap, pick = chip taps + 1. Risk: chip toggles look like round 1’s checkmark, but they are only a selection; the count on “Accept 3” states the result.", "b"),
    ("B · One row per tag", "drop", "Flat list, one row per (garment, tag) with ✕ / ✓; the photo repeats on each row.",
     "Dropped: the restore case triples the list (37 garments → 110 rows), the same photo repeats and the garment loses its context (you judge “Business” without seeing its other suggestions). Fast per row, slow overall.", sk_flat),
    ("D · Focus queue", "drop", "One garment per screen (round 1 Focus) with selectable chips and Reject / Accept N at the bottom.",
     "Dropped as the main view: no overview, 100+ garments = 100+ screens, and the list-shrinks feedback Manu asked for disappears. Kept as an idea: tapping a photo in C could open this view.", sk_focus),
    ("E · Two-step compact rows", "drop", "Dense rows (“+5 · Tops”) with ✕ / ✓; tap a row to expand and see / pick the tags.",
     "Dropped: fastest to scan, but the summary hides what is being accepted (“+5” of what?), which invites blind accepts — exactly the error cost we want low. Expanding is an extra step for every multi-tag garment.", sk_compact),
    ("F · Grouped by tag", "drop", "One card per suggested tag (“+ Business · 9 / 10”) with a photo grid; tap photos to leave them out.",
     "Interesting for 100+ (one decision per tag), but it breaks the garment as the unit: a category swap and its occasions are split across cards, tiny photos are hard to judge, and “37 left” no longer maps to garments. Worth revisiting for a future “bulk tag” tool.", sk_bytag),
]


# ---------------------------------------------------------------- pages
def head(title):
    return (f'<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width">'
            f'<title>{e(title)}</title><style>{CSS}{theme_css()}</style></head><body><div class="page">')


TAIL = "</div></body></html>"


def strings_table(keys):
    rows = "".join(f"<tr><td>{e(k)}</td><td>{e(S[k][0])}</td><td>{e(S[k][1])}</td><td>{e(S[k][2])}</td></tr>" for k in keys)
    return f'<table class="str"><tr><th>key</th><th>EN</th><th>ES</th><th>DE</th></tr>{rows}</table>'


def render_pair(fn):
    global USED, LANG
    USED, LANG = [], "en"
    light, dark = fn("light"), fn("dark")
    return light, dark, list(USED)


def pair_block(title, fn, notes):
    light, dark, keys = render_pair(fn)
    caption = "".join(f"<li>{n}</li>" for n in notes)
    return (f'<div class="pair"><div class="lbl">{e(title)} — light · dark (proposed)</div>'
            f'<div class="phones">{light}{dark}</div>'
            f'<div class="cap"><b class="t">{e(title)}</b><ul>{caption}</ul></div>'
            f'<div class="lbl">Strings used on this screen</div>{strings_table(keys)}</div>')


FINALISTS = [
    ("pick-then-decide", "Finalist 1 · Pick, then decide (recommended)",
     "One card per garment. Every suggestion starts selected; tap a tag to leave it out, then one explicit Reject or Accept N decides the garment and it leaves the list.",
     lambda: screens(b_card, "review_hint_pick", "b")),
    ("tag-by-tag", "Finalist 2 · Tag by tag",
     "One card per garment. Every suggestion is a row with its own ✕ Reject / ✓ Accept and Undo; the card footer rejects or accepts all; the garment leaves when every row is decided.",
     lambda: screens(a_card, "review_hint_per_tag", "a")),
]

DARK_NOTE = ('<div class="note"><b>Tokens.</b> Light = Earth &amp; Steel from <code>Color.kt</code> / <code>docs/ux/earth_steel/DESIGN.md</code> '
             '(espresso #332421 primary, champagne #f1dcc9 secondary container, terracotta accents). Robia ships light only; the dark phones use '
             'round 1’s derived dark proposal of the same palette (imported from <code>../review-redesign/build.py</code>, unchanged). '
             'Fonts render with the fallback stack (Inter/Roboto not installed on the build machine; it is wider, so what fits here fits on device).</div>')


def finalist_page(slug, title, pitch, scr):
    blocks = "".join(pair_block(name, fn, notes) for name, fn, notes in scr())
    return (head(f"Robia · review redesign round 2 · {title}")
            + f'<p><a href="index.html">← Round 2 overview</a></p><h1>{e(title)}</h1><p class="lead">{e(pitch)}</p>'
            + DARK_NOTE + f'<div class="pairs">{blocks}</div>' + TAIL)


DATA_NOTES = """
<table class="cmp">
<tr><th>What</th><th>Where in the code</th><th>What it means for the design</th></tr>
<tr><td>Garment identity</td><td><code>ClothingItem</code>: <code>photoUri</code>, <code>tags</code> (category 0..1, occasions 0..N, seasons, location), <code>colorMetrics</code> (primary / secondary palette colour name + hex), <code>notes</code>. <code>name</code> exists but Add/Edit has no field for it (only Notes).</td>
<td>No name anywhere. Identify by <b>photo</b> + “<b>category · main colour</b>” (e.g. “Tops · Camel”) + “Now: current occasions”. Notes are free text, often empty or private: not shown. No photo → today’s Style icon on the colour-tinted backdrop.</td></tr>
<tr><td>One review session</td><td><code>TagChangeSet(operation, categoryId, touchedTagIds, beforeTags, afterTags)</code>; reviewed groups are only <code>category</code> and <code>occasion</code> (<code>ReviewedTagKinds</code>). Restore defaults is per group (<code>restoredTagChangeSet(…, categoryId, …)</code>).</td>
<td>Today a session is <b>one group</b>. Seasons, location and custom groups are never reviewed; there is no “style” group. The 2-group mockups (Category + Occasions) need a future “restore all default tags” or merged queued reviews — the layout supports it with a group label per section (see question 3).</td></tr>
<tr><td>Proposal per garment</td><td><code>ReviewProposal(before: Set, proposed: Set, kind: Suggested | ChooseManually)</code> — the whole tag set, not per tag.</td>
<td>The per-tag diff is <code>proposed − before</code> (adds) and <code>before − proposed</code> (removals). Per-tag decisions are new state on top: <code>Map&lt;garmentId, Set&lt;tagId&gt;&gt;</code> of accepted adds; commit writes <code>before + accepted − removals</code>.</td></tr>
<tr><td>How many per garment</td><td>Added/restored <b>occasion</b>: <code>selectOccasions</code> adds 1..N touched occasions, never removes. <b>Category</b> added/restored: one swap (−old +new). Deleted occasion: one removal. Deleted category: swap to the best remaining, or ChooseManually (no image vector).</td>
<td>Occasions: 1..N independent “+ tag” items (restoring 6 default occasions can propose 4 at once). Category: always <b>one swap item</b> (“Knitwear instead of Tops”), accepted or rejected as a unit. ChooseManually keeps today’s “Choose” picker inside the card (not drawn here; unchanged).</td></tr>
<tr><td>Commit &amp; undo today</td><td>Nothing is written until <code>onCommit</code>; it auto-commits when every card is closed (<code>shouldAutoCommit</code>). Back → “Discard changes?” undoes the tag change itself.</td>
<td>Kept: decisions live in memory, “0 left” triggers the commit and the completion screen. Leaving halfway is question 2.</td></tr>
</table>"""


def index_page():
    global LANG, USED
    LANG, USED = "en", []
    sk = ""
    fin_first = {"a": lambda: screens(a_card, "review_hint_per_tag", "a")[1][1]("light"),
                 "b": lambda: screens(b_card, "review_hint_pick", "b")[1][1]("light")}
    for name, kind, pitch, critique, fn in DIRECTIONS:
        verdict = {"fin": "finalist", "drop": "dropped"}[kind]
        mini = fin_first[fn]() if isinstance(fn, str) else fn("light")
        sk += (f'<div class="sketch"><div class="mini">{mini}</div><span class="verdict {kind}">{verdict}</span><h3>{e(name)}</h3>'
               f'<p><b>{e(pitch)}</b></p><p>{e(critique)}</p></div>')
    side = ""
    for state_idx, label in ((1, "4–5 proposals, 2 groups"), (4, "Partially decided"), (7, "After Accept + Undo")):
        side += f'<h2 class="sec">Side by side · {e(label)}</h2><div class="side">'
        for slug, title, pitch, scr in FINALISTS:
            LANG = "en"
            side += f'<div class="col"><h3><a href="{slug}.html">{e(title)}</a></h3>{scr()[state_idx][1]("light")}</div>'
        side += "</div>"
    cmp_rows = [
        ("Taps, 1 suggestion", ("good", "1 (Accept / Reject)"), ("good", "1 (Accept / Reject)")),
        ("Taps, 5 suggestions, take all / none", ("good", "1"), ("good", "1 (Accept all / Reject all)")),
        ("Taps, 5 suggestions, take 3", ("good", "2 chip taps + Accept 3"), ("mid", "5 row taps, or 2 ✕ + Accept the rest")),
        ("Buttons per 5-tag card", ("good", "2 + 5 chips"), ("bad", "12 (10 icons + 2)")),
        ("List shrinks on every decision", ("good", "every footer tap removes a garment"), ("mid", "only the last row of a card removes it")),
        ("Error cost", ("good", "chip toggles are free until Accept; Undo + Reviewed"), ("good", "per-row Undo + snackbar + Reviewed")),
        ("Scanning 100+ garments", ("good", "chips read as one line per group"), ("mid", "one 48 dp row per tag: tall cards")),
        ("“Not a checkmark” (Manu, point 4)", ("mid", "chip toggle is a selection; decision is explicit"), ("good", "every tag has explicit ✕ / ✓")),
        ("Long ES / DE strings", ("good", "chips wrap to new lines"), ("good", "chip column ellipsizes; buttons fixed")),
        ("Build cost (Compose)", ("good", "FilterChip + card footer; one state set"), ("mid", "row state machine + per-row undo")),
    ]
    cmp = "<table class='cmp'><tr><th></th><th>1 · Pick, then decide</th><th>2 · Tag by tag</th></tr>"
    for crit, *cells in cmp_rows:
        cmp += f"<tr><th>{e(crit)}</th>" + "".join(
            f"<td><span class='rate {c}'>{ {'good': 'Good', 'mid': 'OK', 'bad': 'Weak'}[c] }</span> {e(txt)}</td>" for c, txt in cells) + "</tr>"
    cmp += "</table>"
    LANG, USED = "en", []
    color_l, color_d = color_review("light"), color_review("dark")
    ckeys = list(USED)
    motion = """<ol>
<li><b>Accept / Reject / Keep as is / last row decided:</b> 0–150 ms the card content cross-fades to a one-line outcome (“Adds Business” / “Kept as it was”); 150–400 ms height → 0 + fade, cards below slide up. FastOutSlowIn, no overshoot, no sparkle. One light haptic tick.</li>
<li><b>Reduced motion</b> (system “Remove animations”, animator duration scale 0): no morph, no slide; 100 ms fade-out, list settles at once. Focus moves to the next card; TalkBack: “Accepted. 36 left.”</li>
<li><b>Undo</b>: reverse of 1 (card re-expands at its old index), counter back. Snackbar lives 4 s, one at a time (a new decision replaces it).</li>
<li><b>Accept all remaining</b>: confirmation dialog → all pending cards fade together (no stagger) → completion screen with an Undo snackbar.</li>
<li><b>Header</b>: “N left” changes with a 150 ms number cross-fade; the bar animates its width (300 ms, as today).</li>
</ol>"""
    return (head("Robia · garment review redesign · round 2")
            + '<h1>Garment review: round 2</h1>'
            + '<p class="lead">Manu chose direction A “Checklist” with four corrections: keep the colour review as it is (only the hint changes), no garment names, '
              '1..N suggested tags per garment, and an interactive review where Accept / Reject are explicit and reviewed garments leave the list. '
              'Round 1 stays in <a href="../review-redesign/index.html">../review-redesign/</a>.</p>'
            + '<div class="reco"><p><b>Recommendation: Finalist 1 · Pick, then decide.</b></p>'
              '<p>The unit of work is the garment, and each garment gets exactly one explicit decision: <b>Reject</b> or <b>Accept N</b>. Every decision makes the list shorter, '
              'so “37 left” always counts what remains. Multi-tag garments stay one tap for all or none; picking is tapping the chips you don’t want, and the button '
              'counts what will be added. It has half the controls of Tag by tag on the restore-defaults case (7 vs 12) and is cheaper to build.</p>'
              '<p>Shared by both finalists: photo + “category · colour” identity (no name), new-value-first chips with a quiet “Now:” / “instead of” line, pinned “N left” header, '
              'Undo snackbar for the last action, a collapsed <b>Reviewed</b> section to recover older decisions, “Accept all remaining” with confirmation, the calm completion screen, '
              'and the colour review untouched except for its new hint.</p></div>'
            + DARK_NOTE
            + f'<h2 class="sec">1 · Data per review item (from the code)</h2>{DATA_NOTES}'
            + f'<h2 class="sec">2 · Directions explored</h2><p class="secd">Six layouts for the tag review that meet points 2–4. Two go forward. '
              f'Minis are the 4–5-suggestion case where possible.</p><div class="sketches">{sk}</div>'
            + side
            + f'<h2 class="sec">3 · Comparison</h2>{cmp}'
            + f'<h2 class="sec">4 · Motion</h2><div class="diag">{motion}</div>'
            + '<h2 class="sec">5 · Recovery: Undo + Reviewed section</h2><p class="secd">Undo only for the last action (snackbar) plus a collapsed “Reviewed · N” section at the end of the list. '
              'Undo alone fails at 100+ garments: mistakes are noticed after the snackbar is gone, and the only exit would be discarding the whole review. The section is collapsed and below '
              'the pending list, so it never competes with what is left; each row says the outcome first and has “Review again”. After the commit, “Edit changes” on the completion screen reopens the same list.</p>'
            + f'<h2 class="sec">6 · Colour review: unchanged + hint</h2><div class="pairs"><div class="pair"><div class="lbl">Colour review — light · dark</div>'
              f'<div class="phones">{color_l}{color_d}</div><div class="cap"><b class="t">Only the description changes</b><ul>'
            + "".join(f"<li>{n}</li>" for n in COLOR_NOTES) + f'</ul></div><div class="lbl">Strings</div>{strings_table(ckeys)}</div></div>'
            + '<h2 class="sec">7 · Open questions for Manu</h2><div class="diag"><ol>'
              '<li><b>Which finalist?</b> (a) Pick, then decide — one Reject / Accept N per garment <i>(recommended)</i>; (b) Tag by tag — ✕ / ✓ on every suggestion.</li>'
              '<li><b>Leaving halfway through 100+ garments.</b> (a) As today: Back → “Discard changes?” throws away every decision and the tag change; '
              '(b) every garment that leaves the list is saved at once, Back keeps them and the rest stay unchanged <i>(recommended: no lost work)</i>; '
              '(c) like (b) plus “Continue review” in Manage tags for the rest.</li>'
              '<li><b>Several groups in one review.</b> Today restore defaults works one group at a time, so one review shows one group. '
              '(a) Keep it that way (the cards just drop the group labels); (b) add “Restore all default tags”, reviewing Category + Occasions together as in these mockups; '
              '(c) merge reviews started back to back into one list.</li></ol></div>'
            + '<h2 class="sec">Pages</h2><ul>' + "".join(f'<li><a href="{s}.html">{e(ti)}</a> — 12 states, light + dark, string tables</li>' for s, ti, _, _ in FINALISTS) + '</ul>'
            + '<p class="secd">Built by <code>build.py</code>. PNGs: <code>&lt;page&gt;.png</code> (or <code>&lt;page&gt;-pN.png</code> slices when tall) and one PNG per phone: '
              '<code>&lt;page&gt;-NN-&lt;state&gt;-light|dark.png</code>.</p>'
            + TAIL)


# ---------------------------------------------------------------- screenshots
def chrome():
    return shutil.which("chromium") or shutil.which("chromium-browser") or shutil.which("google-chrome")


def shoot(url, png, w, h):
    subprocess.run([chrome(), "--headless", "--disable-gpu", "--no-sandbox", "--hide-scrollbars", "--force-device-scale-factor=1",
                    f"--screenshot={png}", f"--window-size={w},{h}", url],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=240)


def page_shot(page, slice_h=2400):
    from PIL import Image, ImageChops
    png = page.with_suffix(".png")
    for old in OUT.glob(page.stem + "-p*.png"):
        old.unlink()
    shoot(page.as_uri(), png, 1640, 16000)
    im = Image.open(png).convert("RGB")
    bg = Image.new("RGB", im.size, im.getpixel((im.width - 1, im.height - 1)))
    box = ImageChops.difference(im, bg).getbbox()
    im = im.crop((0, 0, im.width, min(im.height, box[3] + 40))) if box else im
    if im.height <= slice_h * 1.25:
        im.save(png)
        print("shot", png.name)
        return
    png.unlink()
    for i, y in enumerate(range(0, im.height, slice_h), 1):
        part = OUT / f"{page.stem}-p{i}.png"
        im.crop((0, y, im.width, min(im.height, y + slice_h))).save(part)
        print("shot", part.name)


def phone_shots(slug, scr):
    """One PNG per phone: render every phone on a fixed grid, screenshot once, crop at known coordinates."""
    from PIL import Image
    for old in OUT.glob(f"{slug}-[0-9][0-9]-*.png"):
        old.unlink()
    cells, names = [], []
    for i, (name, fn, _) in enumerate(scr(), 1):
        light, dark, _ = render_pair(fn)
        stem = re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")[:40].strip("-")
        for th, ph in (("light", light), ("dark", dark)):
            cells.append(ph)
            names.append(f"{slug}-{i:02d}-{stem}-{th}.png")
    cols, cw, ch, pad = 4, 400, 780, 20
    rows = (len(cells) + cols - 1) // cols
    body = "".join(f'<div class="fr" style="left:{pad + (k % cols) * cw}px;top:{pad + (k // cols) * ch}px">{c}</div>' for k, c in enumerate(cells))
    doc = (f'<!doctype html><html><head><meta charset="utf-8"><style>{CSS}{theme_css()}body{{background:#e9e6e3}}.phone{{box-shadow:none}}</style></head>'
           f'<body style="margin:0"><div class="frames">{body}</div></body></html>')
    with tempfile.TemporaryDirectory() as d:
        f = Path(d) / "frames.html"
        f.write_text(doc, encoding="utf-8")
        shot = Path(d) / "frames.png"
        shoot(f.as_uri(), shot, pad * 2 + cols * cw, pad * 2 + rows * ch)
        im = Image.open(shot).convert("RGB")
        for k, n in enumerate(names):
            x, y = pad + (k % cols) * cw, pad + (k // cols) * ch
            im.crop((x, y, x + 360, y + 740)).save(OUT / n)
    print(f"shot {len(names)} phones for {slug}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--shots", action="store_true")
    args = ap.parse_args()
    pages = []
    for slug, title, pitch, scr in FINALISTS:
        p = OUT / f"{slug}.html"
        p.write_text(finalist_page(slug, title, pitch, scr), encoding="utf-8")
        pages.append((p, slug, scr))
    p = OUT / "index.html"
    p.write_text(index_page(), encoding="utf-8")
    pages.insert(0, (p, None, None))
    for p, slug, scr in pages:
        print("wrote", p.name)
    if args.shots:
        if not chrome():
            sys.exit("chromium not found")
        for p, slug, scr in pages:
            page_shot(p)
            if slug:
                phone_shots(slug, scr)


if __name__ == "__main__":
    main()
