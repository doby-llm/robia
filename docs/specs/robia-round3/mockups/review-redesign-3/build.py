#!/usr/bin/env python3
"""Garment-review redesign, design round 3: the add / remove language inside "Pick, then decide".

    python3 build.py           # writes index.html + the two design pages next to this file
    python3 build.py --shots   # also screenshots: one PNG per phone, page PNGs, and the colour-blind strip

Rounds 1 and 2 stay untouched: this script imports round 2's build.py (which imports round 1's) for the shared CSS,
the Earth & Steel light tokens, the derived dark proposal, the garment art and the screenshot helpers, and adds
round-3 components on top. Every visible string goes through t(); the table under each phone lists the keys it used.
"""
import argparse
import importlib.util
import re
import sys
import tempfile
from pathlib import Path

sys.dont_write_bytecode = True  # keep rounds 1 and 2's folders untouched when importing them
OUT = Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("r2", OUT.parent / "review-redesign-2" / "build.py")
r2 = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(r2)
r1 = r2.r1
e = r1.e

# ---------------------------------------------------------------- strings (key -> EN, ES, DE). *_one / *_other = Android plurals
S = {k: r2.S[k] for k in ("tag_review_title", "undo", "done", "review_left", "review_reviewed_n", "review_now", "review_none",
                          "review_keep_as_is", "review_ctx_restored_occ", "review_complete_tags", "review_complete_kept",
                          "review_complete_edit")}
S.update({
    "review_reject": ("Reject", "Rechazar", "Ablehnen"),
    "review_ctx_restored_cat": ("Default categories restored", "Categorías predeterminadas restauradas", "Standard-Kategorien wiederhergestellt"),
    "review_hint_changes": ("Tap a change to skip it, then apply or reject. Reviewed garments leave the list.",
                            "Toca un cambio para saltarlo y luego aplica o rechaza. Las prendas revisadas salen de la lista.",
                            "Tippe auf eine Änderung, um sie auszulassen, dann übernimm oder lehne ab. Geprüfte Kleidungsstücke verschwinden aus der Liste."),
    "review_apply_n_one": ("Apply 1 change", "Aplicar 1 cambio", "1 Änderung übernehmen"),
    "review_apply_n_other": ("Apply %1$d changes", "Aplicar %1$d cambios", "%1$d Änderungen übernehmen"),
    "review_apply_n_of_m": ("Apply %1$d of %2$d", "Aplicar %1$d de %2$d", "%1$d von %2$d übernehmen"),
    "review_apply_remaining": ("Apply all remaining (%1$d)", "Aplicar las %1$d restantes", "Alle restlichen %1$d übernehmen"),
    "review_all_skipped": ("All changes skipped: the garment stays as it is.", "Todos los cambios saltados: la prenda se queda como está.",
                           "Alle Änderungen ausgelassen: Das Kleidungsstück bleibt unverändert."),
    "review_none_left_occ": ("After this, the garment has no occasions.", "Después de esto, la prenda no tendrá ocasiones.",
                             "Danach hat das Kleidungsstück keine Anlässe mehr."),
    "review_after": ("After", "Después", "Danach"),
    "review_label_adds": ("Adds", "Añade", "Fügt hinzu"),
    "review_label_removes": ("Removes", "Quita", "Entfernt"),
    "review_label_changes": ("Changes", "Cambia", "Ändert"),
    # content descriptions (TalkBack); the visible chip only shows the sign and the tag name
    "review_cd_add": ("Add %1$s. Double-tap to leave it out.", "Añadir %1$s. Toca dos veces para dejarla fuera.",
                      "%1$s hinzufügen. Doppeltippen, um es wegzulassen."),
    "review_cd_add_off": ("%1$s will not be added. Double-tap to add it.", "%1$s no se añadirá. Toca dos veces para añadirla.",
                          "%1$s wird nicht hinzugefügt. Doppeltippen, um es hinzuzufügen."),
    "review_cd_remove": ("Remove %1$s. Double-tap to keep it.", "Quitar %1$s. Toca dos veces para conservarla.",
                         "%1$s entfernen. Doppeltippen, um es zu behalten."),
    "review_cd_remove_off": ("%1$s stays. Double-tap to remove it.", "%1$s se queda. Toca dos veces para quitarla.",
                             "%1$s bleibt. Doppeltippen, um es zu entfernen."),
    "review_cd_swap": ("%1$s instead of %2$s. Double-tap to keep %2$s.", "%1$s en lugar de %2$s. Toca dos veces para conservar %2$s.",
                       "%1$s statt %2$s. Doppeltippen, um %2$s zu behalten."),
    "review_cd_swap_off": ("Stays %2$s. Double-tap to change to %1$s.", "Se queda en %2$s. Toca dos veces para cambiar a %1$s.",
                           "Bleibt %2$s. Doppeltippen, um zu %1$s zu wechseln."),
    "review_complete_added": ("%1$d tags added", "%1$d etiquetas añadidas", "%1$d Tags hinzugefügt"),
    "review_complete_removed": ("%1$d tags removed", "%1$d etiquetas quitadas", "%1$d Tags entfernt"),
    # copy that is not on a mockup phone but changes with removals (listed in the index copy table)
    "review_snack_applied_one": ("1 change applied", "1 cambio aplicado", "1 Änderung übernommen"),
    "review_snack_applied_other": ("%1$d changes applied", "%1$d cambios aplicados", "%1$d Änderungen übernommen"),
    "review_snack_kept": ("Kept as it was", "Se queda como estaba", "Bleibt unverändert"),
    "review_bulk_title": ("Apply the remaining %1$d?", "¿Aplicar las %1$d restantes?", "Die restlichen %1$d übernehmen?"),
    "review_bulk_body": ("Robia will add %1$d tags and remove %2$d on %3$d garments. You can undo it right after.",
                         "Robia añadirá %1$d etiquetas y quitará %2$d en %3$d prendas. Podrás deshacerlo justo después.",
                         "Robia fügt bei %3$d Kleidungsstücken %1$d Tags hinzu und entfernt %2$d. Du kannst das gleich danach rückgängig machen."),
})
LANGS = ("en", "es", "de")
LANG = "en"
USED = []


def t(key, *args):
    if key not in USED:
        USED.append(key)
    tpl = S[key][LANGS.index(LANG)]
    return re.sub(r"%(\d)\$[sd]", lambda m: str(args[int(m.group(1)) - 1]), tpl)


def tp(key, n, *args):
    """Plural: registers both forms in the table, renders the right one."""
    for f in ("_one", "_other"):
        if key + f not in USED:
            USED.append(key + f)
    return t(key + ("_one" if n == 1 else "_other"), n, *args)


TAGS = dict(r2.TAGS)
TAGS["datenight"] = ("Date night", "Noche de cita", "Date-Abend")
TAGS["weekendaway"] = ("Weekend away", "Escapada", "Wochenendausflug")  # custom occasion merged into the default Casual
TAGS["casual"] = ("Casual", "Informal", "Freizeit")


def tn(tag):
    return TAGS[tag][LANGS.index(LANG)]


def cn(c):
    return r1.COLORS[c][1 + LANGS.index(LANG)]


GARMENTS = dict(r2.GARMENTS)
thumb = r2.thumb

