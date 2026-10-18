#!/usr/bin/env python3
"""Move-only proof for the ui/ split (U00).

Usage:
  declarations.py <ref>            print "<name> <sha1> <private|internal|public>" per top-level declaration under ui/
  declarations.py compare A B      print EQUAL, or DUP-OK <name> for extra private copies, or the differing names

A top-level declaration starts at a column-0 line that opens a declaration (KDoc, annotation or
visibility/keyword line) and ends before the next such start or EOF. Visibility is ignored:
a leading `private ` or `internal ` is removed before hashing; trailing whitespace is stripped.
Imports and `package` lines are ignored.
"""
import hashlib
import re
import subprocess
import sys
from collections import Counter

UI_DIR = "app/src/main/java/com/gusanitolabs/robia/ui"

MODIFIERS = (
    r"(?:(?:private|internal|public|protected)\s+)?"
    r"(?:(?:data|sealed|enum|annotation|abstract|open|const|inline|operator|infix|tailrec|suspend|external|value|lateinit)\s+)*"
)
DECL_KEYWORD = re.compile(MODIFIERS + r"(?:fun|val|var|class|object|interface|typealias)\b")
VISIBILITY = re.compile(r"^(private|internal) ")
NAME = re.compile(MODIFIERS + r"(?:fun|val|var|class|object|interface|typealias)\s+(?:<[^>]*>\s*)?([^(:={]*)")


def git_show(ref, path):
    return subprocess.run(["git", "show", f"{ref}:{path}"], check=True, capture_output=True, text=True).stdout


def ui_files(ref):
    out = subprocess.run(
        ["git", "ls-tree", "-r", "--name-only", ref, "--", UI_DIR],
        check=True, capture_output=True, text=True,
    ).stdout.split()
    return [p for p in out if p.endswith(".kt")]


def last_identifier(fragment):
    names = re.findall(r"[A-Za-z_]\w*", fragment)
    return names[-1] if names else None


def split_declarations(text):
    chunks = []
    current = []
    decl_seen = False
    for line in text.splitlines():
        if line.startswith(("package ", "import ", "@file:")):
            continue
        if line.startswith((" ", "\t")) or line.strip() == "" or line.startswith(("}", ")", "]")):
            current.append(line)
            continue
        if line.startswith(("/*", "//", "@")) or DECL_KEYWORD.match(line):
            if decl_seen:
                chunks.append(current)
                current = []
                decl_seen = False
            if DECL_KEYWORD.match(line):
                decl_seen = True
        current.append(line)
    if current:
        chunks.append(current)

    declarations = []
    for chunk in chunks:
        lines = list(chunk)
        while lines and lines[0].strip() == "":
            lines.pop(0)
        while lines and lines[-1].strip() == "":
            lines.pop()
        decl_line = next((l for l in lines if DECL_KEYWORD.match(l)), None)
        if decl_line is None:
            continue
        visibility = "public"
        m = VISIBILITY.match(decl_line)
        if m:
            visibility = m.group(1)
        name_match = NAME.match(decl_line)
        name = last_identifier(name_match.group(1)) if name_match else None
        normalised = "\n".join(VISIBILITY.sub("", l, count=1).rstrip() for l in lines)
        declarations.append((name, hashlib.sha1(normalised.encode("utf-8")).hexdigest(), visibility))
    return declarations


def declarations_at(ref):
    result = []
    for path in ui_files(ref):
        result.extend(split_declarations(git_show(ref, path)))
    return result


def list_mode(ref):
    for name, digest, visibility in sorted(declarations_at(ref), key=lambda d: (d[0] or "", d[1])):
        print(f"{name} {digest} {visibility}")


def compare_mode(base, head):
    before = Counter((n, d) for n, d, _ in declarations_at(base))
    after_rows = declarations_at(head)
    after = Counter((n, d) for n, d, _ in after_rows)
    private_copies = Counter((n, d) for n, d, v in after_rows if v == "private")

    differing = []
    dup_ok = []
    for key in set(before) | set(after):
        b, a = before[key], after[key]
        if a == b:
            continue
        if a > b and a - b <= private_copies[key]:
            dup_ok.extend([key[0]] * (a - b))
        else:
            differing.append(key[0] or "<unnamed>")

    for name in sorted(dup_ok):
        print(f"DUP-OK {name}")
    if differing:
        for name in sorted(set(differing)):
            print(f"DIFF {name}")
        sys.exit(1)
    print("EQUAL")


if __name__ == "__main__":
    args = sys.argv[1:]
    if len(args) == 3 and args[0] == "compare":
        compare_mode(args[1], args[2])
    elif len(args) == 1:
        list_mode(args[0])
    else:
        print(__doc__, file=sys.stderr)
        sys.exit(2)
