"""uiautomator helpers on top of cap.Session. find() returns the centre of the first node whose text / content-desc matches."""
import re, subprocess, time, xml.etree.ElementTree as ET
def dump(s):
    s.sh("uiautomator dump /sdcard/ui.xml >/dev/null 2>&1")
    xml = subprocess.run(["adb","shell","cat /sdcard/ui.xml"],capture_output=True,text=True).stdout
    return ET.fromstring(xml[xml.find("<"):]) if "<hierarchy" in xml else None
def nodes(root):
    for n in root.iter("node"):
        b = re.findall(r"\d+", n.get("bounds",""))
        if len(b)==4:
            x1,y1,x2,y2 = map(int,b); yield n, ((x1+x2)//2,(y1+y2)//2), (x1,y1,x2,y2)
def find(s, *labels, exact=False, retries=3):
    for _ in range(retries):
        root = dump(s)
        if root is not None:
            for n,c,box in nodes(root):
                t=(n.get("text") or ""); d=(n.get("content-desc") or "")
                for l in labels:
                    if (exact and (t==l or d==l)) or (not exact and (l.lower() in t.lower() or l.lower() in d.lower())):
                        return c
        time.sleep(0.8)
    return None
def tap_text(s, *labels, **kw):
    c = find(s, *labels, **kw)
    if not c: raise RuntimeError(f"not found: {labels}")
    s.tap(*c); return c
def texts(s):
    root = dump(s); return [ (n.get("text") or n.get("content-desc")) for n,_,_ in nodes(root) if (n.get("text") or n.get("content-desc"))]