# ---------------------------------------------------------------- tokens: add / remove (light from Color.kt + round 1; dark derived)
ADDREM = {
    "light": {"add": "#2e6b47", "onadd": "#ffffff", "addc": "#dcefe1", "onaddc": "#17432a", "addb": "#9cc8aa",
              "rem": "#ba1a1a", "onrem": "#ffffff", "remc": "#ffdad6", "onremc": "#93000a", "remb": "#eaa59c"},
    "dark": {"add": "#8fd3a5", "onadd": "#0b2615", "addc": "#1d4430", "onaddc": "#c6f2d3", "addb": "#4f8f66",
             "rem": "#ffb4ab", "onrem": "#5c0a05", "remc": "#5e1f19", "onremc": "#ffdad6", "remb": "#b5554b"},
}


def theme_css():
    to_vars = lambda d: ";".join(f"--{k}:{v}" for k, v in d.items())
    return (f".phone[data-theme=light]{{{to_vars({**r1.LIGHT, **ADDREM['light']})}}}\n"
            f".phone[data-theme=dark]{{{to_vars({**r1.DARK, **ADDREM['dark']})}}}\n")


def _lin(c):
    c = c / 255
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def rel_lum(hx):
    hx = hx.lstrip("#")
    r, g, b = (int(hx[i:i + 2], 16) for i in (0, 2, 4))
    return 0.2126 * _lin(r) + 0.7152 * _lin(g) + 0.0722 * _lin(b)


def contrast(a, b):
    la, lb = sorted((rel_lum(a), rel_lum(b)), reverse=True)
    return (la + 0.05) / (lb + 0.05)


# ---------------------------------------------------------------- CSS (round 1 + round 2 + round 3)
CSS3 = r"""
/* change chips: sign circle + label. Filled = will happen, hollow = skipped by the user */
.cx{display:inline-flex;align-items:center;gap:7px;height:36px;padding:0 12px 0 6px;border-radius:10px;border:1px solid;
  font:500 14px/18px var(--fs);white-space:nowrap;max-width:100%;min-width:0}
.cx .s{width:22px;height:22px;border-radius:50%;flex:none;display:flex;align-items:center;justify-content:center;font:700 17px/1 var(--fs)}
.cx .l{overflow:hidden;text-overflow:ellipsis;min-width:0}
.cx.add{background:var(--addc);color:var(--onaddc);border-color:var(--addb)}
.cx.add .s{background:var(--add);color:var(--onadd)}
.cx.rem{background:var(--remc);color:var(--onremc);border-color:var(--remb)}
.cx.rem .s{background:var(--rem);color:var(--onrem)}
.cx.rem .l{text-decoration:line-through;text-decoration-thickness:1.5px}
.cx.off .s{background:transparent;color:var(--onv);box-shadow:inset 0 0 0 1.5px var(--outline)}
.cx.add.off{background:transparent;color:var(--onv);border:1px dashed var(--outline)}
.cx.rem.off{background:transparent;color:var(--on);border:1px solid var(--outline)}
.cx.rem.off .l{text-decoration:none}
.cx.nos{padding-left:12px}
.cxs{display:inline-flex;align-items:center;gap:4px;padding:4px;border-radius:14px;border:1px solid var(--ov);max-width:100%;min-width:0}
.cxs .ar{flex:none;color:var(--onv);font-size:15px;padding:0 1px}
.cxs .cx{flex:0 1 auto}
.cxs.off{border-style:dashed}
.chg{display:flex;flex-wrap:wrap;gap:0 8px;margin-top:4px}
.chg .tc{display:flex;align-items:center;min-height:48px;max-width:100%;min-width:0}
.gwarn{display:flex;gap:8px;align-items:flex-start;margin-top:4px;font-size:12.5px;line-height:17px;color:var(--onv)}
.gwarn i{flex:none;width:18px;height:18px;border-radius:50%;border:1.5px solid var(--onv);font:700 11px/15px var(--fs);text-align:center;font-style:normal}
.gfoot .pbtn.ton{flex:1.45 1 0}
.ghead .gid span.nowl{white-space:normal;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical}
/* labelled rows (alternative) */
.lh{display:flex;align-items:center;gap:8px;margin:10px 0 0;font:600 12px/16px var(--fs);letter-spacing:.4px;text-transform:uppercase}
.lh .s{width:20px;height:20px;border-radius:50%;display:flex;align-items:center;justify-content:center;font:700 15px/1 var(--fs)}
.lh.add{color:var(--add)}.lh.add .s{background:var(--add);color:var(--onadd)}
.lh.rem{color:var(--rem)}.lh.rem .s{background:var(--rem);color:var(--onrem)}
.lh.swp{color:var(--onv)}.lh.swp .s{box-shadow:inset 0 0 0 1.5px var(--outline);color:var(--onv);font-size:12px}
.lh em{font-style:normal;font-weight:500;text-transform:none;letter-spacing:0;color:var(--onv)}
/* explored variants */
.dl{margin-top:6px;border:1px solid var(--line);border-radius:12px;overflow:hidden}
.dl .r{display:flex;align-items:center;min-height:44px;border-top:1px solid var(--line);font:500 14px/18px var(--fs)}
.dl .r:first-child{border-top:0}
.dl .gu{width:30px;align-self:stretch;flex:none;display:flex;align-items:center;justify-content:center;font:700 16px/1 var(--fs)}
.dl .r.a{background:var(--addc);color:var(--onaddc)}.dl .r.a .gu{background:var(--add);color:var(--onadd)}
.dl .r.d{background:var(--remc);color:var(--onremc)}.dl .r.d .gu{background:var(--rem);color:var(--onrem)}.dl .r.d .n{text-decoration:line-through}
.dl .r.k{color:var(--onv);opacity:.6}
.dl .n{flex:1;padding:0 10px}
.dl .sw2{width:36px;height:20px;border-radius:10px;background:var(--primary);margin-right:10px;position:relative}
.dl .sw2::after{content:"";position:absolute;right:2px;top:2px;width:16px;height:16px;border-radius:50%;background:var(--onprimary)}
.ba{display:grid;grid-template-columns:48px minmax(0,1fr);gap:6px 4px;align-items:start;margin-top:8px}
.ba .k{font:600 11px/36px var(--fs);letter-spacing:.4px;text-transform:uppercase;color:var(--onv)}
.ba .v{display:flex;flex-wrap:wrap;gap:6px}
.ba .arr2{grid-column:2;color:var(--onv);font-size:15px;line-height:12px;padding-left:8px}
.pl{display:inline-flex;align-items:center;height:36px;padding:0 12px;border-radius:10px;border:1px solid var(--ov);font:500 14px/18px var(--fs);color:var(--on);white-space:nowrap}
.pl.x{color:var(--onremc);background:var(--remc);border-color:var(--remb);text-decoration:line-through}
.pl.n{background:var(--addc);color:var(--onaddc);border-color:var(--addb)}
.cx.bare .s{background:transparent !important;color:inherit !important;width:12px;font-size:16px}.cx.bare{padding-left:10px;gap:4px}
.cx.flat .s{display:none}.cx.flat{padding-left:12px}
.cx.colouronly .l{text-decoration:none}.cx.colouronly .s{display:none}.cx.colouronly{padding-left:12px}
/* legend */
.legend{height:auto !important;width:560px !important;padding:16px 18px;border-radius:20px}
.legend table{border-collapse:collapse;width:100%}
.legend td{padding:6px 6px;vertical-align:middle;border-top:1px solid var(--line);font-size:12.5px;line-height:17px;color:var(--onv)}
.legend tr:first-child td{border-top:0}
.legend td:first-child{width:200px}
.legend b{color:var(--on)}
.legends{display:flex;gap:24px;flex-wrap:wrap}
.cvd img{max-width:100%;border-radius:12px;display:block}
.donebox .tally{display:flex;gap:14px;justify-content:center;margin-top:12px;flex-wrap:wrap}
.donebox .tally span{display:inline-flex;align-items:center;gap:6px;font-size:13.5px;color:var(--on)}
.donebox .tally .s{width:20px;height:20px;border-radius:50%;display:inline-flex;align-items:center;justify-content:center;font:700 14px/1 var(--fs)}
.donebox .tally .a .s{background:var(--add);color:var(--onadd)}.donebox .tally .d .s{background:var(--rem);color:var(--onrem)}
.frames{position:relative}.frames .fr{position:absolute;width:360px;height:740px}
"""
CSS = r2.CSS + CSS3


