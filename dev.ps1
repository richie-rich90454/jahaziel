# Copyright 2026 The Jahaziel Authors
# SPDX-License-Identifier: Apache-2.0
#
# Dev workflow for Windows. Usage: ./dev.ps1 <verb> [args]
# Verbs: bootstrap build build:slim test test:watch run dev reset smoke docs docs:build release clean
Set-StrictMode -Version Latest
$ErrorActionPreference = "Continue"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$Jar = Join-Path $ScriptDir "target\jahaziel.jar"

function Invoke-Mvn($Args) {
  & (Join-Path $ScriptDir "mvnw.cmd") @Args
  return $LASTEXITCODE
}

function Verb-Bootstrap {
  $java = (& java -version 2>&1 | Out-String)
  Write-Output "[i] $java"
  if (($java -notmatch 'version "25')) {
    Write-Warning "Need JDK 25 on PATH."
    exit 1
  }
  $code = Invoke-Mvn @("-version")
  if (($code -ne 0)) { exit $code }
  Write-Output "[OK] bootstrap: JDK 25 + Maven work"
}

function Verb-Build { exit (Invoke-Mvn @("-B", "package")) }
function Verb-BuildSlim { exit (Invoke-Mvn @("-B", "-DskipTests", "package")) }

function Verb-Test($Rest) {
  if (($Rest -and ($Rest.Count -gt 0))) { exit (Invoke-Mvn @("test", "-Dtest=$($Rest -join ',')")) }
  exit (Invoke-Mvn @("test"))
}

function Verb-TestWatch {
  $watcher = New-Object System.IO.FileSystemWatcher
  $watcher.Path = (Join-Path $ScriptDir "src")
  $watcher.Filter = "*.java"
  $watcher.IncludeSubdirectories = $true
  $watcher.EnableRaisingEvents = $true
  Write-Output "[i] watching src/**/*.java, Ctrl+C to stop"
  $action = { Write-Output "[i] change detected, testing..."; Invoke-Mvn @("-q", "test") | Out-Null }
  $handlers = @()
  $handlers += Register-ObjectEvent $watcher "Changed" -Action $action
  $handlers += Register-ObjectEvent $watcher "Created" -Action $action
  try { while ($true) { Start-Sleep -Seconds 1 } }
  finally { $handlers | ForEach-Object { Unregister-Event $_.Name }; $watcher.Dispose() }
}

function Ensure-Jar {
  if (-not (Test-Path $Jar)) {
    Write-Output "[i] target/jahaziel.jar missing, building slim..."
    $code = Invoke-Mvn @("-B", "-DskipTests", "package")
    if (($code -ne 0)) { exit $code }
  }
}

function Verb-Run($Rest) {
  Ensure-Jar
  & java -jar $Jar @Rest
  exit $LASTEXITCODE
}

function Verb-Dev($Rest) {
  Ensure-Jar
  $env:JAHAZIEL_DEMO = "true"
  $all = @("--demo") + $Rest
  & java -Dspring.profiles.active=dev -jar $Jar @all
  exit $LASTEXITCODE
}

function Verb-Reset {
  $answer = Read-Host "Delete data/ (keeps .env)? [y/N]"
  if (($answer -ne "y") -and ($answer -ne "Y")) { Write-Output "[i] reset cancelled"; exit 0 }
  $data = Join-Path $ScriptDir "data"
  if ((Test-Path $data)) { Remove-Item -Recurse -Force $data }
  Write-Output "[OK] reset: data/ removed"
}

function Verb-Smoke {
  Ensure-Jar
  exit (Invoke-Mvn @("-Psmoke", "test", "-Dtest=SmokeTest", "-Djahaziel.jar=$Jar"))
}

function Verb-Docs {
  Write-Output "[i] serving docs/ at http://localhost:8000 (Ctrl+C to stop)"
  Push-Location (Join-Path $ScriptDir "docs")
  try { & python -m http.server 8000 }
  finally { Pop-Location }
}

function Verb-DocsBuild {
  $files = @(Get-ChildItem -Path (Join-Path $ScriptDir "docs") -Filter *.md -Recurse)
  Write-Output "[OK] docs:build: $($files.Count) markdown files present, no generator configured"
}

function Verb-Release {
  $code = Invoke-Mvn @("-B", "-DskipTests", "package")
  if (($code -ne 0)) { exit $code }
  $hash = (Get-FileHash -Algorithm SHA256 $Jar).Hash.ToLower()
  $out = "$Jar.sha256"
  "$hash  jahaziel.jar" | Out-File -FilePath $out -Encoding ASCII
  Write-Output "[OK] release: $Jar"
  Write-Output "[OK] release: $out ($hash)"
}

function Verb-Clean { exit (Invoke-Mvn @("clean")) }

function Usage {
  Write-Output "Usage: ./dev.ps1 <verb> [args]"
  Write-Output "  bootstrap build build:slim test test:watch run dev reset smoke docs docs:build release clean"
}

$Verb = $args[0]
$Rest = @()
if (($args.Count -gt 1)) { $Rest = $args[1..($args.Count - 1)] }
switch ($Verb) {
  "bootstrap" { Verb-Bootstrap }
  "build" { Verb-Build }
  "build:slim" { Verb-BuildSlim }
  "test" { Verb-Test $Rest }
  "test:watch" { Verb-TestWatch }
  "run" { Verb-Run $Rest }
  "dev" { Verb-Dev $Rest }
  "reset" { Verb-Reset }
  "smoke" { Verb-Smoke }
  "docs" { Verb-Docs }
  "docs:build" { Verb-DocsBuild }
  "release" { Verb-Release }
  "clean" { Verb-Clean }
  default { Usage }
}
