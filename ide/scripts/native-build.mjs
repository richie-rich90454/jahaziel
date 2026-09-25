#!/usr/bin/env node
/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Builds upstream native modules without requiring Visual Studio's optional
// "Spectre-mitigated libraries" component.
//
// node-gyp's generated projects set SpectreMitigation=Spectre, which makes MSBuild fail
// with MSB8040 before compiling anything, and redirecting the link path away from the
// ordinary toolset libraries makes delayimp.lib unresolvable (LNK1181) because the
// Windows SDK's um/x64 set here does not ship it. Both symptoms disappear when
// SpectreMitigation is false, and the MSVC toolset lib directory then supplies
// delayimp.lib. So: let node-gyp generate the project, then drive MSBuild ourselves with
// the property as a global (it is too late in Directory.Build.targets, which is imported
// after LibraryPath is computed).
//
// Usage: node ide/scripts/native-build.mjs [upstream-dir]

import { execFileSync, spawnSync } from 'node:child_process'
import { existsSync, readdirSync, statSync } from 'node:fs'
import { dirname, join, relative, resolve } from 'node:path'

const upstream = resolve(process.argv[2] ?? 'ide/upstream')
const nodeModules = join(upstream, 'node_modules')

// Invoke node-gyp through the running Node binary. Spawning `npx` directly fails on
// Windows because npx is a .cmd shim and needs a shell.
const nodeGypCli = join(dirname(process.execPath), 'node_modules', 'npm', 'node_modules', 'node-gyp', 'bin', 'node-gyp.js')
const nodeExe = process.execPath

function nodeGyp(args, cwd) {
  return spawnSync(nodeExe, [nodeGypCli, ...args], { cwd, encoding: 'utf8' })
}

function findBindingGypDirs(root) {
  const found = []
  const walk = (dir, depth) => {
    if (depth > 3) return
    let entries
    try {
      entries = readdirSync(dir, { withFileTypes: true })
    } catch {
      return
    }
    for (const entry of entries) {
      if (!entry.isDirectory() || entry.name === '.bin') continue
      const full = join(dir, entry.name)
      if (entry.name === 'build' || entry.name === 'src') continue
      if (existsSync(join(full, 'binding.gyp'))) {
        found.push(full)
        continue
      }
      walk(full, depth + 1)
    }
  }
  walk(root, 0)
  return found
}

function findMsBuild() {
  const roots = [
    'C:/Program Files/Microsoft Visual Studio',
    'C:/Program Files (x86)/Microsoft Visual Studio',
  ]
  for (const root of roots) {
    if (!existsSync(root)) continue
    // Layout is <root>/<version>/<edition>/MSBuild/<msbuild-version>/Bin/MSBuild.exe
    for (const version of readdirSync(root)) {
      const versionRoot = join(root, version)
      if (!statSync(versionRoot).isDirectory()) continue
      for (const edition of readdirSync(versionRoot)) {
        const msbuildRoot = join(versionRoot, edition, 'MSBuild')
        if (!existsSync(msbuildRoot)) continue
        const current = join(msbuildRoot, 'Current', 'Bin', 'MSBuild.exe')
        if (existsSync(current)) return current
        for (const ver of readdirSync(msbuildRoot)) {
          const candidate = join(msbuildRoot, ver, 'Bin', 'MSBuild.exe')
          if (existsSync(candidate)) return candidate
        }
      }
    }
  }
  return null
}

const isWindows = process.platform === 'win32'
const targets = findBindingGypDirs(nodeModules)

// cpu-features ships a binding.gyp that includes deps/cpu_features/buildcheck.gypi, a file
// the published package does not contain, so node-gyp cannot configure it. Upstream
// disables it ("cpu-features": false in package.json) and nothing under src/ or build/
// references it, so it is not part of the REH build.
const SKIP = new Set(['cpu-features'])
const buildable = targets.filter((dir) => !SKIP.has(relative(nodeModules, dir)))
console.log(`native-build: ${buildable.length} native module(s) to build, ${targets.length - buildable.length} skipped`)

