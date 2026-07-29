#!/usr/bin/env python3
"""Catches a Compose extension called without importing it.

Written after doing it three times inside one edit. Wrapping four call sites in
a `Box` meant calling `fillMaxSize()` in files that had never needed it, and
`aspectRatio()` in one that had not — four missing `import` lines, in a module
that does not compile in this environment, which is four CI round trips at ten
minutes each.

`check-imports.py` does not see these. It deliberately ignores anything after a
dot, because after a dot a name is a member — and `Modifier.fillMaxSize()` looks
exactly like one. Extension functions are the hole that leaves, and modifiers are
almost the only thing in this codebase written that way.

No hardcoded list of Compose names. The mapping is read out of the codebase
itself: any `import androidx.…` line anywhere in the module says that this name
comes from that package, and a file calling `.name(` without an import ending in
`.name` is the mistake. So it learns a new modifier the first time one is used
correctly, and it cannot drift out of date against a Compose version.

    ./tools/check-modifiers.py            # from app/

Deliberately quiet, like the others. It ignores:

  - comments and string literals;
  - names the file binds itself, so a local `val padding` is not a call;
  - names declared at top level anywhere in the project, which are the other
    script's business and would be reported twice;
  - names imported under an alias, since the alias is what is written.

False negatives are fine. A check nobody trusts is a check nobody runs.
"""

from __future__ import annotations

import os
import re
import sys

# `:ui` only. `:core` has no androidx on its classpath at all, so a name there
# that happens to match a Compose import -- `Map.getValue` matches the delegate
# operator -- is a false alarm by construction.
ROOTS = ["ui/src/commonMain/kotlin"]

# Delegate operators. They are never written with a dot in ordinary code, so
# every `.getValue(` in this codebase is the standard library's, not Compose's.
DELEGATES = {"getValue", "setValue", "provideDelegate"}

IMPORT = re.compile(r"^import\s+(androidx[\w.]*)\.(\w+)\s*$", re.M)
ANY_IMPORT = re.compile(r"^import\s+([\w.]+)(?:\s+as\s+(\w+))?\s*$", re.M)
PROJECT_DECLARATION = re.compile(
    r"^(?:@\w+(?:\([^)]*\))?\s*)*"
    r"(?:public\s+|internal\s+|private\s+)?"
    r"(?:(?:data|value|sealed|abstract|open|enum|annotation)\s+)*"
    r"(?:class|interface|object|fun|val|var)\s+"
    r"(?:<[^>]+>\s+)?"
    r"(?:[\w.]+\.)?"
    r"(\w+)",
    re.M,
)
LOCALLY_BOUND = re.compile(r"\b(?:val|var)\s+(\w+)|(\w+)\s*(?::|=(?!=))")


def strip_noise(text: str) -> str:
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    text = re.sub(r"//[^\n]*", " ", text)
    text = re.sub(r'"""(?:.|\n)*?"""', '""', text)
    text = re.sub(r'"(?:\\.|[^"\\])*"', '""', text)
    return text


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

    # What the codebase already knows: name -> the androidx packages it has been
    # imported from. A name imported from two places is ambiguous and skipped.
    homes: dict[str, set[str]] = {}
    for text in sources.values():
        for package, name in IMPORT.findall(text):
            homes.setdefault(name, set()).add(package)

    # Anything the project declares itself belongs to the other check.
    ours: set[str] = set()
    for text in sources.values():
        ours.update(PROJECT_DECLARATION.findall(strip_noise(text)))

    known = {
        name: next(iter(packages))
        for name, packages in homes.items()
        if len(packages) == 1
        and name not in ours
        and name not in DELEGATES
        and name[0].islower()
    }

    problems = []
    for path, text in sources.items():
        imported = {
            alias or full.rsplit(".", 1)[-1]
            for full, alias in ANY_IMPORT.findall(text)
        }

        body = strip_noise(
            "\n".join(l for l in text.splitlines() if not l.startswith("import "))
        )
        bound = {n for pair in LOCALLY_BOUND.findall(body) for n in pair if n}

        for number, line in enumerate(body.splitlines(), start=1):
            for name in re.findall(r"\.(\w+)\(", line):
                if name in imported or name in bound or name not in known:
                    continue
                problems.append((path, number, name, known[name]))

    seen = set()
    for path, number, name, package in problems:
        if (path, name) in seen:
            continue
        seen.add((path, name))
        print(f"{path}:{number}: `{name}` is called and never imported")
        print(f"    add `import {package}.{name}`")

    print(f"{len(seen)} suspect(s) across {len(files)} files.")
    return 1 if seen else 0


if __name__ == "__main__":
    sys.exit(main())
