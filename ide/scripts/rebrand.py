#!/usr/bin/env python3
#
# Copyright 2026 The Jahaziel Authors
# SPDX-License-Identifier: Apache-2.0
#

"""Generate the string-replacement tree diff for patch 0002.

Walks an upstream checkout, replaces runtime brand strings in place, and
leaves the result in the worktree. Capture it with `git diff` inside the
checkout and store it as patches/0002-rebrand-strings.patch. Review the
diff before committing it.

Longest match wins so compound names resolve before their parts. Lines
that must stay byte-identical for the build (module imports, typings,
URIs, copyright holders, manifests, dependency pins) are skipped, as are
docs, tests, and generated trees.
"""
import argparse
import pathlib
import re
import sys

MAPPING = [
    ("github.com/microsoft/vscode", "github.com/example/jahaziel"),
    ("Microsoft Visual Studio Code", "Jahaziel Code"),
    ("Microsoft VS Code", "Jahaziel Code"),
    ("Visual Studio Code", "Jahaziel Code"),
    ("Code - OSS", "Jahaziel Code"),
    ("go.microsoft.com", "example.com"),
    ("microsoft.com", "example.com"),
    ("aka.ms", "example.com"),
    (".vscode-oss", ".jahaziel-code"),
    ("vscode-oss", "jahaziel-code"),
    ("vscodeoss", "jahazielcode"),
    ("CodeOSS", "JahazielCode"),
    ("code-oss", "jahaziel-code"),
]

WORD_MAPPING = [
    (r"\bMicrosoft\b", "Jahaziel"),
    (r"\bVS Code\b", "Jahaziel Code"),
    (r"\bvscode\b", "jahaziel-code"),
]

TEXT_SUFFIXES = {".ts", ".js", ".json", ".html", ".css", ".xml", ".yml"}

SKIP_DIRS = {".git", "node_modules", "out", ".build", "test", "tests", "dist"}

SKIP_FILES = {
    "package.json",
    "package-lock.json",
    "yarn.lock",
    "product.json",
    "vscode.d.ts",
}

SKIP_LINE = re.compile(
    "|".join(
        [
            r"Copyright",
            r"https?://",
            r"['\"]vscode['\"]",
            r"['\"]vscode-",
            r"@vscode/",
            r"vscode:",
            r"vscode-icon-",
            r"vscode\.dev",
            r"vscode-cdn\.net",
            r'"vscode"\s*:',
            r"\bvscode\.",
            r"\.vscode(?!-oss)",
        ]
    )
)

SKIP_PROPOSED_TYPINGS = re.compile(r"vscode\.proposed\..*\.d\.ts$")

SCOPE_DIRS = {"src", "extensions", "resources"}


def should_visit(path: pathlib.Path, root: pathlib.Path) -> bool:
    if path.suffix not in TEXT_SUFFIXES:
        return False
    if path.name in SKIP_FILES or SKIP_PROPOSED_TYPINGS.search(path.name):
        return False
    parts = path.relative_to(root).parts
    if not parts or parts[0] not in SCOPE_DIRS:
        return False
    return not any(part in SKIP_DIRS for part in parts[:-1])


def rebrand_line(line: str, rules: list[tuple[str, str]], words: list[tuple[re.Pattern[str], str]]) -> str:
    if SKIP_LINE.search(line):
        return line
    for old, new in rules:
        line = line.replace(old, new)
    for pattern, new in words:
        line = pattern.sub(new, line)
    return line


def main() -> int:
    parser = argparse.ArgumentParser(description="rebrand an upstream checkout for patch 0002")
    parser.add_argument(
        "--upstream",
        default=pathlib.Path(__file__).resolve().parent.parent / "upstream",
        type=pathlib.Path,
        help="upstream checkout to rewrite in place",
    )
    args = parser.parse_args()
    root = args.upstream
    if not root.is_dir():
        print("upstream checkout missing", file=sys.stderr)
        return 1
    rules = sorted(MAPPING, key=lambda pair: -len(pair[0]))
    words = [(re.compile(pattern), new) for pattern, new in WORD_MAPPING]
    hits = 0
    for path in sorted(root.rglob("*")):
        if not path.is_file() or not should_visit(path, root):
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        updated_lines = [rebrand_line(line, rules, words) for line in text.split("\n")]
        updated = "\n".join(updated_lines)
        if not text.endswith("\n") and updated.endswith("\n"):
            updated = updated[:-1]
        if updated != text:
            path.write_text(updated, encoding="utf-8")
            hits += 1
    print(f"rebranded {hits} files; inspect with git -C {root} diff")
    return 0


if __name__ == "__main__":
    sys.exit(main())
