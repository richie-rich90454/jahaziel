@echo off
rem Copyright 2026 The Jahaziel Authors
rem SPDX-License-Identifier: Apache-2.0
rem
rem Runs the Jahaziel fat jar next to this script. Data lives in .\data
rem beside the jar (SQLite file, keys, workspaces); first run creates it.
rem Needs Java 25 on PATH. Usage: jahaziel.bat [--run=all] [dispatcher flags]
setlocal
set "JAR=%~dp0jahaziel.jar"
if not exist "%JAR%" (
  echo jahaziel.jar not found next to %0>&2
  exit /b 1
)
if "%~1"=="" (
  java --enable-native-access=ALL-UNNAMED -jar "%JAR%" --run=all
) else (
  java --enable-native-access=ALL-UNNAMED -jar "%JAR%" %*
)
