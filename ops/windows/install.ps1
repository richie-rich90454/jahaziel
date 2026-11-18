# Copyright 2026 The Jahaziel Authors
# SPDX-License-Identifier: Apache-2.0
# Registers the jar-adjacent Jahaziel deployment as a Windows service.
# Usage: .\install.ps1 -JarDir <directory-containing-jahaziel.jar> -WinSw <path-to-winsw.exe>

param(
    [Parameter(Mandatory = $true)][string]$JarDir,
    [Parameter(Mandatory = $true)][string]$WinSw
)

$ErrorActionPreference = "Stop"
$jar = Join-Path $JarDir "jahaziel.jar"
$template = Join-Path $PSScriptRoot "jahaziel-service.xml.template"

if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
    throw "missing $jar"
}
if (-not (Test-Path -LiteralPath $WinSw -PathType Leaf)) {
    throw "missing WinSW executable: $WinSw"
}
if (-not (Test-Path -LiteralPath $template -PathType Leaf)) {
    throw "missing $template"
}

New-Item -ItemType Directory -Force -Path (Join-Path $JarDir "data\logs") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $JarDir "data\tmp") | Out-Null

$resolved = (Resolve-Path -LiteralPath $JarDir).Path
$config = Join-Path $JarDir "jahaziel-service.xml"
(Get-Content -LiteralPath $template -Raw).Replace("__JAR_DIR__", $resolved) |
    Set-Content -LiteralPath $config -Encoding UTF8

& $WinSw install $config
if ($LASTEXITCODE -ne 0) {
    throw "WinSW install failed with exit code $LASTEXITCODE"
}
& $WinSw start $config
if ($LASTEXITCODE -ne 0) {
    throw "WinSW start failed with exit code $LASTEXITCODE"
}
Write-Host "registered Jahaziel service from $resolved"
