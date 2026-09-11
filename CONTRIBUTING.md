<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# Contributing

## One file, one commit

Each commit holds one logical change to one file. Stage with
`git add <exact-file>`; never `git add .`, never `git commit -a`.

Commit messages use `<type>(<scope>): <subject>` with the subject in
imperative mood, lowercase, no period, max 72 characters. Explain why
in the body, not what.

## Green build

Run `./mvnw -q -pl <module> verify` before every commit. Never commit a
broken build. Every file carries the license header; Java code follows
google-java-format. Both are checked by the build.

## Legal

By contributing you agree your work is licensed under the Apache
License 2.0 and you have the right to submit it (Developer Certificate
of Origin 1.1).
