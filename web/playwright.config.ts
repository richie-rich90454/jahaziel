///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { defineConfig, devices } from "@playwright/test";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

// Prefer an already-installed Chromium-based browser so a fresh Windows machine
// does not need a Playwright browser download. Falls back to the bundled binary.
const candidates = [
  { channel: "chrome", paths: [
    "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
    "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
  ] },
  { channel: "msedge", paths: [
    "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
    "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe",
  ] },
];
const browserChannel = candidates.find((candidate) => candidate.paths.some((file) => fs.existsSync(file)))?.channel;

const port = 5199;
const scratch = path.join(os.tmpdir(), "jahaziel-e2e-scratch");
const staticDir = path.join(scratch, "dist");
const env = {
  ...process.env,
  E2E_API_PORT: String(port),
  E2E_STATIC_DIR: staticDir,
  BROWSER: "none",
  VITE_CONFIG_NATIVE_IGNORE_WARNING: "true",
};
const build = `node node_modules/vite/bin/vite.js build --outDir "${staticDir}" --emptyOutDir`;

export default defineConfig({
  testDir: "./tests/e2e",
  testMatch: "**/*.spec.ts",
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  // Eight workers race the shared in-memory stub under CI-like load; four is stable.
  workers: 4,
  retries: 0,
  reporter: [["list"]],
  outputDir: path.join(os.tmpdir(), "jahaziel-e2e-results"),
  globalTeardown: "./tests/e2e/global-teardown.cjs",
  use: {
    baseURL: `http://127.0.0.1:${port}`,
    trace: "off",
    screenshot: "off",
    video: "off",
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
    ...devices["Desktop Chrome"],
    ...(browserChannel ? { channel: browserChannel } : {}),
  },
  webServer: {
    command: `${build} && node tests/e2e/stub-server.cjs`,
    url: `http://127.0.0.1:${port}/actuator/health`,
    reuseExistingServer: Boolean(process.env.E2E_REUSE_SERVER),
    timeout: 120_000,
    stdout: "pipe",
    stderr: "pipe",
    cwd: process.cwd(),
    env,
  },
});