if (!isWindows) {
  for (const dir of buildable) {
    console.log(`  building ${relative(upstream, dir)} with node-gyp`)
    nodeGyp(['rebuild'], dir)
  }
  process.exit(0)
}

const msbuild = findMsBuild()
if (!msbuild) {
  console.error('native-build: MSBuild not found; install Visual Studio C++ tooling')
  process.exit(1)
}
console.log(`native-build: using ${msbuild}`)

function findFirstFile(dir, fileName, depth = 0) {
  if (depth > 6) return null
  let entries
  try {
    entries = readdirSync(dir, { withFileTypes: true })
  } catch {
    return null
  }
  for (const entry of entries) {
    const full = join(dir, entry.name)
    if (entry.isFile() && entry.name === fileName) return full
    if (entry.isDirectory()) {
      const hit = findFirstFile(full, fileName, depth + 1)
      if (hit) return hit
    }
  }
  return null
}

let failures = 0
for (const dir of buildable) {
  const name = relative(nodeModules, dir)
  const configured = nodeGyp(['configure'], dir)
  if (configured.status !== 0) {
    console.error(`  FAIL ${name}: node-gyp configure failed`)
    console.error((configured.stderr || '').split('\n').slice(-6).join('\n'))
    failures++
    continue
  }
  const buildDir = join(dir, 'build')
  const projects = readdirSync(buildDir).filter((f) => f.endsWith('.vcxproj'))
  if (projects.length === 0) {
    console.error(`  FAIL ${name}: no .vcxproj generated`)
    failures++
    continue
  }
  // Modules that unpack a vendored C library into a sub-project do so under the
  // sub-project's own SHARED_INTERMEDIATE_DIR, so the include path the parent inherits
  // points at a directory that does not exist. Add the real one through CL, which MSVC
  // appends to every compile; an AdditionalIncludeDirectories global property would be
  // shadowed by the project's own item metadata.
  const env = { ...process.env }
  const header = findFirstFile(buildDir, 'sqlite3.h')
  if (header) {
    env.CL = `/I${dirname(header)}`
  }
  let ok = true
  for (const project of projects) {
    const baseArgs = [
      join(buildDir, project),
      '/t:Build',
      '/p:Configuration=Release',
      '/p:Platform=x64',
      '/p:SpectreMitigation=false',
      '/nologo',
      '/v:minimal',
    ]
    let built = spawnSync(msbuild, baseArgs, { cwd: dir, encoding: 'utf8', env })
    // A few vendored parsers (tree-sitter) require C++20 while node-gyp defaults to an
    // older standard, and V8's headers hard-error on it. Retry those with the newer
    // standard instead of patching every binding.gyp.
    if (built.status !== 0 && /C\+\+20 or later required/.test((built.stdout || '') + (built.stderr || ''))) {
      // tree-sitter's project carries both "-std:c++20" and a later "/std:c++17", so the
      // older standard wins. CL is appended after AdditionalOptions by cl.exe, which makes
      // it the only reliable place to force the newer standard; override the project
      // property as well for projects that set one.
      built = spawnSync(msbuild, [...baseArgs, '/p:LanguageStandard=stdcpp20'], {
        cwd: dir,
        encoding: 'utf8',
        env: { ...env, CL: `/std:c++20 ${env.CL ?? ''}`.trim() },
      })
    }
    if (built.status !== 0) {
      ok = false
      console.error(`  FAIL ${name}: MSBuild failed for ${project}`)
      console.error((built.stdout || '').split('\n').slice(-8).join('\n'))
    }
  }
  if (ok) {
    console.log(`  ok ${name}`)
  } else {
    failures++
  }
}

if (failures > 0) {
  console.error(`native-build: ${failures} module(s) failed`)
  process.exit(1)
}
console.log('native-build: all modules built')