# ---------------------------------------------------------------- chips
def chip(kind, tag, on=True, sign=True):
    """kind add|rem. on=False means the user skipped it (add left out / removal kept)."""
    cls = f"cx {kind}" + ("" if on else " off") + ("" if sign else " flat")
    glyph = "+" if kind == "add" else "−"
    cd = {("add", True): "review_cd_add", ("add", False): "review_cd_add_off",
          ("rem", True): "review_cd_remove", ("rem", False): "review_cd_remove_off"}[(kind, on)]
    label = t(cd, tn(tag))
    return (f'<span class="{cls}" role="switch" aria-checked="{str(on).lower()}" aria-label="{e(label)}">'
            f'<span class="s">{glyph}</span><span class="l">{e(tn(tag))}</span></span>')


def swap_chip(new, old, on=True, sign=True):
    label = t("review_cd_swap" if on else "review_cd_swap_off", tn(new), tn(old))
    o = "" if on else " off"
    f = "" if sign else " flat"
    return (f'<span class="cxs{o}" role="switch" aria-label="{e(label)}">'
            f'<span class="cx rem{o}{f}"><span class="s">−</span><span class="l">{e(tn(old))}</span></span><span class="ar">→</span>'
            f'<span class="cx add{o}{f}"><span class="s">+</span><span class="l">{e(tn(new))}</span></span></span>')


def item_chip(it, off, sign=True):
    if it[0] == "swap":
        return swap_chip(it[1], it[2], it[1] not in off, sign)
    return chip(it[0], it[1], it[1] not in off, sign)


def ordered(items):
    """Reading order inside a card: swap (old → new), then removals, then additions."""
    rank = {"swap": 0, "rem": 1, "add": 2}
    return sorted(items, key=lambda it: rank[it[0]])


# ---------------------------------------------------------------- shared phone parts
def phone(theme, inner):
    return f'<div class="phone" data-theme="{theme}" lang="{LANG}">{r1.status()}{inner}</div>'


def appbar(title):
    return f'<div class="ab"><span class="ib">←</span><h1>{e(title)}</h1><span class="ib">⋮</span></div>'


def rhead(left, reviewed, total):
    pct = (total - left) / total * 100 if total else 100
    rv = f'<span class="rv">{e(t("review_reviewed_n", reviewed))} ▾</span>' if reviewed else '<span class="rv"></span>'
    return (f'<div class="rhead"><div class="top"><b>{e(t("review_left", left))}</b>{rv}</div>'
            f'<div class="prog"><i style="width:{pct:.0f}%"></i></div></div>')


def ctx_block(ctx_key, *args):
    return f'<div class="ctxb"><div class="ctx">{e(t(ctx_key, *args))}</div><p>{e(t("review_hint_changes"))}</p></div>'


def bulk_bar(n):
    return f'<div class="bb2"><div class="btn tonal">{e(t("review_apply_remaining", n))}</div></div>'


def identity(g, now, group):
    _, col, cat, _ = GARMENTS[g]
    line1 = f"{tn(cat)} · {cn(col)}"
    if group == "category":
        line2 = ""
    else:
        line2 = t("review_now", ", ".join(tn(x) for x in now) if now else t("review_none"))
    return (f'<div class="ghead">{thumb(g)}<div class="gid"><b>{e(line1)}</b>'
            + (f'<span class="nowl">{e(line2)}</span>' if line2 else "") + "</div></div>")


def footer(items, off):
    n_all = len(items)
    n_on = sum(1 for it in items if it[1] not in off)
    if n_on == 0:
        return (f'<div class="gnote">{e(t("review_all_skipped"))}</div>'
                f'<div class="gfoot"><div class="pbtn ton">{e(t("review_keep_as_is"))}</div></div>')
    label = tp("review_apply_n", n_all) if n_on == n_all else t("review_apply_n_of_m", n_on, n_all)
    return (f'<div class="gfoot"><div class="pbtn out">{e(t("review_reject"))}</div>'
            f'<div class="pbtn ton">{e(label)}</div></div>')


def none_left(now, items, off, group):
    """True when applying the active changes leaves the group empty on this garment."""
    if group != "occasion" or not now:
        return False
    after = set(now)
    for it in items:
        if it[1] in off:
            continue
        if it[0] == "add":
            after.add(it[1])
        elif it[0] == "rem":
            after.discard(it[1])
    return not after


def warn_line():
    return f'<div class="gwarn"><i>!</i><span>{e(t("review_none_left_occ"))}</span></div>'


# ---------------------------------------------------------------- recommended: signed chips
def card_signed(g, now, items, off=(), group="occasion"):
    off = set(off)
    items = ordered(items)
    chips = "".join(f'<span class="tc">{item_chip(it, off)}</span>' for it in items)
    warn = warn_line() if none_left(now, items, off, group) else ""
    return f'<div class="gcard">{identity(g, now, group)}<div class="chg">{chips}</div>{warn}{footer(items, off)}</div>'


# ---------------------------------------------------------------- alternative: labelled rows
def card_rows(g, now, items, off=(), group="occasion"):
    off = set(off)
    body = ""
    for kind, key, glyph, cls in (("swap", "review_label_changes", "⇄", "swp"), ("rem", "review_label_removes", "−", "rem"),
                                  ("add", "review_label_adds", "+", "add")):
        its = [it for it in items if it[0] == kind]
        if not its:
            continue
        n_on = sum(1 for it in its if it[1] not in off)
        count = f' <em>· {n_on}/{len(its)}</em>' if n_on != len(its) else ""
        body += (f'<div class="lh {cls}"><span class="s">{glyph}</span>{e(t(key))}{count}</div>'
                 f'<div class="chg" style="margin-top:0">'
                 + "".join(f'<span class="tc">{item_chip(it, off, sign=False)}</span>' for it in its) + '</div>')
    warn = warn_line() if none_left(now, items, off, group) else ""
    return f'<div class="gcard">{identity(g, now, group)}{body}{warn}{footer(items, off)}</div>'


