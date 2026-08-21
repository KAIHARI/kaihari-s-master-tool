#!/usr/bin/env python3
"""
Which of the ported core is actually reachable from the app, and which is not.

The gap this exists to make visible is the one an audit found the hard way: of
~95 ported functions the app called 21, so `mt_drop_resolve` - proved against
2,771 vectors - was dead code, and a card could not be dragged to the graveyard
at all. The conformance suite cannot see that. It proves `src/core/` matches
`:core`; nothing in it proves the app uses `src/core/`.

**Transitively**, and that is the whole difficulty. Counting only what `main.c`
names directly reports `mt_field_to_graveyard` as unreached on the very release
that made it reachable, because the app asks `mt_drop_commit` and the commit
asks that. An audit that cries wolf about the fix is worse than no audit, so
this walks the call graph: a function is reached if the app names it, or if
anything reached names it.

Deliberately syntactic - a brace counter and a word match, no compiler. It over-
reports reachability for a function named inside a branch that never runs, and
that is the safe direction: it will not tell you something is wired when nothing
mentions it.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CORE = sorted((ROOT / "src/core").glob("*.c")) + sorted((ROOT / "src/core").glob("*.h"))
APP = [ROOT / "src/main.c"] + sorted((ROOT / "src/gfx").glob("*.c"))

# Every call, not only the `mt_` ones. `mt_drop_commit` reaches
# `mt_field_to_graveyard` through a file-static `commit_into`, so a graph whose
# only nodes are `mt_*` has a hole exactly where the interesting edges are - and
# reports the graveyard as unreachable on the release that made it reachable.
CALL = re.compile(r"\b([A-Za-z_][A-Za-z0-9_]*)\s*\(")
KEYWORDS = {"if", "while", "for", "switch", "return", "sizeof", "do", "else", "case"}

# A definition, as opposed to a declaration: a name, a parameter list, and an
# opening brace before the semicolon that would end a prototype.
DEFN = re.compile(r"^[A-Za-z_][A-Za-z0-9_ \*]*?\b([A-Za-z_][A-Za-z0-9_]*)\s*\([^;]*?\)\s*\{", re.M | re.S)


def bodies(text):
    """Every function definition in `text`, as name -> body source."""
    out = {}
    for m in DEFN.finditer(text):
        i = text.index("{", m.start())
        depth, j = 0, i
        while j < len(text):
            if text[j] == "{":
                depth += 1
            elif text[j] == "}":
                depth -= 1
                if depth == 0:
                    break
            j += 1
        out.setdefault(m.group(1), "")
        out[m.group(1)] += text[i:j + 1]
    return out


def strip_comments(text):
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    return re.sub(r"//[^\n]*", " ", text)


graph, declared = {}, set()
for path in CORE:
    text = strip_comments(path.read_text())
    for name, body in bodies(text).items():
        graph.setdefault(name, set()).update(set(CALL.findall(body)) - KEYWORDS)
    if path.suffix == ".h":
        # A prototype is the promise; that is what "ported" means here.
        for m in re.finditer(r"^[A-Za-z_][A-Za-z0-9_ \*]*?\b(mt_[a-z0-9_]+)\s*\([^;{]*?\)\s*;",
                             text, re.M | re.S):
            declared.add(m.group(1))
        for name in bodies(text):
            declared.add(name)

seed = set()
for path in APP:
    seed.update(set(CALL.findall(strip_comments(path.read_text()))) - KEYWORDS)

reached, queue = set(), list(seed)
while queue:
    name = queue.pop()
    if name in reached:
        continue
    reached.add(name)
    queue.extend(graph.get(name, ()))

unreached = sorted(declared - reached)
print(f"{len(declared)} ported, {len(declared & reached)} reachable from the app, "
      f"{len(unreached)} not:")
for name in unreached:
    print(f"  {name}")
sys.exit(0)
