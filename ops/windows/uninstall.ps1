# Copyright 2026 The Jahaziel Authors
# SPDX-License-Identifier: Apache-2.0
# Usage: .\uninstall.ps1 -WinSw <path-to-winsw.exe> -JarDir <jar-directory>

param(
    [Parameter(Mandatory = $true)][string]$WinSw,
    [Parameter(Mandatory = $true)][string]$JarDir
)

$ErrorActionPreference = "Stop"
$config = Join-Path $JarDir "jahaziel-service.xml"
if (-not (Test-Path -LiteralPath $config -PathType Leaf)) {
    throw "missing $config"
}
& $WinSw stop $config
& $WinSw uninstall $config
Remove-Item -LiteralPath $config -Force
Write-Host "unregistered Jahaziel service"
