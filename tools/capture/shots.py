"""Shot scripts for the Robia CAP session (docs/specs/robia-round5/creative/shotlist.md). Every adb call goes through
cap.Session -> <session-dir>/commands.jsonl.
Usage: python3 -I tools/capture/shots.py <locale> <session-dir> <shot> [<shot> ...]   (locale: en|es|de)
A relative <session-dir> resolves against $ROBIA_CAPTURE_ROOT (default: current directory)."""
import os, subprocess, sys, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # needed under -I, which drops the script dir from sys.path
from cap import Session, session_root
import ui
from i18n import T

PKG = "com.gusanitolabs.robia.capture"
L = {  # labels per locale (real app strings)
 "search_field": {"en":"Search your wardrobe","es":"Buscar en tu armario","de":"Kleiderschrank durchsuchen"},
 "clear_search": {"en":"Clear search","es":"Borrar búsqueda","de":"Suche löschen"},
 "stats": {"en":"Wardrobe stats","es":"Estadísticas del armario","de":"Kleiderschrank-Statistik"},
 "browse": {"en":"Browse","es":"Explorar","de":"Durchsuchen"},
}
Q = {"Q1":{"en":"something warm for a wedding","es":"algo abrigado para una boda","de":"etwas Warmes zur Hochzeit"},
     "Q2":{"en":"something for a rainy day","es":"algo para cuando llueve","de":"Outfit bei Regenwetter"}}

