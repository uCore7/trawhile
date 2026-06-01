#!/usr/bin/env python3
"""Renumber ADRs in docs/adr/ to a contiguous 0001..N sequence.

After ADRs are added or removed (for example, retired during triage), the
directory can have gaps. This script closes them: it sorts the ADR files by
their current number, assigns 0001..N preserving that order, and applies the
mapping consistently across:

  * ADR filenames in docs/adr/
  * the "# NNNN. Title" heading line in each ADR
  * cross-reference links between ADRs ("[NNNN. ...](NNNN-slug.md)")
  * references in other tracked docs ("ADR NNNN" and "NNNN-slug.md" links)

Safe by design:
  * dry run by default; pass --apply to write changes
  * idempotent: an already-contiguous corpus produces no changes
  * collision-free renames (staged through temporary names)
  * warns about references to numbers no longer present in docs/adr/
    (likely dangling references to deleted ADRs — fix those by hand first)

Usage:
    python scripts/renumber-adrs.py            # dry run, print the plan
    python scripts/renumber-adrs.py --apply    # apply the renumbering
"""

import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
ADR_DIR = REPO / "docs" / "adr"

# Locations scanned for external references to ADRs.
SCAN_PATHS = ["docs", "tasks", "CLAUDE.md", "AGENTS.md"]

ADR_FILE_RE = re.compile(r"^(\d{4})-(.+)\.md$")
HEADING_RE = re.compile(r"(?m)^#\s+(\d{4})\.\s")   # "# NNNN. " heading line
LINKTEXT_RE = re.compile(r"\[(\d{4})\.\s")         # "[NNNN. " cross-ref link text
TEXTUAL_RE = re.compile(r"\bADR\s+(\d{4})\b")      # "ADR NNNN" prose reference


def fmt(n):
    return f"{n:04d}"


def discover_adrs():
    """Return [(old_num, slug, path)] sorted by current number."""
    adrs = []
    for p in sorted(ADR_DIR.glob("*.md")):
        m = ADR_FILE_RE.match(p.name)
        if m:
            adrs.append((int(m.group(1)), m.group(2), p))
        else:
            print(f"  skipping non-ADR file: {p.name}")
    adrs.sort(key=lambda t: t[0])
    return adrs


def make_remapper(num_map, warnings, fname):
    """Build a re.sub callback that remaps the single 4-digit group of a match."""
    def remap(m):
        old = int(m.group(1))
        whole = m.group(0)
        if old in num_map:
            return whole.replace(m.group(1), fmt(num_map[old]), 1)
        warnings.append(
            f"{fname}: reference to {fmt(old)} is not a current ADR "
            f"(possible dangling reference)"
        )
        return whole
    return remap


def rewrite(text, num_map, file_map, warnings, fname):
    """Apply all reference rewrites to a single file's text."""
    remap = make_remapper(num_map, warnings, fname)
    text = HEADING_RE.sub(remap, text)
    text = LINKTEXT_RE.sub(remap, text)
    text = TEXTUAL_RE.sub(remap, text)
    for old_file, new_file in file_map.items():
        text = text.replace(old_file, new_file)
    return text


def main():
    apply = "--apply" in sys.argv[1:]

    adrs = discover_adrs()
    if not adrs:
        print("No ADRs found in docs/adr/.")
        return 1

    num_map = {old: i for i, (old, _, _) in enumerate(adrs, start=1)}
    file_map = {
        path.name: f"{fmt(num_map[old])}-{slug}.md"
        for old, slug, path in adrs
        if old != num_map[old]
    }

    print(f"Found {len(adrs)} ADRs in docs/adr/.\n")
    print("Plan:")
    moved = 0
    for old, slug, path in adrs:
        new = num_map[old]
        if old == new:
            print(f"  {fmt(old)}-{slug}.md  (unchanged)")
        else:
            print(f"  {fmt(old)}-{slug}.md  ->  {fmt(new)}-{slug}.md")
            moved += 1
    print()

    if moved == 0:
        print("ADRs already contiguous — nothing to do.")
        return 0

    warnings = []

    # New ADR file contents, keyed by their final path.
    adr_writes = {}
    for old, slug, path in adrs:
        new = num_map[old]
        text = rewrite(path.read_text(), num_map, file_map, warnings, path.name)
        adr_writes[ADR_DIR / f"{fmt(new)}-{slug}.md"] = (path, text)

    # External files whose references change.
    ext_writes = {}
    for sp in SCAN_PATHS:
        base = REPO / sp
        if base.is_dir():
            candidates = list(base.rglob("*.md"))
        elif base.is_file():
            candidates = [base]
        else:
            continue
        for f in candidates:
            if ADR_DIR == f.parent:
                continue  # ADR files are handled above
            original = f.read_text()
            updated = rewrite(original, num_map, file_map, warnings,
                              str(f.relative_to(REPO)))
            if updated != original:
                ext_writes[f] = updated

    if ext_writes:
        print("External files with updated references:")
        for f in ext_writes:
            print(f"  {f.relative_to(REPO)}")
        print()

    if warnings:
        print("WARNINGS:")
        for w in warnings:
            print(f"  ! {w}")
        print()

    if not apply:
        print("Dry run. Re-run with --apply to write changes.")
        return 0

    # Apply ADR files: stage through temp names so renames never collide.
    staged = []
    for final, (old_path, text) in adr_writes.items():
        tmp = ADR_DIR / (".renumber-tmp-" + final.name)
        tmp.write_text(text)
        staged.append((tmp, final))
    for old_path, _ in adr_writes.values():
        if old_path.exists():
            old_path.unlink()
    for tmp, final in staged:
        tmp.rename(final)

    # Apply external files.
    for f, text in ext_writes.items():
        f.write_text(text)

    print(f"Applied: {moved} ADRs renumbered, "
          f"{len(ext_writes)} external file(s) updated.")
    if warnings:
        print(f"{len(warnings)} warning(s) above — review manually.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
