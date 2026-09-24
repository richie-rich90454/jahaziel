# Copyright 2026 The Jahaziel Authors
# SPDX-License-Identifier: Apache-2.0
#
# Runs the Jahaziel fat jar next to this script. Data lives in .\data
# beside the jar (SQLite file, keys, workspaces); first run creates it.
# Needs Java 25 on PATH. Usage: .\jahaziel.ps1 [dispatcher flags]
param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Args)

$jar = Join-Path $PSScriptRoot 'jahaziel.jar'
if (-not (Test-Path $jar)) {
  Write-Error "jahaziel.jar not found next to $PSCommandPath"
  exit 1
}
& java --enable-native-access=ALL-UNNAMED -jar $jar @Args
exit $LASTEXITCODE
