/* Copyright 2026 The Jahaziel Authors. SPDX-License-Identifier: Apache-2.0. */
"use strict";

const assert = require("node:assert/strict");
const { spawn, execFileSync } = require("node:child_process");
const fs = require("node:fs");
const net = require("node:net");
const os = require("node:os");
const path = require("node:path");
const { setTimeout: delay } = require("node:timers/promises");
const { chromium } = require("playwright");

const jarSource = process.argv[2];
if (!jarSource) throw new Error("usage: node tools/browser-smoke.cjs <packaged-jar>");

const jarSourcePath = path.resolve(jarSource);
const scratchRoot = fs.mkdtempSync(path.join(os.tmpdir(), "jahaziel-browser-smoke-"));
const jarPath = path.join(scratchRoot, "jahaziel.jar");
const dataTemp = path.join(scratchRoot, "data", "tmp");
const serverLogs = [];
let server = null;
let browser = null;

function logServer(chunk) {
  serverLogs.push(chunk.toString());
  while (serverLogs.join("").length > 96 * 1024) serverLogs.shift();
}

async function freePort() {
  const socket = net.createServer();
  await new Promise((resolve, reject) => {
    socket.once("error", reject);
    socket.listen(0, "127.0.0.1", resolve);
  });
  const address = socket.address();
  await new Promise((resolve, reject) => socket.close((failure) => failure ? reject(failure) : resolve()));
  return address.port;
}

function launchServer(port) {
  const env = {
    ...process.env,
    PORT: String(port),
    JAHAZIEL_LANGUAGE_TOOLS_NODE_EXECUTABLE: process.execPath,
  };
  for (const key of Object.keys(env)) {
    if (key.startsWith("BASIS_")) delete env[key];
  }
  const child = spawn(
    "java",
    [`-Djava.io.tmpdir=${dataTemp}`, "-jar", jarPath, "--demo", `--port=${port}`],
    { cwd: scratchRoot, env, detached: true, stdio: ["ignore", "pipe", "pipe"] },
  );
  child.stdout.on("data", logServer);
  child.stderr.on("data", logServer);
  child.on("error", logServer);
  return child;
}

async function waitForHealth(port, child) {
  const deadline = Date.now() + 60_000;
  let last = "unreachable";
  while (Date.now() < deadline) {
    if (child.exitCode !== null) {
      throw new Error(`Jahaziel exited with ${child.exitCode}:\n${serverLogs.join("")}`);
    }
    try {
      const response = await fetch(`http://127.0.0.1:${port}/actuator/health`, {
        signal: AbortSignal.timeout(3000),
      });
      const body = await response.text();
      if (response.ok && body.includes('"status":"UP"')) return;
      last = `${response.status} ${body}`;
    } catch (failure) {
      last = failure.message;
    }
    await delay(500);
  }
  throw new Error(`health did not become UP (${last}):\n${serverLogs.join("")}`);
}

async function stopServer(child) {
  if (!child || child.exitCode !== null) return;
  try {
    process.kill(-child.pid, "SIGTERM");
  } catch (failure) {
    if (failure.code !== "ESRCH") throw failure;
  }
  const stopped = await Promise.race([
    new Promise((resolve) => child.once("exit", resolve).once("error", resolve)),
    delay(15_000).then(() => false),
  ]);
  if (stopped === false && child.exitCode === null) {
    try {
      process.kill(-child.pid, "SIGKILL");
    } catch (failure) {
      if (failure.code !== "ESRCH") throw failure;
    }
    await Promise.race([new Promise((resolve) => child.once("exit", resolve)), delay(5000)]);
  }
}

async function crashServer(child) {
  if (!child || child.exitCode !== null) throw new Error("cannot verify recovery without a running server");
  try {
    process.kill(-child.pid, "SIGKILL");
  } catch (failure) {
    if (failure.code !== "ESRCH") throw failure;
  }
  const stopped = await Promise.race([
    new Promise((resolve) => child.once("exit", resolve).once("error", resolve)),
    delay(5000).then(() => false),
  ]);
  if (stopped === false) throw new Error("server did not exit after forced termination");
}

