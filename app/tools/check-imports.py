#!/usr/bin/env python3
"""Find uses of a module's own top-level symbols that are missing an import.

Why this exists
---------------
`androidx` is served only from Google's Maven. Where that is unreachable, `:ui`
does not compile at all, so CI is the only thing that will ever tell you a name
does not resolve — and a round trip to learn you forgot one `import` line is a
poor trade. This catches that one mistake before the push.

It is not a compiler and does not try to be. It knows the failure that actually
happens when a shared symbol is introduced or moved: a file uses a top-level
declaration from another package in the same build and never imports it.

It does **not** know library names. A missing `import androidx.compose.runtime.
remember` is invisible here, and generalising to catch it was tried and taken
back out: learning name-to-package from the codebase's own import lines is the
trick `check-modifiers.py` uses, but unscoped it collides with Kotlin's `get`
and `set` accessors, with every property called `size` or `key`, and with member
names generally -- forty-four reports, none of them real. `check-modifiers`
works because `Modifier.x` is one syntactic position where the answer is not
ambiguous. There is no such position for names in general.

    python3 tools/check-imports.py

To stay useful it has to stay quiet, so it deliberately ignores:

  - comments and string literals, or every mention of "the Main Deck" in prose
    would look like a use of `Deck`;
  - any name the file also binds itself — a parameter or local called `accent`
    is not a reference to a top-level `accent` elsewhere;
  - anything after a dot, which is a member: `SwissColors.accent` and
    `PointerEventType.Release` name no top-level symbol;
  - anything the file already imports under that name from anywhere, so
    Material's `Card` is not mistaken for the project's;
  - names declared in more than one package, which it cannot resolve anyway.

Exits non-zero if it finds anything. A report is something to check, not a
verdict.
"""

from __future__ import annotations

import os
import re
import sys

ROOTS = ["core/src/commonMain/kotlin", "ui/src/commonMain/kotlin"]

PACKAGE = re.compile(r"^package\s+([\w.]+)", re.M)


# Top-level declarations only — anchored at column zero, because an indented
# `val` is a member or a local and neither is imported by name. `private` is
# deliberately absent: a private top-level name is file-scoped, so putting one
# in the shared map makes every other file's use of that name -- including
# Kotlin's own `Set` -- look like a missing import.
DECLARATION = re.compile(
    r"^(?:@\w+(?:\([^)]*\))?\s*)*"
    r"(?:public\s+|internal\s+)?"
    r"(?:expect\s+|actual\s+)?"
    r"(?:(?:data|value|sealed|abstract|open|enum|annotation|const)\s+)*"
    r"(?:class|interface|object|fun|val|var)\s+"
    r"(?:<[^>]+>\s+)?"
    r"(?:[\w.]+\.)?"            # an extension receiver, e.g. `fun Modifier.foil`
    r"(\w+)",
    re.M,
)

# Anywhere the file binds a name itself: locals, parameters, named arguments,
# destructured components. Over-broad on purpose — a missed check is better
# than a false alarm, because a checker nobody trusts gets ignored.
LOCALLY_BOUND = re.compile(r"\b(?:val|var)\s+(\w+)|(\w+)\s*(?::|=(?!=))")

KEYWORDS = {"class", "interface", "object", "fun", "val", "var", "get", "set"}


def strip_noise(text: str) -> str:
    """Kotlin with comments and string bodies removed."""
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
    packages: dict[str, str] = {}
    owner: dict[str, str] = {}
    ambiguous: set[str] = set()

    for path, text in sources.items():
        match = PACKAGE.search(text)
        if not match:
            continue
        package = match.group(1)
        packages[path] = package
        for name in DECLARATION.findall(strip_noise(text)):
            if name in KEYWORDS or name.startswith("_"):
                continue
            if owner.setdefault(name, package) != package:
                ambiguous.add(name)

    problems = []
    for path, text in sources.items():
        package = packages.get(path)
        if package is None:
            continue

        imports = set(re.findall(r"^import\s+([\w.]+)", text, re.M))
        wildcards = {i[:-2] for i in imports if i.endswith(".*")}

        body = strip_noise("\n".join(
            line for line in text.splitlines() if not line.startswith("import ")
        ))
        bound = {n for pair in LOCALLY_BOUND.findall(body) for n in pair if n}
        # Not preceded by a dot: after one it is a member, not a top-level name.
        used = set(re.findall(r"(?<![.\w])([A-Za-z_]\w*)\b", body))
        # Already imported under this name from anywhere -- Material has a
        # `Card` too, and it is not this project's.
        spoken_for = {i.rsplit(".", 1)[-1] for i in imports}

        for name in sorted(used - bound):
            home = owner.get(name)
            if home is None or home == package or name in ambiguous:
                continue
            if name in spoken_for:
                continue
            if f"{home}.{name}" in imports or home in wildcards:
                continue
            problems.append(f"{path}: uses {name}, needs `import {home}.{name}`")


    for problem in problems:
        print(problem)

    print(f"{len(problems)} suspect(s) across {len(files)} files.")
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