# ---------------------------------------------------------------- scenario data (one tag group per review)
NOW = {"coat": ["everyday"], "shirt": ["everyday"], "dress": ["everyday"], "blazer": ["business", "weekend", "datenight"],
       "trench": ["everyday", "weekend"], "skirt": ["weekend"], "jeans": ["everyday"], "tee": ["active"]}
SINGLES = [("coat", [("add", "business")]), ("shirt", [("add", "formal")]), ("dress", [("add", "dressedup")])]
# Restore default tags only adds defaults (restoredTagChangeSet); a custom tag merged into a default re-points its garments,
# which the review shows as a swap. Custom tags that were not merged stay and only appear in the "Now:" line.
RESTORE = ("blazer", ["business", "weekendaway", "datenight"],
           [("add", "everyday"), ("add", "formal"), ("add", "dressedup"), ("add", "statement"), ("swap", "casual", "weekendaway")])
# A mixed adds + removes proposal (e.g. after a tag is deleted or merged): the stress case for red next to green.
MIXED = ("blazer", [("add", "everyday"), ("add", "formal"), ("add", "dressedup"), ("add", "statement"),
                    ("rem", "weekend"), ("rem", "datenight")])
SWAPS = [("knit", [("swap", "knitwear", "tops")]), ("trench", [("swap", "coats", "jackets")]), ("tee", [("swap", "shirts", "tops")])]
REMOVAL = [("trench", [("rem", "weekend")]), ("skirt", [("rem", "weekend")])]


def lang(code, fn):
    def run(th):
        global LANG
        prev, LANG = LANG, code
        try:
            return fn(th)
        finally:
            LANG = prev
    return run


def screens(card, alt=False, cvd=False):
    out = []

    def lst(th, cards, left=37, reviewed=0, total=37, ctx="review_ctx_restored_occ", with_ctx=True):
        return phone(th, appbar(t("tag_review_title")) + rhead(left, reviewed, total) + '<div class="scroll">'
                     + (ctx_block(ctx) if with_ctx else '<div style="height:4px"></div>')
                     + "".join(cards) + '</div>' + bulk_bar(left))

    c = lambda g, items, off=(), group="occasion": card(g, NOW.get(g, []), items, off, group)
    sign_word = "the label above the chips (“Adds” / “Removes”)" if alt else "the sign in the circle (+ / −)"

    out.append(("Single add", lambda th: lst(th, [c(*SINGLES[0]), c(*SINGLES[1], off={"formal"}), c(*SINGLES[2])]),
                ([f"Most common case: one green change. The row label “+ Adds” states what happens in words; the chip itself carries no sign."]
                 if alt else
                 ["Most common case: one change per garment. The chip says it twice without colour: a filled <b>+</b> circle and plain text = “will be added”."])
                + ["<b>Tap the chip</b> → it turns into a dashed ghost with a hollow sign (“left out”), a quiet line says “All changes skipped” and the footer collapses to one button, <b>Keep as is</b> (second card). Tap the chip again to bring Reject / Apply back.",
                   "<b>Reject</b> = keep the garment’s tags exactly as they are. <b>Apply 1 change</b> = apply what is on (filled). Both remove the garment from the list (Undo snackbar).",
                   "Apply is the wider, right-hand button (thumb side); both 48 dp. Bottom bar: <b>Apply all remaining (37)</b> with a confirmation that states adds and removes."]))

    g, items = MIXED
    rg, rnow, ritems = RESTORE
    restore_card = (lambda th: lst(th, [c(g, items), c(*SINGLES[0])])) if cvd else (lambda th: lst(th, [card(rg, rnow, ritems), c(*SINGLES[0])]))
    out.append(("Restore case · 4 adds + 1 merge", restore_card,
                ["Restoring the default occasions <b>only adds</b>: 4 defaults this garment lacks. It never removes custom tags: Business and the custom Date night are unchanged and only appear in the “Now:” line.",
                 "The one red sign comes from a <b>merge</b>: the custom “Weekend away” was merged into the default Casual, so its garments move to Casual. "
                 "It is a swap, one unit “− Weekend away → + Casual”, one tap target; tapped, the garment keeps Weekend away.",
                 ("Rows in reading order: <b>⇄ Changes</b> (the merge) first, then <b>+ Adds</b>; each label has its own filled sign circle in the matching colour."
                  if alt else
                  "Reading order is fixed: <b>swap first, then additions</b>, so the red half never interleaves with the green chips. Added: green tint, filled <b>+</b>, plain text."),
                 "<b>Tap a + chip</b> to leave it out; <b>tap the swap</b> to keep Weekend away. Each chip is a switch (36 dp visual, 48 dp row); state changes immediately, nothing is saved until the footer.",
                 "Primary button counts what will happen: <b>Apply 5 changes</b>. Removals-only cards come from deleting or merging a tag or changing a category, not from restore (state 4)."]))

    out.append(("Swap category", lambda th: lst(th, [c(g2, it, group="category") for g2, it in SWAPS], ctx="review_ctx_restored_cat"),
                ["A category review (one group per review). A garment has one category, so a change is a <b>swap</b>: one unit “− Tops → + Knitwear” inside a thin frame.",
                 "The whole frame is <b>one tap target</b> (you cannot drop Tops without taking Knitwear: that would leave no category, or two). Tapped: Tops turns into a plain chip (stays), Knitwear into a dashed ghost, the frame goes dashed, and the footer becomes <b>Keep as is</b>.",
                 "No “Now:” line in category reviews: the identity line already names the current category.",
                 "TalkBack: “Knitwear instead of Tops. Double-tap to keep Tops.”"]))

    out.append(("Removal only + all removed", lambda th: lst(th, [c(g2, it) for g2, it in REMOVAL] + [c(*SINGLES[1])]),
                ["Removal only: the card has nothing green. " + ("The “− Removes” label still says it in words." if alt else "The struck-through text and the filled <b>−</b> are enough to read “goes away” in grey-scale."),
                 "All removed: the second garment’s only occasion goes away. A quiet line with an outlined <b>!</b> says so (“After this, the garment has no occasions.”) — information, not an error, so no red fill and no extra dialog.",
                 "<b>Tap the − chip</b> → the tag stays: plain outlined chip, text no longer struck, sign hollow; the card becomes <b>Keep as is</b>."]))

    out.append(("User skipped one add and one remove", lambda th: lst(th, [c(g, items, off={"statement", "datenight"}), c(*SINGLES[2])], with_ctx=False),
                ["The user tapped <b>+ Statement</b> (left out) and <b>− Date night</b> (kept). The rule is one sentence: <b>filled sign = will happen, hollow sign = skipped</b>.",
                 "Left-out add → dashed ghost, muted text (the tag will not be there). Kept removal → plain solid chip, normal text, strike gone (the tag stays, like any tag the garment has).",
                 ("The row labels show the split: “Adds · 3/4”, “Removes · 1/2”. Weak spot: a kept chip still sits under “Removes”." if alt else
                  "Chips never move when toggled (no re-sorting), so the thumb can tap again to undo the toggle."),
                 "Button: <b>Apply 4 of 6</b>. Reject still means “keep everything as it is”, whatever the chips show."]))

    out.append(("Long strings · German", lang("de", lambda th: lst(th, [c("blazer", items, off={"statement"}), c(*REMOVAL[1])])),
                ["“Wochenendausflug mit Freunden” (user-made) is the worst case: the chip takes the full card width and ellipsizes only past it; the sign never shrinks.",
                 "“5 von 6 übernehmen” in the wider primary button; “6 Änderungen übernehmen” wraps to 2 lines inside 48 dp if needed. “Alle restlichen 37 übernehmen” fits the bottom bar.",
                 "Hint and context lines wrap freely; chips never wrap inside."]))

    def done(th):
        minis = "".join(thumb(x) for x in ("coat", "knit", "blazer", "shirt", "jeans"))
        inner = (appbar(t("tag_review_title")).replace('<span class="ib">⋮</span>', "").replace("←", "×")
                 + f'<div class="donebox"><div class="okc">✓</div><h2>{e(t("review_complete_tags", 31))}</h2>'
                 + f'<div class="tally"><span class="a"><span class="s">+</span>{e(t("review_complete_added", 52))}</span>'
                 + f'<span class="d"><span class="s">−</span>{e(t("review_complete_removed", 9))}</span></div>'
                 + f'<p>{e(t("review_complete_kept", 6))}</p>'
                 + f'<div class="mini-list">{minis}</div><span class="ibtn tonal">✎ {e(t("review_complete_edit"))}</span></div>'
                 + f'<div class="bb"><div class="btn filled">{e(t("done"))}</div></div>')
        return phone(th, inner)
    out.append(("Completion", done,
                ["Round 2’s calm completion, plus one tally line in the same language as the chips: <b>+ 52 tags added · − 9 tags removed</b> (sign in a filled circle + words).",
                 "Static, no sparkle, no auto-dismiss. <b>Edit changes</b> reopens the review with every garment in “Reviewed”; <b>Done</b> returns to Manage tags."]))
    return out