async function waitForPortState(port, expected, timeout = 10_000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const listening = await new Promise((resolve) => {
      const socket = net.createConnection({ host: "127.0.0.1", port });
      socket.once("connect", () => { socket.destroy(); resolve(true); });
      socket.once("error", () => resolve(false));
    });
    if (listening === expected) return;
    await delay(250);
  }
  throw new Error(`port ${port} did not become ${expected ? "available" : "closed"}`);
}

async function waitForText(page, selector, expression, timeout = 15_000) {
  await page.waitForFunction(
    ({ selector: target, expression: pattern }) =>
      new RegExp(pattern).test(document.querySelector(target)?.textContent || ""),
    { selector, expression },
    { timeout },
  );
}

async function run() {
  if (!fs.statSync(jarSourcePath).isFile()) throw new Error(`jar not found: ${jarSourcePath}`);
  fs.copyFileSync(jarSourcePath, jarPath);
  const env = { ...process.env };
  for (const key of Object.keys(env)) {
    if (key.startsWith("BASIS_")) delete env[key];
  }
  execFileSync("java", ["-jar", jarPath, "init"], { cwd: scratchRoot, env, stdio: "pipe" });

  const port = await freePort();
  server = launchServer(port);
  await waitForHealth(port, server);
  browser = await chromium.launch({ headless: true, args: ["--no-sandbox"] });
  const page = await browser.newPage();
  page.setDefaultTimeout(20_000);
  const pageErrors = [];
  page.on("pageerror", (failure) => pageErrors.push(failure.message));
  const origin = `http://127.0.0.1:${port}`;

  await page.goto(origin);
  await page.locator("#user-badge").waitFor({ state: "visible" });
  assert.equal(await page.locator("#user-email").textContent(), "demo@example.com");
  await page.locator("#workspace-name").fill("browser-smoke");
  await page.locator("#template-select").selectOption("python");
  await page.locator("#create-workspace").click();
  await page.locator("#workbench-page").waitFor({ state: "visible" });
  await page.waitForURL(/#workspace\/[0-9a-f-]+$/i);
  await waitForText(page, "#setup-state", "^READY$");

  const workspaceId = new URL(page.url()).hash.split("/")[1];
  const secondWorkspaceResponse = await page.request.post(
    origin + "/api/v1/templates/empty/apply",
    { headers: { Origin: origin }, data: { workspaceName: "browser-smoke-empty" } },
  );
  assert.equal(secondWorkspaceResponse.status(), 201, "a second template must provision through the browser session");
  const secondWorkspace = await secondWorkspaceResponse.json();
  assert.notEqual(secondWorkspace.workspaceId, workspaceId, "each workspace must have its own identity");
  const crossOriginWrite = await page.request.post(
    origin + "/api/v1/templates/empty/apply",
    {
      headers: { Origin: "http://attacker.invalid" },
      data: { workspaceName: "must-be-rejected" },
    },
  );
  assert.equal(crossOriginWrite.status(), 403, "cookie-authenticated cross-origin writes must fail");
  const workspaceHome = path.join(scratchRoot, "data", "workspaces", workspaceId, "home");
  const secondWorkspaceHome = path.join(
    scratchRoot,
    "data",
    "workspaces",
    secondWorkspace.workspaceId,
    "home",
  );
  assert.notEqual(
    fs.realpathSync(workspaceHome),
    fs.realpathSync(secondWorkspaceHome),
    "workspaces must not share a project home",
  );
  assert.equal(
    fs.readFileSync(path.join(secondWorkspaceHome, "README.md"), "utf8"),
    "# New workspace\n\nYour project files live in this workspace.\n",
    "template starter files must be materialized in the new workspace",
  );
  const additionalTemplates = new Map([
    ["node", "src/index.ts"],
    ["java", "Main.java"],
    ["static-site", "index.html"],
    ["cpp", "main.cpp"],
    ["go", "main.go"],
    ["rust", "src/main.rs"],
  ]);
  for (const [templateName, starterPath] of additionalTemplates) {
    const response = await page.request.post(
      `${origin}/api/v1/templates/${templateName}/apply`,
      {
        headers: { Origin: origin },
        data: { workspaceName: `browser-smoke-${templateName}` },
      },
    );
    assert.equal(response.status(), 201, `${templateName} template must provision`);
    const workspace = await response.json();
    const home = path.join(scratchRoot, "data", "workspaces", workspace.workspaceId, "home");
    assert.ok(
      fs.statSync(path.join(home, starterPath)).isFile(),
      `${templateName} template starter file must be materialized`,
    );
  }

  await page.locator(".tree-item").filter({ hasText: "README.md" }).click();
  const editor = page.locator(".cm-content");
  await editor.waitFor({ state: "visible" });
  await editor.click();
  await page.keyboard.press("Control+A");
  await page.keyboard.type("# Browser smoke\n\nSaved by the packaged workbench.\n");
  await page.locator("#save-file").click();
  await waitForText(page, "#save-state", "^Saved$");

  const fileUrl = `${origin}/api/v1/workspaces/${workspaceId}/files/document?path=README.md`;
  const original = await page.request.get(fileUrl);
  assert.equal(original.status(), 200, "workspace file must be readable from the browser session");
  const etag = original.headers()["etag"];
  assert.ok(etag, "file read must return an ETag");
  await editor.click();
  await page.keyboard.press("Control+A");
  await page.keyboard.type("# Local unsaved edit\n");
  const external = await page.request.put(fileUrl, {
    headers: { "If-Match": etag, Origin: origin },
    data: { content: "# External update\n\nConflict preserved this version.\n" },
  });
  assert.equal(external.status(), 200, "external file update must succeed with the current ETag");
  await page.locator("#save-file").click();
  await waitForText(page, "#save-state", "Changed on disk");
  await page.reload();
  await page.locator("#workbench-page").waitFor({ state: "visible" });
  await page.locator(".tree-item").filter({ hasText: "README.md" }).click();
  await page.waitForFunction(() => document.querySelector(".cm-content")?.textContent.includes("External update"));

  const search = page.locator("#workspace-search");
  await search.fill("External update");
  await search.press("Enter");
  const searchResult = page.locator(".search-result").filter({ hasText: "README.md" });
  await searchResult.waitFor({ state: "visible" });
  assert.match(await searchResult.textContent(), /External update/);
  await searchResult.click();
  await page.waitForFunction(() => document.querySelector(".cm-content")?.textContent.includes("External update"));
  await search.fill("");

  assert.ok(fs.statSync(workspaceHome).isDirectory(), "workspace must have its own home directory");
  execFileSync("git", ["init", "--initial-branch=main"], { cwd: workspaceHome, stdio: "pipe" });
  execFileSync("git", ["config", "user.name", "Jahaziel Browser Smoke"], { cwd: workspaceHome });
  execFileSync("git", ["config", "user.email", "smoke@example.invalid"], { cwd: workspaceHome });
  execFileSync("git", ["config", "commit.gpgsign", "false"], { cwd: workspaceHome });
  execFileSync("git", ["add", "-A"], { cwd: workspaceHome });
  execFileSync("git", ["commit", "-m", "Initial workspace"], { cwd: workspaceHome, stdio: "pipe" });
  fs.appendFileSync(path.join(workspaceHome, "README.md"), "\nChanges from packaged browser smoke.\n");
  await page.locator("#git-refresh").click();
  await waitForText(page, "#git-state", "^main · 1$");
  const readmeChange = page.locator("#git-changes .git-change").filter({ hasText: "README.md" });
  await readmeChange.locator(".git-path").click();
  const gitDiff = page.locator("#git-diff");
  await gitDiff.waitFor({ state: "visible" });
  assert.match(await gitDiff.textContent(), /Changes from packaged browser smoke/);

  await readmeChange.locator(".git-action").filter({ hasText: "Stage" }).click();
  await page.waitForFunction(
    () => document.querySelector("#git-changes .git-action")?.textContent?.trim() === "Unstage",
  );
  await page.locator("#git-changes .git-action").filter({ hasText: "Unstage" }).click();
  await page.waitForFunction(
    () => document.querySelector("#git-changes .git-action")?.textContent?.trim() === "Stage",
  );
  await page.locator("#git-changes .git-action").filter({ hasText: "Stage" }).click();
  await page.waitForFunction(
    () => document.querySelector("#git-changes .git-action")?.textContent?.trim() === "Unstage",
  );
  await page.locator("#git-message").fill("Exercise Git panel");
  await page.waitForFunction(() => !document.querySelector("#git-commit")?.disabled);
  await page.locator("#git-commit").click();
  await page.waitForFunction(
    () => document.querySelector("#git-changes")?.textContent?.includes("Working tree clean."),
  );
  assert.equal(
    execFileSync("git", ["log", "-1", "--pretty=%s"], { cwd: workspaceHome, encoding: "utf8" }).trim(),
    "Exercise Git panel",
  );

  await page.locator("#runtime-toggle").click();
  await waitForText(page, "#workbench-state", "READY", 20_000);

  const pyrightPack = page.locator(".language-pack").filter({ hasText: "Pyright" });
  await pyrightPack.locator(".language-pack-action").click();
  await waitForText(page, "#language-state", "python language service ready", 90_000);
  await page.locator(".tree-item").filter({ hasText: "main.py" }).click();
  const pythonEditor = page.locator(".cm-content");
  await pythonEditor.click();
  await page.keyboard.press("Control+A");
  await page.keyboard.type("def main(:\n    pass\n");
  const syntaxDiagnostic = page.locator("#language-diagnostics .language-diagnostic-error")
    .filter({ hasText: "main.py" });
  await syntaxDiagnostic.waitFor({ state: "visible", timeout: 45_000 });

  await page.locator("#task-list button").filter({ hasText: "Serve preview" }).click();
  await waitForText(page, "#task-output", "Serving HTTP on 0\\.0\\.0\\.0 port 8000", 20_000);
  await waitForText(page, "#preview-links", "LISTENING", 15_000);
  const preview = await fetch("http://127.0.0.1:8000/");
  assert.equal(preview.status, 200, "declared preview task must serve the workspace files");
  assert.match(await preview.text(), /Jahaziel Python workspace/);
  assert.equal(await page.locator("#preview-links a").count(), 1, "registered preview port must be visible");
  const previewHref = await page.locator("#preview-links a").getAttribute("href");
  assert.ok(previewHref, "registered preview link must include its workspace-scoped URL");
  const previewPage = await browser.newPage();
  let previewLoaded = false;
  for (let attempt = 0; attempt < 12 && !previewLoaded; attempt += 1) {
    try {
      const response = await previewPage.goto(previewHref, { waitUntil: "domcontentloaded", timeout: 8000 });
      previewLoaded = response?.ok() === true
        && (await previewPage.locator("body").textContent()).includes("Jahaziel Python workspace");
    } catch (failure) {
      if (attempt === 11) throw failure;
    }
    if (!previewLoaded) await delay(500);
  }
  assert.ok(previewLoaded, "workspace-scoped preview URL must route to the running task");
  await previewPage.close();
  await page.locator("#task-stop").click();
  await waitForText(page, "#task-output", "STOPPED", 15_000);
  await waitForText(page, "#preview-links", "NOT LISTENING", 15_000);

  await page.locator("#terminal-new").click();
  await waitForText(page, "#terminal-state", "^Connected$", 30_000);
  const sessionsUrl = `${origin}/api/v1/workspaces/${workspaceId}/terminals`;
  const beforeResizeResponse = await page.request.get(sessionsUrl);
  assert.equal(beforeResizeResponse.status(), 200, "terminal sessions must be readable");
  const beforeResize = (await beforeResizeResponse.json()).at(-1);
  assert.ok(beforeResize, "the newly opened terminal session must be listed");
  await page.locator("#terminal-surface").evaluate((surface) => {
    surface.style.width = "560px";
    surface.style.height = "110px";
  });
  await page.waitForFunction(async ({ sessionsUrl: url, sessionId, rows, cols }) => {
    const response = await fetch(url);
    if (!response.ok) return false;
    const sessions = await response.json();
    const session = sessions.find((entry) => entry.sessionId === sessionId);
    return Boolean(session && (session.rows !== rows || session.cols !== cols));
  }, {
    sessionsUrl,
    sessionId: beforeResize.sessionId,
    rows: beforeResize.rows,
    cols: beforeResize.cols,
  }, { timeout: 15_000 });

  const marker = `JAH_SMOKE_${Date.now()}`;
  await page.locator(".xterm-helper-textarea").click();
  await page.keyboard.type(`printf '${marker}\\n'`);
  await page.keyboard.press("Enter");
  await page.waitForFunction(
    (expected) => document.querySelector(".xterm-rows")?.textContent.includes(expected),
    marker,
    { timeout: 15_000 },
  );

  await page.reload();
  await page.locator("#workbench-page").waitFor({ state: "visible" });
  await page.locator("#terminal-reconnect").waitFor({ state: "visible", timeout: 20_000 });
  await page.locator("#terminal-reconnect").click();
  await waitForText(page, "#terminal-state", "^Connected$", 30_000);
  await page.waitForFunction(
    (expected) => document.querySelector(".xterm-rows")?.textContent.includes(expected),
    marker,
    { timeout: 15_000 },
  );
  await page.locator("#close-terminal").click();
  await page.locator("#terminal-reconnect").waitFor({ state: "hidden", timeout: 15_000 });

  await page.locator("#task-list button").filter({ hasText: "Serve preview" }).click();
  await waitForText(page, "#task-output", "Serving HTTP on 0\\.0\\.0\\.0 port 8000", 20_000);
  await waitForText(page, "#preview-links", "LISTENING", 15_000);
  const runningTaskResponse = await page.request.get(
    `${origin}/api/v1/workspaces/${workspaceId}/task-runs/latest`,
  );
  assert.equal(runningTaskResponse.status(), 200, "latest task status must be readable");
  assert.equal((await runningTaskResponse.json()).status, "RUNNING");

  await browser.close();
  browser = null;
  await crashServer(server);
  server = null;
  serverLogs.length = 0;
  server = launchServer(port);
  await waitForHealth(port, server);
  await waitForPortState(8000, false, 15_000);
  browser = await chromium.launch({ headless: true, args: ["--no-sandbox"] });
  const recoveredPage = await browser.newPage();
  await recoveredPage.goto(`${origin}/#workspace/${workspaceId}`);
  await recoveredPage.locator("#workbench-page").waitFor({ state: "visible" });
  await waitForText(recoveredPage, "#task-output", "Task was interrupted by a server restart", 15_000);
  const recoveredTaskResponse = await recoveredPage.request.get(
    `${origin}/api/v1/workspaces/${workspaceId}/task-runs/latest`,
  );
  assert.equal(recoveredTaskResponse.status(), 200, "interrupted task status must be available after restart");
  const recoveredTask = await recoveredTaskResponse.json();
  assert.equal(recoveredTask.status, "FAILED");
  assert.match(recoveredTask.output, /Task was interrupted by a server restart/);
  await recoveredPage.locator(".tree-item").filter({ hasText: "README.md" }).click();
  await recoveredPage.waitForFunction(() => document.querySelector(".cm-content")?.textContent.includes("External update"));
  assert.deepEqual(pageErrors, [], "the browser workbench must not raise uncaught errors");
  console.log("Packaged browser smoke passed: all template families, setup, file conflict/search, Git panel, language diagnostics, task preview, terminal resize/reconnect, abrupt task recovery, workspace restart recovery.");
}

run()
  .catch((failure) => {
    console.error(failure);
    if (serverLogs.length) console.error(serverLogs.join(""));
    process.exitCode = 1;
  })
  .finally(async () => {
    if (browser) await browser.close().catch(() => undefined);
    await stopServer(server).catch((failure) => console.error(failure));
    if (fs.existsSync(scratchRoot)
        && path.dirname(scratchRoot) === os.tmpdir()
        && path.basename(scratchRoot).startsWith("jahaziel-browser-smoke-")) {
      fs.rmSync(scratchRoot, { recursive: true, force: true });
    }
  });
