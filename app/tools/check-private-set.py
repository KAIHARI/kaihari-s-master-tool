#!/usr/bin/env python3
"""Catches an assignment to a property whose setter is private.

Written after doing it in a test. `DeckBuilderState.index` is a `var` with
`private set` — correct, because the card pool is loaded and never handed in —
and a test tried to seed it. That is not a warning, it is a compile error, and
in this environment `:ui` does not compile, so it cost a full CI round trip to
find out.

The other checks here catch a name that does not resolve. This one catches a name
that resolves perfectly and cannot be written to, which is a different mistake
with the same cost.

The rule is narrow on purpose. Only a qualified assignment is reported —
`something.index = …` — because an unqualified `index = …` inside the declaring
class is the legal case and by far the common one. And a file that declares the
property itself is skipped entirely rather than parsed for scope, which is a
whole Kotlin front end nobody here is writing.

    ./tools/check-private-set.py            # from app/

False negatives are fine. A check nobody trusts is a check nobody runs.
"""

from __future__ import annotations

import os
import re
import sys

ROOTS = ["ui/src", "core/src"]

DECLARATION = re.compile(r"^\s*(?:internal\s+|public\s+)?var\s+(\w+)\b")
PRIVATE_SETTER = re.compile(r"^\s*private set\s*$")


def kotlin_files() -> list[str]:
    found = []
    for root in ROOTS:
        for directory, _, names in os.walk(root):
            found += [os.path.join(directory, n) for n in names if n.endswith(".kt")]
    return sorted(found)


def main() -> int:
    files = kotlin_files()
    if not files:
        print("no Kotlin sources found; run this from app/", file=sys.stderr)
        return 2

    sources = {path: open(path, encoding="utf-8").read() for path in files}

    # name -> the files that declare it with a private setter.
    sealed: dict[str, set[str]] = {}
    for path, text in sources.items():
        lines = text.splitlines()
        for number, line in enumerate(lines):
            found = DECLARATION.match(line)
            if found and number + 1 < len(lines) and PRIVATE_SETTER.match(lines[number + 1]):
                sealed.setdefault(found.group(1), set()).add(path)

    problems = []
    for path, text in sources.items():
        for name, owners in sealed.items():
            # The declaring file is allowed to write to it, and working out
            # whether some *other* class in the same file may is a job for a
            # compiler. Skipping it costs a rare miss and no false alarms.
            if path in owners:
                continue

            for match in re.finditer(r"\.(%s)\s*=(?!=)" % re.escape(name), text):
                number = text[: match.start()].count("\n") + 1
                problems.append((path, number, name, sorted(owners)[0]))

    for path, number, name, owner in problems:
        print(f"{path}:{number}: `{name}` is assigned, and its setter is private")
        print(f"    declared in {owner}")

    print(f"{len(problems)} suspect(s) across {len(files)} files.")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
