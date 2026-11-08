# Copyright 2026 The Jahaziel Authors
# SPDX-License-Identifier: Apache-2.0
#
# Dev workflow for Windows. Usage: ./dev.ps1 <verb> [args]
# Verbs: bootstrap build test test:watch run dev reset smoke docs docs:build release cache:report cache:prune clean
Set-StrictMode -Version Latest
$ErrorActionPreference = "Continue"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$Jar = Join-Path $ScriptDir "target\jahaziel.jar"
$env:LOCALAPPDATA = Join-Path $ScriptDir ".cache\localappdata"
$env:PNPM_HOME = Join-Path $ScriptDir ".cache\pnpm-home"
$env:XDG_CONFIG_HOME = Join-Path $ScriptDir ".cache\xdg-config"
$env:PNPM_CONFIG_STORE_DIR = Join-Path $ScriptDir ".cache\pnpm-home\store"
$env:PNPM_CONFIG_CACHE_DIR = Join-Path $ScriptDir ".cache\pnpm-cache"
$env:PNPM_CONFIG_STATE_DIR = Join-Path $ScriptDir ".cache\pnpm-state"
$env:NPM_CONFIG_CACHE = Join-Path $ScriptDir ".cache\npm-cache"

function Resolve-JdkHome {
  $candidates = @()
  if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }

  $javacCommands = @(Get-Command javac.exe -All -ErrorAction SilentlyContinue)
  foreach ($javac in $javacCommands) {
    if ($javac.Source) {
      $candidates += Split-Path -Parent (Split-Path -Parent $javac.Source)
    }
  }

  $installRoots = @()
  foreach ($base in @($env:ProgramFiles, ${env:ProgramFiles(x86)}, $env:LOCALAPPDATA)) {
    if (-not $base) { continue }
    foreach ($name in @("Java", "Eclipse Adoptium", "Microsoft", "Amazon Corretto", "Zulu", "Programs\Eclipse Adoptium", "Programs\Microsoft")) {
      $installRoots += Join-Path $base $name
    }
  }
  foreach ($root in ($installRoots | Select-Object -Unique)) {
    if (-not (Test-Path -LiteralPath $root -PathType Container)) { continue }
    $candidates += Get-ChildItem -LiteralPath $root -Directory -ErrorAction SilentlyContinue |
      Where-Object { $_.Name -match '(?i)(jdk|temurin|corretto|zulu|microsoft).*25|^25([.-]|$)' } |
      ForEach-Object { $_.FullName }
  }

  foreach ($candidate in ($candidates | Select-Object -Unique)) {
    $jdkPath = [IO.Path]::GetFullPath($candidate)
    $java = Join-Path $jdkPath "bin\java.exe"
    $javac = Join-Path $jdkPath "bin\javac.exe"
    $release = Join-Path $jdkPath "release"
    if ((Test-Path -LiteralPath $java -PathType Leaf) -and
        (Test-Path -LiteralPath $javac -PathType Leaf) -and
        (Test-Path -LiteralPath $release -PathType Leaf) -and
        ((Get-Content -LiteralPath $release -Raw) -match '(?m)^JAVA_VERSION="25(?:\.|")')) {
      return $jdkPath
    }
  }

  throw "JDK 25 is required. Set JAVA_HOME to an existing JDK 25 installation with bin\java.exe and bin\javac.exe; this workflow will not install it."
}

function Get-NodeVersion {
  if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    throw "Node.js 24 or newer is required for the web workbench build. Select an existing Node runtime; this workflow will not install it."
  }
  $version = (& node --version 2>&1 | Out-String).Trim()
  if ($version -notmatch '^v(?<major>[0-9]+)\.') {
    throw "Cannot identify the existing Node.js version '$version'."
  }
  if ([int]$Matches.major -lt 24) {
    throw "Node.js 24 or newer is required for the web workbench build (found '$version'). Select an existing Node runtime; this workflow will not install it."
  }
  return $version
}

function Resolve-MavenExecutable {
  $maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
  if (-not $maven) { $maven = Get-Command mvn -ErrorAction SilentlyContinue }
  if ($maven -and $maven.Source) { return $maven.Source }
  throw "An existing Maven installation is required. Add its executable to PATH; this workflow will not download a Maven distribution."
}

function Invoke-Mvn([string[]]$MavenArguments) {
  $exitCode = 1
  Push-Location -LiteralPath $ScriptDir
  $previousJavaHome = $env:JAVA_HOME
  try {
    $env:JAVA_HOME = Resolve-JdkHome
    $mavenExe = Resolve-MavenExecutable
    & $mavenExe @MavenArguments 2>&1 | Out-Host
    $exitCode = $LASTEXITCODE
  }
  finally {
    if ($null -eq $previousJavaHome) {
      Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
    } else {
      $env:JAVA_HOME = $previousJavaHome
    }
    Pop-Location
  }
  return $exitCode
}

