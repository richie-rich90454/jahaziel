#!/usr/bin/env node
/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Installs the per-directory dependencies VS Code's build expects.
//
// Upstream build/npm/postinstall.ts does this by running `npm install` inside every
// directory listed in build/npm/dirs.ts. Two things make that unusable here:
//
//   1. It runs lifecycle scripts, so any native module in those trees hits MSBuild's
//      MSB8040 (Visual Studio's optional Spectre-mitigated libraries) and aborts the
//      whole postinstall, leaving every later directory uninstalled.
//   2. The list contains extensions this fork removes (patch 0009 deletes
//      github-authentication and microsoft-authentication), so those entries are gone.
//
// So: install with --ignore-scripts, skip directories that no longer exist, and let
// ide/scripts/native-build.mjs compile the native modules afterwards.
//
// Usage: node ide/scripts/install-deps.mjs [upstream-dir]

import { spawnSync } from 'node:child_process'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'

const upstream = resolve(process.argv[2] ?? 'ide/upstream')
const npmExe = process.platform === 'win32'
  ? join(dirname(process.execPath), 'npm.cmd')
  : join(dirname(process.execPath), 'npm')

// Read the directory list straight from upstream so it cannot drift out of sync.
const dirsSource = readFileSync(join(upstream, 'build', 'npm', 'dirs.ts'), 'utf8')
const dirs = [...dirsSource.matchAll(/^\s*'([^']+)',/gm)].map((m) => m[1])

const wanted = ['', 'build', ...dirs]
let installed = 0
let skipped = 0

for (const dir of wanted) {
  const path = join(upstream, dir)
  if (!existsSync(join(path, 'package.json'))) {
    continue
  }
  if (existsSync(join(path, 'node_modules'))) {
    skipped++
    continue
  }
  console.log(`install-deps: installing ${dir || '<root>'}`)
  const result = spawnSync(npmExe, ['install', '--ignore-scripts', '--no-audit', '--no-fund'], {
    cwd: path,
    encoding: 'utf8',
    shell: process.platform === 'win32',
  })
  if (result.status !== 0) {
    console.error(`install-deps: FAILED in ${dir || '<root>'}`)
    console.error((result.stdout || '').split('\n').slice(-8).join('\n'))
    console.error((result.stderr || '').split('\n').slice(-8).join('\n'))
    process.exit(1)
  }
  installed++
}

console.log(`install-deps: ${installed} installed, ${skipped} already present`)