class Run:
    def __init__(self, root, locale):
        self.s = Session(root); self.loc = locale; self.root = root
        os.makedirs(f"{root}/{locale}/clips", exist_ok=True); os.makedirs(f"{root}/{locale}/stills", exist_ok=True)
        self.manifest_path = f"{root}/manifest.json"
        self.manifest = json.load(open(self.manifest_path)) if os.path.exists(self.manifest_path) else []
    def label(self, k): return L[k][self.loc]
    def t(self, en): return T(en, self.loc)
    def tap_t(self, en, exact=False, retries=3): return ui.tap_text(self.s, self.t(en), exact=exact, retries=retries)
    def shot_png(self, name):
        out=f"{self.root}/{self.loc}/stills/{name}.png"
        open(out,"wb").write(subprocess.run(["adb","exec-out","screencap","-p"],capture_output=True).stdout); return out
    def clip(self, shot, secs, fn):
        self.s.begin(self.loc, shot); remote = f"/sdcard/r5/{self.loc}/{shot}.mp4"
        self.s.sh(f"mkdir -p /sdcard/r5/{self.loc}")
        t0 = __import__("time").time()
        p = subprocess.Popen(["adb","shell",f"screenrecord --bit-rate 16000000 --time-limit {secs} {remote}"])
        self.s.sleep(1.0); self.s._log("record_start", remote, seconds=secs)
        try: fn()
        finally:
            self.s.stop_record(p)
        dur = round(__import__("time").time()-t0,1)
        dst = f"{self.root}/{self.loc}/clips/{shot}.mp4"
        subprocess.run(["adb","pull",remote,dst],capture_output=True); self.s.end()
        self.add(shot,"clip",dst,dur)
    def still(self, shot):
        self.s.begin(self.loc, shot+"-still"); out = self.s.still(shot); self.s.end(); self.add(shot,"still",out,0)
    def add(self, shot, kind, path, secs):
        w = h = None
        try:
            o = subprocess.run(["ffprobe","-v","error","-select_streams","v:0","-show_entries","stream=width,height","-of","csv=p=0",path],capture_output=True,text=True).stdout.strip().split(",")
            w,h = int(o[0]),int(o[1])
        except Exception: pass
        self.manifest = [m for m in self.manifest if not (m["locale"]==self.loc and m["shot"]==shot and m["kind"]==kind)]
        self.manifest.append({"locale":self.loc,"shot":shot,"file":os.path.relpath(path, self.root),"kind":kind,"seconds":secs,"width":w,"height":h})
        json.dump(self.manifest, open(self.manifest_path,"w"), indent=1)
    # --- navigation helpers
    def go_browse(self):
        ui.tap_text(self.s, "Browse", "Explorar", "Durchsuchen", exact=True); self.s.sleep(1.2)
        for _ in range(3): self.s.swipe(540,900,540,2000,200); self.s.sleep(0.3)
    def type_query(self, q, gap=0.15):
        """Type word by word and VERIFY the field text (adb input text can drop characters). Sets self.typed_ok."""
        ui.tap_text(self.s, self.label("search_field")); self.s.sleep(0.8); self.s.text_words(q, gap=gap)
        self.s.sleep(0.6)
        self.typed_ok = any(t.strip() == q for t in ui.texts(self.s))

    # ---------- shots
    def B(self):
        self.go_browse()
        def f():
            s=self.s; s.sleep(1)
            for _ in range(3): s.swipe(540,1800,540,700,500); s.sleep(0.2)
            s.sleep(1); s.swipe(540,1800,540,700,1200); s.sleep(0.8)
            for _ in range(5): s.swipe(540,700,540,2000,150); s.sleep(0.1)
            s.sleep(1)
        self.clip("B", 12, f); self.s.sleep(0.5); self.still("B")
    def D(self):
        self.go_browse(); s=self.s
        for attempt, gap in enumerate([0.15, 0.3, 0.5], 1):
            self.typed_ok = False
            def f():
                s.sleep(1); self.type_query(Q["Q1"][self.loc], gap=gap); s.key(4); s.sleep(3)
                s.swipe(540,1900,540,1100,900); s.sleep(1.2)
            self.clip("D", 20, f)
            s.swipe(540,1100,540,1900,300); s.sleep(0.8)
            if self.typed_ok: break
            print("D: typed text mismatch, retaking (attempt", attempt, ")")
            ui.tap_text(s, self.label("clear_search")); s.sleep(1)
        else:
            raise RuntimeError("D: could not type the query exactly")
        self.still("D")
        ui.tap_text(s, self.label("clear_search")); s.sleep(1)
    def H(self):
        self.go_browse(); s=self.s
        def f():
            s.sleep(0.8); ui.tap_text(s, self.label("stats")); s.sleep(2.5)
            s.swipe(540,1800,540,900,1100); s.sleep(2); s.swipe(540,1800,540,900,1100); s.sleep(1.5)
        self.clip("H", 14, f)
        # still of top of analytics
        s.swipe(540,900,540,1900,300); s.sleep(0.8); self.still("H")
        s.key(4); s.sleep(1)

    def _measure_G(self):
        """Dry pass (not recorded): measure the coordinates this locale needs, then return to Browse."""
        s=self.s; c={}
        c["manage"]=ui.find(s,self.t("Manage"),exact=True); s.tap(*c["manage"]); s.sleep(1.2)
        s.swipe(900,718,150,718,300); s.sleep(0.8)
        for n,cc,b in ui.nodes(ui.dump(s)):
            if (n.get("text") or "").startswith(self.t("Collections")) and cc[1]<800: c["coll"]=cc
        s.tap(*c["coll"]); s.sleep(1.8)
        c["btn"]=[cc for n,cc,b in ui.nodes(ui.dump(s)) if (n.get("content-desc") or n.get("text"))==self.t("Add or remove garments")][0]
        s.tap(*c["btn"]); s.sleep(2)
        c["notin"]=ui.find(s,self.t("Not in"),exact=True)
        s.key(4); s.sleep(1.0)
        ui.tap_text(s,self.t("Browse"),exact=True); s.sleep(1.0)
        return c
    def G(self):
        s=self.s; self.go_browse(); c=self._measure_G(); self.go_browse()
        def f():
            s.tap(*c["manage"]); s.sleep(1.2)
            s.swipe(900,718,150,718,300); s.sleep(0.8)
            s.tap(*c["coll"]); s.sleep(1.8)          # collections list, hold
            s.tap(*c["btn"]); s.sleep(2.0)           # members screen
            s.tap(*c["notin"]); s.sleep(1.4)         # only garments not in the collection
            s.tap(200,1090); s.sleep(0.7); s.tap(540,1090); s.sleep(1.2)   # two tiles -> "added"
            s.tap(*c["undo"]) if "undo" in c else s.tap(661,2167); s.sleep(1.2)
        c["undo"]=None; c.pop("undo")
        self.clip("G", 22, f)
        # leave without saving
        s.key(4); s.sleep(1.0)
        txt=ui.texts(s)
        if self.t("Manage") not in txt: s.key(4); s.sleep(1.0)
        ui.tap_text(s, self.t("Browse"), exact=True); s.sleep(1)
    def J(self):
        s=self.s; self.go_browse()
        def f():
            s.sleep(0.8); s.tap(198,867); s.sleep(2.0)             # tile 1 of the Browse grid
            ui.tap_text(s, self.t("Share item")); s.sleep(2.2)      # Export screen with the Robia frame
            s.sleep(1.5)
            for n,cc,b in ui.nodes(ui.dump(s)):                     # toggle the first "Show" switch off and on
                if n.get("class","").endswith("Switch") or n.get("checkable")=="true":
                    s.tap(*cc); s.sleep(0.9); s.tap(*cc); s.sleep(1.0); break
        self.clip("J", 14, f)
        s.key(4); s.sleep(0.8); s.key(4); s.sleep(0.8)
        for _ in range(2):
            if "Search your wardrobe" in ui.texts(s) or self.t("Search your wardrobe") in ui.texts(s): break
            s.key(4); s.sleep(0.8)
    def K(self):
        s=self.s
        def open_theme():
            ui.tap_text(s, self.t("Open settings")); s.sleep(1.5)
            for n,cc,b in ui.nodes(ui.dump(s)):
                if (n.get("text") or "")==self.t("Theme"): s.tap(*cc); break
            s.sleep(1.2)
        open_theme(); self._dark(); s.sleep(1.5)
        for _ in range(2): s.key(4); s.sleep(0.8)
        self.go_browse()
        def f():
            s.sleep(2); s.swipe(540,1800,540,900,1200); s.sleep(1); s.swipe(540,900,540,1800,400); s.sleep(1)
        self.clip("K", 10, f); self.still("K")
        open_theme(); self._light(); s.sleep(1.5)
        for _ in range(2): s.key(4); s.sleep(0.8)
    def _dark(self):
        cand=[t for t in ui.texts(self.s) if t in ("Oscuro","Dunkel","Dark")]
        ui.tap_text(self.s, cand[0], exact=True)
    def _light(self):
        # ES "Claro", DE "Hell" (string key settings_theme_light is the source; the dialog row text is used)
        txts=ui.texts(self.s); cand=[t for t in txts if t in ("Claro","Hell","Light")]
        ui.tap_text(self.s, cand[0], exact=True)
    def L(self):
        s=self.s; self.go_browse()
        def f():
            s.sleep(0.8); ui.tap_text(s, self.t("Filters"), exact=True); s.sleep(1.5)
            s.swipe(540,1700,540,1000,500); s.sleep(0.8)
        self.clip("L", 10, f)
        ui.tap_text(s, self.t("Reset")); s.sleep(0.8); s.key(4); s.sleep(1)
    def M(self):
        s=self.s; self.go_browse()
        def f():
            ui.tap_text(s, self.t("Open settings")); s.sleep(1.5); ui.tap_text(s, self.t("Smart suggestions")); s.sleep(3)
        self.clip("M", 8, f); self.still("M")
        s.key(4); s.sleep(0.8); s.key(4); s.sleep(0.8)

if __name__ == "__main__":
    if len(sys.argv) < 4 or sys.argv[1] not in ("en", "es", "de"): print(__doc__); sys.exit(2)
    loc, root, *shots = sys.argv[1:]
    r = Run(session_root(root), loc)
    for sh in shots: getattr(r, sh)(); print("done", sh)
