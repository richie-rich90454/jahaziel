/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Headless product tour: starts the packaged jar in demo mode on loopback, walks
 * the real browser workbench with the bundled Chromium, records a video and
 * writes screenshots. It never touches the user's browser profile or desktop.
 */

const fs = require("fs");
const os = require("os");
const path = require("path");
const net = require("net");
const { spawn, spawnSync, execFileSync } = require("child_process");

const root = path.resolve(__dirname, "..");
const jar = path.resolve(process.argv[2] || path.join(root, "target", "jahaziel.jar"));
const outputDir = path.resolve(process.argv[3] || path.join(root, "docs", "assets", "demo"));
const scratchBase = path.join(root, ".cache", "demo-capture");
const playwright = require(require.resolve("@playwright/test", { paths: [path.join(root, "web")] }));

const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const serverLog = [];

function freePort() {
  return new Promise((resolve, reject) => {
    const server = net.createServer();
    server.once("error", reject);
    server.listen(0, "127.0.0.1", () => {
      const port = server.address().port;
      server.close(() => resolve(port));
    });
  });
}

async function waitForHealth(port, child) {
  const deadline = Date.now() + 90_000;
  while (Date.now() < deadline) {
    if (child.exitCode !== null) throw new Error(`server exited with ${child.exitCode}`);
    try {
      const response = await fetch(`http://127.0.0.1:${port}/actuator/health`);
      const body = await response.text();
      if (response.ok && body.includes('"status":"UP"')) return;
    } catch {
      /* not listening yet */
    }
    await delay(500);
  }
  throw new Error("server health did not become UP");
}

async function capture(page, name) {
  await page.screenshot({ path: path.join(outputDir, `${name}.png`), fullPage: false });
  console.log(`  screenshot ${name}.png`);
}

async function run() {
  if (!fs.existsSync(jar)) throw new Error(`jar not found: ${jar}`);
  fs.mkdirSync(scratchBase, { recursive: true });
  fs.mkdirSync(outputDir, { recursive: true });
  const scratch = fs.mkdtempSync(path.join(scratchBase, "run-"));
  const videoDir = path.join(scratch, "video");
  fs.mkdirSync(videoDir, { recursive: true });
  const env = { ...process.env };
  for (const key of Object.keys(env)) if (key.startsWith("BASIS_")) delete env[key];
  delete env.JAHAZIEL_DEMO;
  const port = await freePort();
  const scratchJar = path.join(scratch, "jahaziel.jar");
  fs.copyFileSync(jar, scratchJar);
  execFileSync("java", ["-jar", scratchJar, "init"], { cwd: scratch, env, stdio: "pipe" });
  const server = spawn("java", ["-jar", scratchJar, "--demo", `--port=${port}`], {
    cwd: scratch,
    env,
    detached: process.platform !== "win32",
    stdio: ["ignore", "pipe", "pipe"],
    windowsHide: true,
  });
  let browser;
  serverLog.length = 0;
  server.stdout.on("data", (chunk) => serverLog.push(chunk.toString()));
  server.stderr.on("data", (chunk) => serverLog.push(chunk.toString()));
  try {
    await waitForHealth(port, server);
    const origin = `http://127.0.0.1:${port}`;
    browser = await playwright.chromium.launch({ headless: true, args: ["--no-sandbox"] });
    const context = await browser.newContext({
      viewport: { width: 1440, height: 900 },
      deviceScaleFactor: 1,
      recordVideo: { dir: videoDir, size: { width: 1440, height: 900 } },
      locale: "en-US",
    });
    const page = await context.newPage();
    page.setDefaultTimeout(20_000);
    await page.goto(origin);
    await page.locator("#home-page").waitFor({ state: "visible" });
    if (await page.locator("#login-link").isVisible()) await page.locator("#login-link").click();
    await page.locator("#user-badge").waitFor({ state: "visible" });
    await page.locator("#loading-state").waitFor({ state: "visible" });
    if (await page.locator("#tour-skip").isVisible()) await page.locator("#tour-skip").click();
    await delay(1200);
    await capture(page, "01-home");

    const name = `demo-${Date.now().toString(36)}`;
    await page.locator("#workspace-name").fill(name);
    await page.locator("#template-select").selectOption("python");
    await capture(page, "02-create-workspace");
    await page.locator("#create-workspace").click();
    await page.locator("#workbench-page").waitFor({ state: "visible" });
    await page.locator("#workbench-title").waitFor({ text: name });
    await delay(1500);
    await capture(page, "03-workbench");

    const readme = page.locator("#file-tree .tree-item").filter({ hasText: "README.md" }).first();
    if (await readme.count()) {
      await readme.click();
      await delay(1000);
      await capture(page, "04-editor");
    }

    await page.keyboard.press("Control+p");
    await delay(700);
    if (await page.locator("#quick-open-palette").isVisible()) await capture(page, "05-quick-open");
    await page.keyboard.press("Escape");

    const panels = [
      ["run", "06-run"],
      ["terminal", "07-terminal"],
      ["preview", "08-preview"],
      ["git", "09-git"],
      ["debug", "10-debug"],
      ["languages", "11-languages"],
    ];
    for (const [panel, shot] of panels) {
      const button = page.locator(`.workbench-panel-nav [data-panel="${panel}"]`);
      if (await button.count()) {
        await button.click();
        await delay(900);
        await capture(page, shot);
      }
    }

    if (await page.locator("#panel-more").count()) {
      await page.locator("#panel-more").click();
      await delay(700);
      await capture(page, "12-more-panels");
      await page.locator("#panel-more").click();
    }

    await page.locator("#theme-select").selectOption("dark");
    await delay(700);
    await capture(page, "13-dark-theme");
    await page.locator("#theme-select").selectOption("light");

    await page.locator("#locale-select").selectOption("es");
    await delay(700);
    await capture(page, "14-locale");
    await page.locator("#locale-select").selectOption("en");

    await page.setViewportSize({ width: 1024, height: 768 });
    await page.locator('.workbench-panel-nav [data-panel="files"]').click();
    await delay(900);
    await capture(page, "15-ipad-layout");

    const video = page.video();
    await context.close();
    if (video) {
      const recorded = await video.path();
      const target = path.join(outputDir, "demo-walkthrough.webm");
      fs.copyFileSync(recorded, target);
      console.log(`  video ${path.relative(root, target)}`);
    }
  } catch (failure) {
    if (serverLog.length) console.error(serverLog.join(""));
    throw failure;
  } finally {
    if (browser) await browser.close().catch(() => undefined);
    if (server.exitCode === null) {
      if (process.platform === "win32") {
        spawnSync("taskkill", ["/PID", String(server.pid), "/T", "/F"], { stdio: "ignore" });
      } else {
        try { process.kill(-server.pid, "SIGKILL"); } catch { /* already gone */ }
      }
    }
    // The demo server can install a managed coding tool whose child processes keep
    // the scratch tree busy briefly after the server exits.
    let removed = false;
    for (let attempt = 0; attempt < 6; attempt += 1) {
      try {
        fs.rmSync(scratch, { recursive: true, force: true });
        removed = !fs.existsSync(scratch);
      } catch {
        removed = false;
      }
      if (removed) break;
      await delay(500 * (attempt + 1));
    }
    if (!removed) console.error(`demo capture: could not remove scratch ${scratch}`);
  }
  console.log(`Demo capture complete: ${path.relative(root, outputDir)}`);
}

run().catch((failure) => {
  console.error(failure);
  process.exitCode = 1;
});