function Resolve-Pnpm {
  $candidates = @()
  if ($env:APPDATA) {
    $userPnpm = Join-Path $env:APPDATA "npm\pnpm.cmd"
    if (Test-Path -LiteralPath $userPnpm) { $candidates += $userPnpm }
  }
  $candidates += @(Get-Command pnpm.cmd -All -ErrorAction SilentlyContinue | ForEach-Object { $_.Source })
  $candidates += @(Get-Command pnpm -All -ErrorAction SilentlyContinue | ForEach-Object { $_.Source })
  foreach ($candidate in ($candidates | Select-Object -Unique)) {
    $version = (& $candidate --version 2>&1 | Out-String).Trim()
    if ($version -eq "12.14.0") { return @($candidate) }
  }
  throw "pnpm 12.14.0 is required. Add the existing pnpm 12.14.0 executable to PATH or your standard npm user bin; this script will not install, upgrade, or activate a downloaded version."
}

function Invoke-WebBuild {
  $node = Get-NodeVersion
  $pnpmExe = Resolve-Pnpm
  $modules = Join-Path $ScriptDir "web\node_modules"
  if (-not (Test-Path (Join-Path $modules ".bin\tsc.cmd"))) {
    & $pnpmExe --dir (Join-Path $ScriptDir "web") install --frozen-lockfile
    if ($LASTEXITCODE -ne 0) { throw "pnpm dependency restore failed with exit code $LASTEXITCODE" }
  }
  & $pnpmExe --dir (Join-Path $ScriptDir "web") run build
  if ($LASTEXITCODE -ne 0) { throw "workbench build failed with exit code $LASTEXITCODE" }
}

function Verb-Bootstrap {
  try { $javaHome = Resolve-JdkHome }
  catch { Write-Error $_; exit 1 }
  $javaExe = Join-Path $javaHome "bin\java.exe"
  $java = (& $javaExe -version 2>&1 | Out-String)
  Write-Output "[i] $java"
  if (($java -notmatch 'version "25(?:\.|")')) {
    Write-Warning "Need an existing JDK 25."
    exit 1
  }
  try { $node = Get-NodeVersion }
  catch { Write-Error $_; exit 1 }
  try { $null = Resolve-Pnpm }
  catch { Write-Error $_; exit 1 }
  $code = Invoke-Mvn @("-version")
  if (($code -ne 0)) { exit $code }
  Write-Output "[OK] bootstrap: existing JDK 25, Node $node, and pinned pnpm are available"
}

function Verb-Build {
  try { Invoke-WebBuild } catch { Write-Error $_; exit 1 }
  exit (Invoke-Mvn @("-B", "-DskipTests", "package"))
}

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
    Write-Output "[i] target/jahaziel.jar missing, building the app and workbench..."
    try { Invoke-WebBuild } catch { Write-Error $_; exit 1 }
    $code = Invoke-Mvn @("-B", "-DskipTests", "package")
    if (($code -ne 0)) { exit $code }
  }
}

function Verb-Run($Rest) {
  Ensure-Jar
  $java = Join-Path (Resolve-JdkHome) "bin\java.exe"
  Push-Location -LiteralPath $ScriptDir
  try {
    & $java -jar $Jar @Rest
    $code = $LASTEXITCODE
  } finally {
    Pop-Location
  }
  exit $code
}

function Verb-Dev($Rest) {
  Ensure-Jar
  $env:JAHAZIEL_DEMO = "true"
  $all = @("--demo") + $Rest
  $java = Join-Path (Resolve-JdkHome) "bin\java.exe"
  Push-Location -LiteralPath $ScriptDir
  try {
    & $java -Dspring.profiles.active=dev -jar $Jar @all
    $code = $LASTEXITCODE
  } finally {
    Pop-Location
  }
  exit $code
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
  exit (Invoke-Mvn @("-Psmoke", "verify", "-Djahaziel.jar=$Jar"))
}

function Verb-Docs {
  $docs = Join-Path $ScriptDir "docs"
  $pnpm = Resolve-Pnpm
  if (-not (Test-Path (Join-Path $docs "node_modules\.bin\vitepress.cmd"))) {
    Write-Output "[i] restoring locked docs dependencies with pnpm 12"
    & $pnpm --dir $docs install --frozen-lockfile
    if (($LASTEXITCODE -ne 0)) { exit $LASTEXITCODE }
  }
  Write-Output "[i] serving VitePress at http://localhost:5173 (Ctrl+C to stop)"
  & $pnpm --dir $docs run docs:dev
  exit $LASTEXITCODE
}

function Verb-DocsBuild {
  $docs = Join-Path $ScriptDir "docs"
  $pnpm = Resolve-Pnpm
  if (-not (Test-Path (Join-Path $docs "node_modules\.bin\vitepress.cmd"))) {
    Write-Output "[i] restoring locked docs dependencies with pnpm 12"
    & $pnpm --dir $docs install --frozen-lockfile
    if (($LASTEXITCODE -ne 0)) { exit $LASTEXITCODE }
  }
  & $pnpm --dir $docs run docs:build
  if (($LASTEXITCODE -ne 0)) { exit $LASTEXITCODE }
  Write-Output "[OK] docs:build: site in docs/.vitepress/dist/"
}

