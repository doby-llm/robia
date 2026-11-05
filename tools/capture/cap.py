#!/usr/bin/env python3
"""Robia capture runner. Every adb command it issues is appended to a replay log, so a session can be re-run
against a future build:  python3 cap.py replay <session-dir>.
Library use:  from cap import Session; s = Session("path/to/session"); s.sh("input tap 540 1200"); ...
Commands are logged with their delay since the previous one (seconds), so the replay keeps the timing.
Nothing here contains personal data; photos live outside the repo."""
import json, os, subprocess, sys, time

def session_root(path):
    """Session dir from an argument. Relative paths resolve against $ROBIA_CAPTURE_ROOT (default: current directory)."""
    return os.path.abspath(os.path.join(os.environ.get("ROBIA_CAPTURE_ROOT", "."), path))

class Session:
    def __init__(self, root, serial=None):
        self.root = root; os.makedirs(root, exist_ok=True)
        self.serial = serial; self.log_path = os.path.join(root, "commands.jsonl")
        self.t_last = time.time(); self.shot = None; self.locale = None

    def _adb(self, args):
        base = ["adb"] + (["-s", self.serial] if self.serial else [])
        return base + args

    def _log(self, kind, cmd, **extra):
        now = time.time()
        rec = {"kind": kind, "shot": self.shot, "locale": self.locale, "cmd": cmd,
               "delay": round(now - self.t_last, 3), **extra}
        self.t_last = now
        with open(self.log_path, "a") as f: f.write(json.dumps(rec) + "\n")

    def begin(self, locale, shot):
        self.locale, self.shot = locale, shot; self._log("marker", f"BEGIN {locale}/{shot}")
    def end(self): self._log("marker", f"END {self.locale}/{self.shot}"); self.shot = None

    def sh(self, cmd, check=True):
        """adb shell <cmd> (logged)."""
        self._log("shell", cmd)
        return subprocess.run(self._adb(["shell", cmd]), capture_output=True, text=True, check=check).stdout
    def adb(self, *args, check=True):
        self._log("adb", list(args))
        return subprocess.run(self._adb(list(args)), capture_output=True, text=True, check=check).stdout
    def sleep(self, s): self._log("sleep", s); time.sleep(s)
    def tap(self, x, y): self.sh(f"input tap {x} {y}")
    def swipe(self, x1, y1, x2, y2, ms=300): self.sh(f"input swipe {x1} {y1} {x2} {y2} {ms}")
    def key(self, code): self.sh(f"input keyevent {code}")
    def text_words(self, phrase, gap=0.15, char_gap=0.12):
        """Type char by char (Gboard drops letters when `input text` sends a whole word at once), pause `gap` between words.
        Space = `input keyevent 62`. ASCII only."""
        for i, w in enumerate(phrase.split(" ")):
            for ch in w:
                self.sh(f"input text {ch}"); self.sleep(char_gap)
            if i < len(phrase.split(" ")) - 1:
                self.sh("input keyevent 62"); self.sleep(gap)
    def still(self, name):
        path = os.path.join(self.root, self.locale, "stills"); os.makedirs(path, exist_ok=True)
        out = os.path.join(path, name + ".png"); self._log("screencap", out)
        with open(out, "wb") as f: f.write(subprocess.run(self._adb(["exec-out", "screencap", "-p"]), capture_output=True).stdout)
        return out
    def record(self, name, seconds=30, bitrate=16000000):
        remote = f"/sdcard/r5/{self.locale}/{name}.mp4"
        self.sh(f"mkdir -p /sdcard/r5/{self.locale}")
        self._log("record_start", remote, seconds=seconds)
        return subprocess.Popen(self._adb(["shell", f"screenrecord --bit-rate {bitrate} --time-limit {seconds} {remote}"]))
    def stop_record(self, proc):
        """Finish a screenrecord cleanly (SIGINT on the device) so the mp4 is playable."""
        self.sh("pkill -2 screenrecord", check=False); self.sleep(1.5)
        try: proc.wait(timeout=10)
        except Exception: proc.kill()
    def pull(self):
        dst = os.path.join(self.root, "clips"); os.makedirs(dst, exist_ok=True)
        self.adb("pull", "/sdcard/r5/.", dst)

def replay(root, pace=True):
    """Re-run <root>/commands.jsonl on the connected device: shell and adb commands, and screenrecord starts, are
    re-issued in order; each record waits for its logged delay (capped at 10 s). Stills and pulls are not repeated;
    pull the clips afterwards with: adb pull /sdcard/r5/. <root>/clips"""
    recorders = []
    for line in open(os.path.join(root, "commands.jsonl")):
        r = json.loads(line)
        if pace and r["delay"] > 0 and r["kind"] != "marker": time.sleep(min(r["delay"], 10))
        if r["kind"] == "shell": subprocess.run(["adb", "shell", r["cmd"]])
        elif r["kind"] == "adb": subprocess.run(["adb"] + r["cmd"], capture_output=True)
        elif r["kind"] == "record_start":
            recorders.append(subprocess.Popen(["adb", "shell", f"screenrecord --bit-rate 16000000 --time-limit {r.get('seconds', 30)} {r['cmd']}"]))
        elif r["kind"] == "marker": print(r["cmd"])
    for p in recorders: p.wait()

if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] == "replay": replay(session_root(sys.argv[2]))
    else: print(__doc__)
