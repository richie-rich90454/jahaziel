#!/usr/bin/env bash
#
# Copyright 2026 The Jahaziel Authors
# SPDX-License-Identifier: Apache-2.0
#

# Runs the Jahaziel fat jar next to this script. Data lives in ./data
# beside the jar (SQLite file, keys, workspaces); first run creates it.
# Needs Java 25 on PATH. Usage: ./jahaziel.sh [--run=all] [dispatcher flags]
set -euo pipefail

cd "$(dirname "$0")"
JAR="$(pwd)/jahaziel.jar"

if [ ! -f "$JAR" ]; then
  echo "jahaziel.jar not found next to $0" >&2
  exit 1
fi

if [ "$#" -eq 0 ]; then
  set -- --run=all
fi

exec java --enable-native-access=ALL-UNNAMED -jar "$JAR" "$@"
