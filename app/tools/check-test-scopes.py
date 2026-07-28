#!/usr/bin/env python3
"""Catch a `:ui` test that forgot to be a coroutine test.

`builderState()` is an extension on `TestScope`, so a `@Test fun x() { ... }`
that calls it does not compile — the receiver is missing. That is a whole
category of mistake this environment cannot find on its own: `:ui` needs
androidx, androidx needs dl.google.com, and dl.google.com is refused at the
proxy, so the first compiler to see these files is the one in CI four minutes
after the push.

So: every test in a file that has coroutine tests must be one, unless it never
touches the scope. The rule is narrow on purpose — it only fires on a `@Test`
whose body reaches for something that needs the receiver.
"""

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent

# Anything only reachable through the TestScope receiver.
SCOPED = ("builderState(", "testScheduler", "advanceUntilIdle(", "runCurrent(")

TEST_FUN = re.compile(r"^\s*fun\s+(\w+)\s*\([^)]*\)\s*(=\s*\w+\s*\{|\{)", re.M)


def main() -> int:
    problems = []
    files = sorted(ROOT.glob("*/src/*Test/kotlin/**/*.kt"))

    for path in files:
        source = path.read_text()
        lines = source.splitlines()

        for index, line in enumerate(lines):
            if line.strip() != "@Test":
                continue

            header = next(
                (lines[i] for i in range(index + 1, min(index + 4, len(lines)))
                 if "fun " in lines[i]),
                None,
            )
            if header is None or "runTest" in header:
                continue

            # The body: up to the next @Test or the end of the class.
            body = []
            for following in lines[index + 1:]:
                if following.strip() == "@Test":
                    break
                body.append(following)
            body_text = "\n".join(body)

            if any(marker in body_text for marker in SCOPED):
                name = header.strip()
                problems.append(f"{path.relative_to(ROOT)}: {name} needs `= runTest {{`")

    for problem in problems:
        print(problem)

    print(f"{len(problems)} suspect(s) across {len(files)} test files.")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
