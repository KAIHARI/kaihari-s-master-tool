#!/usr/bin/env python3
"""Catches an accessor that has been separated from the property it belongs to.

Written after doing it: a block of new members was inserted between

    var selection by mutableStateOf(Selection.EMPTY)
        private set

leaving the `private set` dangling after an unrelated function. Kotlin rejects
it, but this module cannot be compiled in the environment it is written in, so
"Kotlin rejects it" costs a four-minute round trip through CI rather than a red
squiggle. This costs nothing.

The rule is narrow on purpose: a lone `private set` (or `internal set`, or a
`get()` on its own line) must follow something that could be a property, and the
one thing that certainly is not is a closing brace.

    ./tools/check-accessors.py            # from app/
"""

import pathlib
import re
import sys

ACCESSOR = re.compile(r"^(private|internal|protected|public)?\s*(set|get)\s*(\(\))?\s*$")

def suspects(path):
    lines = path.read_text(encoding="utf-8").splitlines()
    out = []
    for i, line in enumerate(lines):
        if not ACCESSOR.match(line.strip()):
            continue
        # Walk back past blanks and comments to whatever this is attached to.
        j = i - 1
        while j >= 0:
            previous = lines[j].strip()
            if previous and not previous.startswith(("//", "*", "/*")):
                break
            j -= 1
        if j < 0:
            out.append((i + 1, line.strip(), "<start of file>"))
        elif lines[j].strip() in ("}", "),") or lines[j].strip().endswith("}"):
            out.append((i + 1, line.strip(), lines[j].strip()))
    return out

def main():
    root = pathlib.Path(".")
    files = sorted(root.glob("*/src/**/*.kt"))
    if not files:
        print("no Kotlin sources found; run this from app/")
        return 1

    found = 0
    for path in files:
        for line_number, accessor, attached_to in suspects(path):
            found += 1
            print(f"{path}:{line_number}: `{accessor}` follows `{attached_to}`")

    print(f"{found} suspect(s) across {len(files)} files.")
    return 1 if found else 0

if __name__ == "__main__":
    sys.exit(main())
