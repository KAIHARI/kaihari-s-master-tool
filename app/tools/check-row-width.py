#!/usr/bin/env python3
"""Catches a child of a Row that asks for the whole row's width.

Written after doing it, twice in one file: both note sheets put a text box
beside a card in a Row and gave the box `fillMaxWidth()`. Inside a Row that
measures against the row's own width rather than against what is left of it, so
the box runs out past the card standing next to it. `weight(1f)` is what was
meant, every time.

Compose accepts both, so this is not a compile error anywhere -- it is a layout
that looks broken on a screen this environment does not have. That is the same
reason the other three checks here exist.

The rule is deliberately narrow, because `Row(modifier = Modifier.fillMaxWidth())`
is correct and extremely common: only a `fillMaxWidth()` that appears more than a
few lines after the `Row(` that encloses it is reported, since a Row's own
modifier is written at its head. False negatives are fine; a check nobody trusts
is a check nobody runs.

    ./tools/check-row-width.py            # from app/
"""

import pathlib
import re
import sys

LAYOUT = re.compile(r"\b(Row|Column|Box|FlowRow)\s*\(")

# How far past a `Row(` a modifier can appear and still plausibly be the Row's
# own. Its modifier is the first or second argument in every call in this
# codebase; four lines is generous.
HEAD = 4


def suspects(path):
    lines = path.read_text(encoding="utf-8").splitlines()
    out = []
    stack = []  # (name, depth at entry, line number)
    depth = 0

    for number, line in enumerate(lines, start=1):
        enclosing = stack[-1] if stack else None
        if (
            "fillMaxWidth()" in line
            and enclosing is not None
            and enclosing[0] in ("Row", "FlowRow")
            and number - enclosing[2] > HEAD
        ):
            out.append((number, line.strip(), enclosing[2]))

        found = LAYOUT.search(line)
        if found:
            stack.append((found.group(1), depth, number))

        depth += line.count("{") + line.count("(") - line.count("}") - line.count(")")
        while stack and depth <= stack[-1][1]:
            stack.pop()

    return out


def main():
    roots = [pathlib.Path("ui/src"), pathlib.Path("androidApp/src"), pathlib.Path("desktopApp/src")]
    files = [f for root in roots if root.exists() for f in root.rglob("*.kt")]

    total = 0
    for path in sorted(files):
        for number, text, row in suspects(path):
            total += 1
            print(f"{path}:{number}: fills the width of the Row opened at line {row}")
            print(f"    {text}")
            print("    a child of a Row wants weight(1f); fillMaxWidth() takes the whole row")

    print(f"{total} suspect(s) across {len(files)} files.")
    return 1 if total else 0


if __name__ == "__main__":
    sys.exit(main())