# ---------------------------------------------------------------- explored variants (mixed adds + removes case, light)
def _variant_phone(th, body, foot_label):
    _, col, cat, _ = GARMENTS["blazer"]
    now = NOW["blazer"]
    ident = (f'<div class="ghead">{thumb("blazer")}<div class="gid"><b>{e(tn(cat))} · {e(cn(col))}</b>'
             f'<span class="nowl">{e(t("review_now", ", ".join(tn(x) for x in now)))}</span></div></div>')
    card = (f'<div class="gcard">{ident}{body}<div class="gfoot"><div class="pbtn out">{e(t("review_reject"))}</div>'
            f'<div class="pbtn ton">{e(foot_label)}</div></div></div>')
    return phone(th, appbar(t("tag_review_title")) + rhead(37, 0, 37) + '<div class="scroll"><div style="height:4px"></div>'
                 + card + '</div>' + bulk_bar(37))


def v1(th):  # Manu's literal idea: mixed flow, model order
    _, items = MIXED
    mixed = [items[0], items[4], items[1], items[2], items[5], items[3]]
    chips = "".join(f'<span class="tc">{item_chip(it, set()).replace("class=" + chr(34) + "cx ", "class=" + chr(34) + "cx bare ")}</span>' for it in mixed)
    return _variant_phone(th, f'<div class="chg">{chips}</div>', tp("review_apply_n", 6))


def v2(th):
    _, items = MIXED
    return phone(th, appbar(t("tag_review_title")) + rhead(37, 0, 37) + '<div class="scroll"><div style="height:4px"></div>'
                 + card_rows("blazer", NOW["blazer"], items) + '</div>' + bulk_bar(37))


def v3(th):
    now = NOW["blazer"]
    rows = ""
    for tag, k in (("business", "k"), ("weekend", "d"), ("datenight", "d"), ("everyday", "a"), ("formal", "a"), ("dressedup", "a"), ("statement", "a")):
        g = {"a": "+", "d": "−", "k": ""}[k]
        sw = '<span class="sw2"></span>' if k != "k" else ""
        rows += f'<div class="r {k}"><span class="gu">{g}</span><span class="n">{e(tn(tag))}</span>{sw}</div>'
    _ = now
    return _variant_phone(th, f'<div class="dl">{rows}</div>', tp("review_apply_n", 6))


def v4(th):
    before = "".join(f'<span class="pl{" x" if x in ("weekend", "datenight") else ""}">{e(tn(x))}</span>' for x in ("business", "weekend", "datenight"))
    after = "".join(f'<span class="pl{" n" if x != "business" else ""}">{e(tn(x))}</span>' for x in ("business", "everyday", "formal", "dressedup", "statement"))
    body = (f'<div class="ba"><span class="k">{e(t("review_now", "")).rstrip(": ")}</span><div class="v">{before}</div>'
            f'<span class="k">{e(t("review_after"))}</span><div class="v">{after}</div></div>')
    return _variant_phone(th, body, tp("review_apply_n", 6))


def v5(th):  # one in-place strip: the result set, changes marked in place
    items = [("keep", "business"), ("rem", "weekend"), ("rem", "datenight"), ("add", "everyday"), ("add", "formal"), ("add", "dressedup"), ("add", "statement")]
    chips = "".join(f'<span class="tc">{"<span class=" + chr(34) + "pl" + chr(34) + ">" + e(tn(tg)) + "</span>" if k == "keep" else chip(k, tg)}</span>' for k, tg in items)
    return _variant_phone(th, f'<div class="chg">{chips}</div>', tp("review_apply_n", 6))


def control_colour_only(th):
    """CVD control, not a proposal: same chips with colour as the only cue (no sign, no strike)."""
    _, items = MIXED
    chips = "".join(f'<span class="tc"><span class="cx {it[0]} colouronly"><span class="s"></span><span class="l">{e(tn(it[1]))}</span></span></span>'
                    for it in ordered(items))
    return _variant_phone(th, f'<div class="chg">{chips}</div>', tp("review_apply_n", 6))


