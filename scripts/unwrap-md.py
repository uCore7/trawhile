#!/usr/bin/env python3
"""
unwrap-md.py — join hard-wrapped continuation lines in Markdown files.

Within each paragraph block (delimited by blank lines), continuation lines
are joined into a single line. The following are never touched:
  - fenced code blocks (``` or ~~~)
  - headings (lines starting with #)
  - table rows (lines starting with |)
  - list item starters (lines starting with - / * / + / digit.)

Usage:
  python3 scripts/unwrap-md.py                  # process default file list
  python3 scripts/unwrap-md.py file1.md file2.md
"""

import re
import sys
import os

DEFAULT_FILES = [
    "docs/requirements-ur.md",
    "docs/requirements-sr.md",
    "docs/glossary.md",
    "docs/architecture.md",
    "CLAUDE.md",
]


def unwrap(text: str) -> str:
    lines = text.split("\n")
    result = []
    i = 0
    n = len(lines)
    in_code = False

    while i < n:
        line = lines[i]
        stripped = line.strip()

        # Toggle fenced code block tracking
        if stripped.startswith("```") or stripped.startswith("~~~"):
            in_code = not in_code
            result.append(line)
            i += 1
            continue

        # Inside a code block — preserve verbatim
        if in_code:
            result.append(line)
            i += 1
            continue

        # Blank line
        if not stripped:
            result.append("")
            i += 1
            continue

        # Heading
        if stripped.startswith("#"):
            result.append(line)
            i += 1
            continue

        # Table row
        if stripped.startswith("|"):
            result.append(line)
            i += 1
            continue

        # Everything else: collect + join continuations
        current = line.rstrip()
        i += 1

        while i < n:
            nxt = lines[i]
            nxt_s = nxt.strip()

            if (
                not nxt_s
                or nxt_s.startswith("#")
                or nxt_s.startswith("|")
                or re.match(r"^[-*+] ", nxt_s)
                or re.match(r"^\d+\. ", nxt_s)
                or nxt_s.startswith("```")
                or nxt_s.startswith("~~~")
            ):
                break

            current = current.rstrip() + " " + nxt_s
            i += 1

        result.append(current)

    return "\n".join(result)


def process(path: str) -> None:
    with open(path) as f:
        original = f.read()
    processed = unwrap(original)
    if processed != original:
        with open(path, "w") as f:
            f.write(processed)
        before = original.count("\n")
        after = processed.count("\n")
        print(f"  updated  {path} ({before} → {after} lines)")
    else:
        print(f"  unchanged {path}")


if __name__ == "__main__":
    # Resolve paths relative to the project root (one level up from scripts/)
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    targets = sys.argv[1:] if len(sys.argv) > 1 else [os.path.join(root, f) for f in DEFAULT_FILES]
    for t in targets:
        process(t)