function Verb-Release {
  try { Invoke-WebBuild } catch { Write-Error $_; exit 1 }
  $code = Invoke-Mvn @("-B", "-DskipTests", "package")
  if (($code -ne 0)) { exit $code }
  $hash = (Get-FileHash -Algorithm SHA256 $Jar).Hash.ToLower()
  $out = "$Jar.sha256"
  "$hash  jahaziel.jar" | Out-File -FilePath $out -Encoding ASCII
  Write-Output "[OK] release: $Jar"
  Write-Output "[OK] release: $out ($hash)"
}

function Verb-Clean { exit (Invoke-Mvn @("clean")) }

function Get-DirectoryBytes([string]$Path) {
  if (-not (Test-Path -LiteralPath $Path)) { return 0L }
  $measure = Get-ChildItem -LiteralPath $Path -File -Recurse -Force -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum
  if ($null -eq $measure) { return 0L }
  $sum = $measure.PSObject.Properties['Sum']
  if ($null -eq $sum -or $null -eq $sum.Value) { return 0L }
  return [long]$sum.Value
}

function Get-GeneratedPaths {
  return @(
    ".cache/maven/repository",
    ".cache/maven-wrapper",
    ".cache/localappdata/pnpm-cache",
    ".cache/localappdata/pnpm-state",
    ".cache/localappdata/Microsoft/Windows/Caches",
    ".cache/pnpm-home/store",
    ".cache/pnpm-cache",
    ".cache/pnpm-state",
    ".cache/npm-cache",
    "web/node_modules",
    "docs/node_modules",
    "web/dist",
    "docs/.vitepress/cache",
    "docs/.vitepress/dist",
    "target"
  )
}

function Resolve-GeneratedPath([string]$RelativePath) {
  $repoRoot = [IO.Path]::GetFullPath($ScriptDir)
  $rootItem = Get-Item -LiteralPath $repoRoot -Force
  if (($rootItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
    throw "Refusing to prune generated paths through a reparse-point repository root: $repoRoot"
  }
  $repoPrefix = $repoRoot.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
  $candidate = [IO.Path]::GetFullPath((Join-Path $repoRoot $RelativePath))
  if (-not $candidate.StartsWith($repoPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Generated path escapes the repository: $RelativePath"
  }

  $cursor = $repoRoot
  foreach ($segment in ($RelativePath -split '[\\/]')) {
    $cursor = Join-Path $cursor $segment
    if (Test-Path -LiteralPath $cursor) {
      $item = Get-Item -LiteralPath $cursor -Force
      if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw "Refusing to follow a reparse point while resolving generated path: $cursor"
      }
    }
  }
  return $candidate
}

function Verb-CacheReport {
  foreach ($name in (Get-GeneratedPaths)) {
    $path = Resolve-GeneratedPath $name
    $bytes = Get-DirectoryBytes $path
    Write-Output ("{0,10:N1} MiB  {1}" -f ($bytes / 1MB), $path)
  }
}

function Verb-CachePrune($Rest) {
  $apply = $Rest -contains "--apply"
  foreach ($name in (Get-GeneratedPaths)) {
    $path = Resolve-GeneratedPath $name
    if (Test-Path -LiteralPath $path -PathType Container) {
      $bytes = Get-DirectoryBytes $path
      if ($apply) {
        Remove-Item -LiteralPath $path -Recurse -Force
        Write-Output ("removed {0,10:N1} MiB  {1}" -f ($bytes / 1MB), $path)
      } else {
        Write-Output ("would remove {0,10:N1} MiB  {1}" -f ($bytes / 1MB), $path)
      }
    }
  }
  if (-not $apply) { Write-Output "Dry run only. Pass --apply to remove only these repository-local dependency caches and generated build outputs." }
}

function Usage {
  Write-Output "Usage: ./dev.ps1 <verb> [args]"
  Write-Output "  bootstrap build test test:watch run dev reset smoke docs docs:build release cache:report cache:prune clean"
}

$Verb = $args[0]
$Rest = @()
if (($args.Count -gt 1)) { $Rest = $args[1..($args.Count - 1)] }
switch ($Verb) {
  "bootstrap" { Verb-Bootstrap }
  "build" { Verb-Build }
  "test" { Verb-Test $Rest }
  "test:watch" { Verb-TestWatch }
  "run" { Verb-Run $Rest }
  "dev" { Verb-Dev $Rest }
  "reset" { Verb-Reset }
  "smoke" { Verb-Smoke }
  "docs" { Verb-Docs }
  "docs:build" { Verb-DocsBuild }
  "release" { Verb-Release }
  "cache:report" { Verb-CacheReport }
  "cache:prune" { Verb-CachePrune $Rest }
  "clean" { Verb-Clean }
  default { Usage }
}