VARIANTS = [
    ("1 · Signed chips (Manu’s idea, literal)", "base", v1,
     "Red chip with “−” and struck-through text next to the green “+” chips, in the order the model returns them.",
     "Answers the feedback directly and stays as compact as round 2. Problems when taken literally: red and green chips interleave in one wrapped flow, so the eye has to sort them; "
     "a bare 14 px “−” is a thin dash that disappears next to a strike line; round 2 used strike-through for “left out”, which now collides with “will be removed”. "
     "All three are fixable without adding rows → this is the base of the recommendation."),
    ("2 · Labelled rows “Adds” / “Removes”", "alt", v2,
     "Two sections with a leading sign + word, chips under each; swap gets its own “Changes” row.",
     "The clearest for colour-blind users and screen readers (the word is there), and the rows sort red from green by themselves. Costs: +2 label lines per card "
     "(≈40 dp on a mixed card), labels are redundant on the 80 % single-add cards, and a <i>kept</i> removal still sits under “Removes”, so the label can contradict the chip. "
     "Built in full as the alternative."),
    ("3 · Unified diff list", "drop", v3,
     "One row per tag with a +/− gutter and a switch; unchanged tags dimmed in place.",
     "Most explicit and very accessible (gutter sign + row + switch), and shows the full result. But it is a code-review metaphor, one 44–48 dp row per tag makes a mixed card ~2× taller "
     "(7 rows here, more with many occasions), and dimmed kept rows look disabled rather than “unchanged”. Too heavy for 100+ garments."),
    ("4 · Before / after strips", "drop", v4,
     "“Now” strip with removed tags crossed out in place, “After” strip with added tags appearing in place.",
     "Reads like a story (now → after), but every unchanged tag is drawn twice, long DE strings double the wrapping, and it is unclear which strip is tappable: "
     "tap the crossed “Weekend” on top to keep it, or tap something below? It is round 1’s before/after boxes again, which Manu moved away from."),
    ("5 · One in-place strip (result set)", "drop", v5,
     "A single strip of the tags the garment will have: unchanged tags plain, removed ones struck in place, added ones green.",
     "Merges “Now:” and the changes, no duplication, nice for small sets. But plain unchanged chips sit between tappable change chips and look tappable too (they are not), "
     "the strip grows with tags that do not change, and red/green interleave again. Its good part — show unchanged tags only as context — stays as the “Now:” line."),
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
    return (f'<div class="pair"><div class="lbl">{e(title)} — light · dark</div>'
            f'<div class="phones">{light}{dark}</div>'
            f'<div class="cap"><b class="t">{e(title)}</b><ul>{caption}</ul></div>'
            f'<div class="lbl">Strings used on this screen (incl. TalkBack descriptions)</div>{strings_table(keys)}</div>')


DESIGNS = [
    ("signed-chips", "Recommended · Signed chips, sorted",
     "Inside round 2’s “Pick, then decide” card: removals then additions, each a chip with a filled sign circle (− red, + green) and, for removals, struck-through text. "
     "Tap a chip to skip it (sign goes hollow). One explicit Reject / Apply N per garment.",
     lambda: screens(card_signed)),
    ("labelled-rows", "Alternative · Labelled rows",
     "Same card, same chip states, but changes are grouped under a leading sign + word (“− Removes”, “+ Adds”, “⇄ Changes”) and the chips carry no sign of their own.",
     lambda: screens(card_rows, alt=True)),
]

DARK_NOTE = ('<div class="note"><b>Tokens.</b> Light = Earth &amp; Steel (<code>Color.kt</code>): error #BA1A1A / errorContainer #FFDAD6 / onErrorContainer #93000A for removals, '
             'round 1’s success green #2E6B47 / #DCEFE1 / #17432A for additions. Dark = round 1’s derived dark proposal plus a derived add/remove pair '
             '(Robia ships light only). Fonts render with the fallback stack (wider than Inter/Roboto, so what fits here fits on device).</div>')


def design_page(slug, title, pitch, scr):
    blocks = "".join(pair_block(name, fn, notes) for name, fn, notes in scr())
    return (head(f"Robia · review redesign round 3 · {title}")
            + f'<p><a href="index.html">← Round 3 overview</a></p><h1>{e(title)}</h1><p class="lead">{e(pitch)}</p>'
            + DARK_NOTE + f'<div class="pairs">{blocks}</div>' + TAIL)


def legend(theme):
    rows = [
        (chip("add", "business"), "<b>Will be added.</b> Filled + circle, green tint, plain text. Tap → left out."),
        (chip("add", "business", on=False), "<b>Left out by you.</b> Hollow +, dashed outline, muted text: it will not be there. Tap → add again."),
        (chip("rem", "weekend"), "<b>Will be removed.</b> Filled − circle, red tint, <s>struck</s> text. Tap → keep it."),
        (chip("rem", "weekend", on=False), "<b>Kept by you.</b> Hollow −, solid outline, normal text: it stays like any tag. Tap → remove again."),
        (swap_chip("knitwear", "tops"), "<b>Category swap.</b> One tap target. Tap → stays Tops."),
        (swap_chip("knitwear", "tops", on=False), "<b>Swap skipped.</b> Tops stays (plain), Knitwear ghost, dashed frame."),
        (f'<span class="inst" style="font-size:13px">{e(t("review_now", tn("business")))}</span>', "<b>Unchanged tags.</b> Text only in the “Now:” line, never a chip: not a proposal, not tappable."),
    ]
    body = "".join(f"<tr><td>{a}</td><td>{b}</td></tr>" for a, b in rows)
    return f'<div class="phone legend" data-theme="{theme}"><table>{body}</table></div>'


def contrast_table():
    rows = ""
    checks = [("+ sign / label colour vs card", "add", "lowest"), ("− sign / label colour vs card", "rem", "lowest"),
              ("Add chip text vs its fill", "onaddc", "addc"), ("Remove chip text vs its fill", "onremc", "remc"),
              ("+ glyph vs filled circle", "onadd", "add"), ("− glyph vs filled circle", "onrem", "rem"),
              ("Kept / ghost chip text vs card", "onv", "lowest")]
    for name, fg, bg in checks:
        cells = ""
        for th in ("light", "dark"):
            tok = {**(r1.LIGHT if th == "light" else r1.DARK), **ADDREM[th]}
            c = contrast(tok[fg], tok[bg])
            cls = "good" if c >= 4.5 else "bad"
            cells += (f"<td><span class='rate {cls}'>{c:.1f}:1</span> <code>{tok[fg]}</code> on <code>{tok[bg]}</code></td>")
        rows += f"<tr><th>{e(name)}</th>{cells}</tr>"
    return f"<table class='cmp'><tr><th>Pair</th><th>Light (card #FFFFFF)</th><th>Dark (card #211C1A)</th></tr>{rows}</table>"


COPY_KEYS = ["review_reject", "review_apply_n_one", "review_apply_n_other", "review_apply_n_of_m", "review_keep_as_is", "review_all_skipped",
             "review_none_left_occ", "review_apply_remaining", "review_bulk_title", "review_bulk_body", "review_snack_applied_one",
             "review_snack_applied_other", "review_snack_kept", "review_hint_changes", "review_cd_add", "review_cd_add_off", "review_cd_remove",
             "review_cd_remove_off", "review_cd_swap", "review_cd_swap_off"]


def index_page():
    global LANG, USED
    LANG, USED = "en", []
    sk = ""
    for name, kind, fn, pitch, crit in VARIANTS:
        verdict = {"base": "base of the recommendation", "alt": "alternative", "drop": "dropped"}[kind]
        vcls = {"base": "fin", "alt": "lang", "drop": "drop"}[kind]
        sk += (f'<div class="sketch"><div class="mini">{fn("light")}</div><span class="verdict {vcls}">{verdict}</span><h3>{e(name)}</h3>'
               f'<p><b>{e(pitch)}</b></p><p>{crit}</p></div>')
    side = ""
    for idx, label in ((1, "Restore case · 4 adds + 1 merge"), (4, "User skipped one add and one remove")):
        side += f'<h2 class="sec">Side by side · {e(label)}</h2><div class="side">'
        for slug, title, pitch, scr in DESIGNS:
            LANG = "en"
            for th in ("light", "dark"):
                side += f'<div class="col"><h3><a href="{slug}.html">{e(title)}</a> <small>{th}</small></h3>{scr()[idx][1](th)}</div>'
        side += "</div>"
    cmp_rows = [
        ("Restore card height (4 adds + 1 merge)", ("good", "3 chip lines"), ("mid", "+2 label lines (≈ +40 dp)")),
        ("Single-add card", ("good", "same as round 2"), ("mid", "label “+ Adds” above one chip is redundant")),
        ("Without colour", ("good", "sign shape + strike + reading order"), ("good", "words + strike + grouping")),
        ("Skipped states", ("good", "hollow sign, one rule for all chips"), ("mid", "kept chip sits under “Removes”; needs “1/2” count")),
        ("Swap", ("good", "one framed unit"), ("good", "own “Changes” row")),
        ("Long DE strings", ("good", "chip up to card width"), ("good", "same + longer labels (“Fügt hinzu”)")),
        ("Build (Compose)", ("good", "FilterChip with custom leading icon; one FlowRow"), ("good", "same chips + section headers")),
    ]
    cmp = "<table class='cmp'><tr><th></th><th>Recommended · Signed chips</th><th>Alternative · Labelled rows</th></tr>"
    for crit, *cells in cmp_rows:
        cmp += f"<tr><th>{e(crit)}</th>" + "".join(
            f"<td><span class='rate {c}'>{ {'good': 'Good', 'mid': 'OK', 'bad': 'Weak'}[c] }</span> {e(txt)}</td>" for c, txt in cells) + "</tr>"
    cmp += "</table>"
    LANG, USED = "en", []
    leg = f'<div class="legends">{legend("light")}{legend("dark")}</div>'
    for k in COPY_KEYS:
        t(k, 1, 2, 3) if "%" in S[k][0] else t(k)
    copy = strings_table(COPY_KEYS)
    tap = """<table class='cmp'>
<tr><th>Chip kind</th><th>Default</th><th>Tap once</th><th>Tap again</th><th>Button effect</th></tr>
<tr><td><b>+ add</b></td><td>filled +, green tint</td><td>left out: hollow +, dashed ghost, muted</td><td>back to add</td><td>Apply count −1</td></tr>
<tr><td><b>− remove</b></td><td>filled −, red tint, struck</td><td>kept: hollow −, plain solid chip, no strike</td><td>back to remove</td><td>Apply count −1</td></tr>
<tr><td><b>− old → + new</b> (category)</td><td>framed pair, both filled</td><td>stays old: old plain, new ghost, dashed frame</td><td>back to swap</td><td>Apply count −1</td></tr>
<tr><td>unchanged tag</td><td colspan=4>Text in “Now:” only. Not a chip, not tappable.</td></tr>
</table>
<p class="secd" style="margin-top:10px">Feedback is immediate (150 ms cross-fade of fill + sign, no movement, light haptic tick). Chips never re-sort when toggled. Nothing is saved until the card footer.
Footer: all on → <b>Apply N changes</b> (plural); some skipped → <b>Apply n of N</b>; all skipped → one button <b>Keep as is</b> + “All changes skipped”. <b>Reject</b> is always “keep this garment’s tags exactly as they are”, whatever the chips show.
Why “Apply 4 of 6” and not “Apply 3 · keep 1 · leave out 1”: with mixed skips the three-part label does not fit in German (“3 übernehmen · 1 behalten · 1 weglassen”); the chips already show which ones, the button only needs the count.</p>"""
    return (head("Robia · garment review redesign · round 3")
            + '<h1>Garment review: round 3 · the add / remove language</h1>'
            + '<p class="lead">Manu (round 2): “el A va en la dirección correcta. […] no queda muy claro cuál es la etiqueta que se quita. La que se añade queda muy claro con ese verde. '
              '¿Quizás se puede hacer lo mismo con el icono ‘−’ y en rojo para las que se quitan?” Direction A “Pick, then decide” is decided; this round only perfects how a card says '
              '<b>adds</b>, <b>removes</b>, <b>keeps</b> and <b>swaps</b>. Everything else (save per garment, list shrinks, Undo, “Reviewed · N”, completion) is round 2 as approved. '
              'Rounds 1 and 2: <a href="../review-redesign/index.html">../review-redesign/</a>, <a href="../review-redesign-2/index.html">../review-redesign-2/</a>.</p>'
            + '<div class="reco"><p><b>Recommendation: Signed chips, sorted.</b> Yes to Manu’s idea, with three fixes that make it survive without colour.</p>'
              '<p>① Every change chip has a <b>filled sign circle</b>: green <b>+</b> for added, red <b>−</b> for removed, and removed tags are <b>struck through</b>. '
              '② Fixed <b>reading order</b>: category swap, then removals, then additions, so red never interleaves with green. '
              '③ One rule for skipping: <b>filled sign = will happen, hollow sign = skipped by you</b>. A skipped add becomes a dashed ghost (won’t be there); a kept removal becomes a plain chip (stays). '
              'Unchanged tags are never chips: they live in the “Now:” line.</p>'
              '<p>Card buttons: <b>Reject</b> = keep the garment exactly as it is. <b>Apply N changes</b> / <b>Apply n of N</b> = apply what is filled. All skipped → one <b>Keep as is</b>. '
              'Alternative, also fully built: <b>Labelled rows</b> (“− Removes” / “+ Adds” headers), clearer in words but taller and redundant on single-add cards.</p></div>'
            + DARK_NOTE
            + f'<h2 class="sec">1 · Five visual languages explored</h2><p class="secd">Same garment in all five: a mixed proposal (as after deleting or merging tags) with 4 adds, 2 removes and 1 unchanged tag (Business), the hardest case for red next to green.</p>'
              f'<div class="sketches">{sk}</div>'
            + f'<h2 class="sec">2 · The converged language</h2><p class="secd">Every chip state, light and dark. The text column states the meaning without referring to colour.</p>{leg}'
            + f'<h2 class="sec">3 · Tap behaviour</h2>{tap}'
            + '<h2 class="sec">4 · Colour-blind check</h2><p class="secd">Screenshots of the built phones, transformed with Machado et al. (2009) full-severity matrices in linear RGB, plus luminance only. '
              'Bottom row is a <b>control, not a proposal</b>: the same chips with colour as the only cue (no sign, no strike). In the control the removed and added chips merge under protanopia / deuteranopia / grey-scale; '
              'in the recommendation they stay distinct by the − / + shape, the strike line and the order.</p>'
              '<div class="cvd"><img src="cvd-strip.png" alt="Colour-blind simulation strip"></div>'
            + f'<h2 class="sec">5 · Contrast</h2><p class="secd">WCAG 2.x ratios computed from the tokens by build.py. Requirement: ≥ 4.5:1 for both colours against the card, in both themes.</p>{contrast_table()}'
            + f'<h2 class="sec">6 · Recommended vs alternative</h2>{cmp}'
            + side
            + f'<h2 class="sec">7 · Card copy (EN / ES / DE)</h2><p class="secd">Plurals are Android quantity strings (<code>_one</code> / <code>_other</code>). Content descriptions make each chip a switch for TalkBack.</p>{copy}'
            + '<h2 class="sec">8 · Open questions for Manu</h2><div class="diag"><ol>'
              '<li><b>Left button name.</b> (a) <b>Reject</b> / Rechazar / Ablehnen, as in round 2 <i>(recommended: explicit, as you asked)</i>; '
              '(b) <b>Keep as is</b> / Dejar como está / Unverändert lassen, which names the result and already appears when everything is skipped.</li>'
              '</ol><p>Settled in code (<code>restoredTagChangeSet</code>): <b>restoring defaults never removes custom tags</b>. It only adds defaults; a custom tag merged into a default '
              'shows as one swap “− Weekend away → + Casual”. Removal-only cards come from deleting or merging a tag or changing a category.</p></div>'
            + '<h2 class="sec">Pages</h2><ul>' + "".join(f'<li><a href="{s}.html">{e(ti)}</a> — 7 states, light + dark, string tables</li>' for s, ti, _, _ in DESIGNS) + '</ul>'
            + '<p class="secd">Built by <code>build.py</code> (imports round 2’s, which imports round 1’s). PNGs: one per phone '
              '(<code>&lt;page&gt;-NN-&lt;state&gt;-light|dark.png</code>), page slices, <code>variants-*.png</code>, <code>cvd-strip.png</code>.</p>'
            + TAIL)


# ---------------------------------------------------------------- screenshots
def frames_shot(cells, names, cols=4):
    """Render phones on a fixed grid, screenshot once, crop each at known coordinates. Returns the PIL images."""
    from PIL import Image
    cw, ch, pad = 400, 780, 20
    rows = (len(cells) + cols - 1) // cols
    body = "".join(f'<div class="fr" style="left:{pad + (k % cols) * cw}px;top:{pad + (k // cols) * ch}px">{c}</div>' for k, c in enumerate(cells))
    doc = (f'<!doctype html><html><head><meta charset="utf-8"><style>{CSS}{theme_css()}body{{background:#e9e6e3}}.phone{{box-shadow:none}}</style></head>'
           f'<body style="margin:0"><div class="frames">{body}</div></body></html>')
    out = {}
    with tempfile.TemporaryDirectory() as d:
        f = Path(d) / "frames.html"
        f.write_text(doc, encoding="utf-8")
        shot = Path(d) / "frames.png"
        r2.shoot(f.as_uri(), shot, pad * 2 + cols * cw, pad * 2 + rows * ch + 120)
        im = Image.open(shot).convert("RGB")
        for k, n in enumerate(names):
            x, y = pad + (k % cols) * cw, pad + (k // cols) * ch
            out[n] = im.crop((x, y, x + 360, y + 740))
    return out


def phone_shots(slug, scr):
    for old in OUT.glob(f"{slug}-[0-9][0-9]-*.png"):
        old.unlink()
    cells, names = [], []
    for i, (name, fn, _) in enumerate(scr(), 1):
        light, dark, _ = render_pair(fn)
        stem = re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")[:40].strip("-")
        for th, ph in (("light", light), ("dark", dark)):
            cells.append(ph)
            names.append(f"{slug}-{i:02d}-{stem}-{th}.png")
    for n, im in frames_shot(cells, names).items():
        im.save(OUT / n)
    print(f"shot {len(names)} phones for {slug}")


CVD = {  # Machado, Oliveira & Fernandes 2009, severity 1.0 (linear RGB)
    "Protanopia": [[0.152286, 1.052583, -0.204868], [0.114503, 0.786281, 0.099216], [-0.003882, -0.048116, 1.051998]],
    "Deuteranopia": [[0.367322, 0.860646, -0.227968], [0.280085, 0.672501, 0.047413], [-0.011820, 0.042940, 0.968881]],
}


def simulate(im, kind):
    import numpy as np
    from PIL import Image
    a = np.asarray(im).astype(np.float64) / 255
    lin = np.where(a <= 0.04045, a / 12.92, ((a + 0.055) / 1.055) ** 2.4)
    if kind == "Grey-scale":
        y = lin @ np.array([0.2126, 0.7152, 0.0722])
        lin = np.repeat(y[..., None], 3, axis=2)
    else:
        lin = np.clip(lin @ np.array(CVD[kind]).T, 0, 1)
    srgb = np.where(lin <= 0.0031308, lin * 12.92, 1.055 * lin ** (1 / 2.4) - 0.055)
    return Image.fromarray((np.clip(srgb, 0, 1) * 255 + 0.5).astype("uint8"))


def cvd_strip():
    """Card crops of the mixed adds + removes and skipped states (light, dark) and a colour-only control, × 4 vision types."""
    from PIL import Image, ImageDraw, ImageFont
    global LANG, USED
    LANG, USED = "en", []
    scr = screens(card_signed, cvd=True)  # the mixed adds + removes card, so the control row compares the same chips
    restore, skipped = scr[1][1], scr[4][1]
    cells = [restore("light"), restore("dark"), skipped("light"), control_colour_only("light")]
    names = ["Recommended · light", "Recommended · dark", "Recommended · skipped states", "CONTROL: colour only (not a proposal)"]
    shots = frames_shot(cells, names)
    crops = {"Recommended · light": (205, 572), "Recommended · dark": (205, 572), "Recommended · skipped states": (140, 490),
             "CONTROL: colour only (not a proposal)": (140, 470)}
    kinds = ["Normal vision", "Protanopia", "Deuteranopia", "Grey-scale"]
    font = ImageFont.load_default(size=15)
    cw, lw, gap = 360, 230, 12
    rows = []
    for n in names:
        y0, y1 = crops[n]
        rows.append((n, shots[n].crop((0, y0, 360, y1))))
    H = 34 + sum(r[1].height + gap for r in rows)
    W = lw + len(kinds) * (cw + gap)
    out = Image.new("RGB", (W, H), "#e9e6e3")
    d = ImageDraw.Draw(out)
    for j, k in enumerate(kinds):
        d.text((lw + j * (cw + gap) + 8, 8), k, fill="#332421", font=font)
    y = 34
    for n, crop in rows:
        d.multiline_text((8, y + 8), n.replace(" · ", "\n").replace(": ", ":\n"), fill="#332421", font=font, spacing=4)
        for j, k in enumerate(kinds):
            out.paste(crop if k == "Normal vision" else simulate(crop, k), (lw + j * (cw + gap), y))
        y += crop.height + gap
    out.save(OUT / "cvd-strip.png")
    print("wrote cvd-strip.png")


def variant_shots():
    global LANG, USED
    LANG, USED = "en", []
    cells, names = [], []
    for i, (name, _, fn, _, _) in enumerate(VARIANTS, 1):
        stem = re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")[:30].strip("-")
        cells.append(fn("light"))
        names.append(f"variants-{stem}.png")
    for old in OUT.glob("variants-*.png"):
        old.unlink()
    for n, im in frames_shot(cells, names, cols=5).items():
        im.save(OUT / n)
    print(f"shot {len(names)} variants")


def page_shot(page, slice_h=2400):
    from PIL import Image, ImageChops
    png = page.with_suffix(".png")
    for old in OUT.glob(page.stem + "-p*.png"):
        old.unlink()
    r2.shoot(page.as_uri(), png, 1640, 16000)
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


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--shots", action="store_true")
    args = ap.parse_args()
    pages = []
    for slug, title, pitch, scr in DESIGNS:
        p = OUT / f"{slug}.html"
        p.write_text(design_page(slug, title, pitch, scr), encoding="utf-8")
        pages.append((p, slug, scr))
        print("wrote", p.name)
    p = OUT / "index.html"
    p.write_text(index_page(), encoding="utf-8")
    print("wrote", p.name)
    if args.shots:
        if not r2.chrome():
            sys.exit("chromium not found")
        for _, slug, scr in pages:
            phone_shots(slug, scr)
        variant_shots()
        cvd_strip()
        for pg in [p] + [x[0] for x in pages]:
            page_shot(pg)


if __name__ == "__main__":
    main()
