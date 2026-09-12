#!/usr/bin/env python3
# Copyright 2026 The Jahaziel Authors
# SPDX-License-Identifier: Apache-2.0
"""Generate the string-replacement patch from a mapping table.

Walks the upstream tree, replaces runtime brand strings, and writes
patches/0002-rebrand-strings.patch. Review the diff before committing it.
"""
import pathlib
import sys

UPSTREAM = pathlib.Path(__file__).resolve().parent.parent / "upstream"

MAPPING = [
    ("Visual Studio Code", "Jahaziel Code"),
    ("VS Code", "Jahaziel Code"),
    ("vscode", "jahaziel-code"),
    ("Microsoft", "Jahaziel"),
    ("github.com/microsoft/vscode", "github.com/jahaziel-project/jahaziel"),
    ("microsoft.com", "jahaziel.dev"),
    ("aka.ms", "jahaziel.dev"),
    ("go.microsoft.com", "jahaziel.dev"),
]

TEXT_SUFFIXES = {".ts", ".js", ".json", ".html", ".css", ".md", ".xml", ".yml"}

SKIP_DIRS = {".git", "node_modules", "out", ".build"}


def main() -> int:
    if not UPSTREAM.is_dir():
        print("upstream/ checkout missing", file=sys.stderr)
        return 1
    hits = 0
    for path in UPSTREAM.rglob("*"):
        if not path.is_file():
            continue
        if path.suffix not in TEXT_SUFFIXES:
            continue
        if any(part in SKIP_DIRS for part in path.parts):
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        updated = text
        for old, new in MAPPING:
            updated = updated.replace(old, new)
        if updated != text:
            path.write_text(updated, encoding="utf-8")
            hits += 1
    print(f"rebranded {hits} files; inspect with git -C upstream diff")
    return 0


if __name__ == "__main__":
    sys.exit(main())
