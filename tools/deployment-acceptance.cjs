/* Copyright 2026 The Jahaziel Authors. SPDX-License-Identifier: Apache-2.0. */
"use strict";

const assert = require("node:assert/strict");
const { spawn, spawnSync } = require("node:child_process");
const fs = require("node:fs");
const net = require("node:net");
const path = require("node:path");
const { setTimeout: delay } = require("node:timers/promises");

const JAVA = "java";
const IS_WINDOWS = process.platform === "win32";
const REPO_ROOT = path.resolve(__dirname, "..");
const DEFAULT_BASE = path.join(REPO_ROOT, ".cache", "deployment-acceptance");
const CHECKS = [];

let base = DEFAULT_BASE;
let scratchRoot = null;
let jarPath = null;
let dataDir = null;
let childEnvValue = null;
const children = new Set();
const serverLogs = [];

function usage() {
  return "usage: node tools/deployment-acceptance.cjs <jar> [--data-dir <dir>]\n"
    + "  <jar>          packaged jar to exercise (copied into an isolated scratch dir)\n"
    + "  --data-dir DIR optional base for the scratch dir (default: repo-local .cache/deployment-acceptance)\n";
}

function parseArgs(argv) {
  let jar = null;
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      throw new Error("HELP");
    }
    if (arg === "--jar" || arg === "--data-dir") {
      const value = argv[++index];
      if (!value) throw new Error(`${arg} needs a value\n${usage()}`);
      if (arg === "--jar") jar = value;
      else base = path.resolve(value);
    } else if (arg.startsWith("--jar=")) {
      jar = arg.slice("--jar=".length);
    } else if (arg.startsWith("--data-dir=")) {
      base = path.resolve(arg.slice("--data-dir=".length));
    } else if (arg.startsWith("--")) {
      throw new Error(`unknown option ${arg}\n${usage()}`);
    } else if (!jar) {
      jar = arg;
    } else {
      throw new Error(`unexpected argument ${arg}\n${usage()}`);
    }
  }
  if (!jar) throw new Error(`a jar path is required\n${usage()}`);
  return path.resolve(jar);
}

function check(label) {
  CHECKS.push(label);
  console.error(`  ok ${label}`);
}

function logServer(chunk) {
  serverLogs.push(chunk.toString());
  while (serverLogs.join("").length > 96 * 1024) serverLogs.shift();
}

function envForChild() {
  if (childEnvValue) return childEnvValue;
  const env = { ...process.env };
  for (const key of Object.keys(env)) {
    if (key.startsWith("BASIS_") || key === "JAHAZIEL_DEMO") delete env[key];
  }
  env.PATH = [path.dirname(process.execPath), env.PATH || env.Path || ""].join(path.delimiter);
  childEnvValue = env;
  return env;
}

function javaArgs(extra) {
  return [`-Djava.io.tmpdir=${path.join(dataDir, "tmp")}`, "-jar", jarPath, ...extra];
}

function runJar(args, timeout = 120_000) {
  const result = spawnSync(JAVA, javaArgs(args), {
    cwd: scratchRoot,
    env: envForChild(),
    encoding: "utf8",
    timeout,
    maxBuffer: 8 * 1024 * 1024,
    windowsHide: true,
  });
  if (result.error) throw result.error;
  if (result.status !== 0) {
    throw new Error(
      `java -jar jahaziel.jar ${args.join(" ")} failed (status ${result.status}):\n`
        + `${result.stdout || ""}\n${result.stderr || ""}`,
    );
  }
  return result.stdout || "";
}

async function freePort() {
  const socket = net.createServer();
  await new Promise((resolve, reject) => {
    socket.once("error", reject);
    socket.listen(0, "127.0.0.1", resolve);
  });
  const port = socket.address().port;
  await new Promise((resolve, reject) => socket.close((failure) => failure ? reject(failure) : resolve()));
  return port;
}

async function portIsListening(port) {
  return new Promise((resolve) => {
    const socket = net.createConnection({ host: "127.0.0.1", port });
    socket.once("connect", () => { socket.destroy(); resolve(true); });
    socket.once("error", () => resolve(false));
  });
}

async function waitForPort(port, expected, timeout = 10_000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    if (await portIsListening(port) === expected) return;
    await delay(250);
  }
  throw new Error(`port ${port} did not become ${expected ? "listening" : "closed"}`);
}

function startServer(port) {
  const child = spawn(
    JAVA,
    javaArgs([`--port=${port}`, `--data-dir=${dataDir}`, "--demo"]),
    {
      cwd: scratchRoot,
      env: envForChild(),
      detached: true,
      windowsHide: true,
      stdio: ["ignore", "pipe", "pipe"],
    },
  );
  children.add(child);
  child.stdout.on("data", logServer);
  child.stderr.on("data", logServer);
  child.on("error", logServer);
  return child;
}

async function waitForExit(child, timeout) {
  const deadline = Date.now() + timeout;
  while (child.exitCode === null && child.signalCode === null && Date.now() < deadline) {
    await delay(50);
  }
  return child.exitCode !== null || child.signalCode !== null;
}

async function stopChild(child) {
  if (!child) return;
  const alive = () => child.exitCode === null && child.signalCode === null;
  const signalGroup = (signal) => {
    if (IS_WINDOWS) return;
    try {
      process.kill(-child.pid, signal);
    } catch { /* process group already gone */ }
  };
  const taskKill = () => spawnSync("taskkill", ["/PID", String(child.pid), "/T", "/F"], {
    stdio: "ignore",
    timeout: 15_000,
  });
  if (alive()) {
    if (IS_WINDOWS) taskKill();
    else signalGroup("SIGTERM");
    if (!(await waitForExit(child, 5_000))) {
      if (IS_WINDOWS) taskKill();
      else {
        signalGroup("SIGKILL");
        child.kill("SIGKILL");
      }
      await waitForExit(child, 5_000);
    }
  }
  // Java can exit before its spawned task processes release files; sweep the whole group.
  signalGroup("SIGKILL");
}

async function waitForHealth(origin, child) {
  const deadline = Date.now() + 90_000;
  let last = "unreachable";
  while (Date.now() < deadline) {
    if (child.exitCode !== null || child.signalCode !== null) {
      throw new Error(`Jahaziel exited during startup:\n${serverLogs.join("")}`);
    }
    try {
      const response = await fetch(`${origin}/actuator/health`, {
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

async function request(origin, method, route, options = {}) {
  const headers = { ...(options.headers || {}) };
  if (method !== "GET") {
    headers.Origin = origin;
    headers["Content-Type"] = "application/json";
  }
  const response = await fetch(origin + route, {
    method,
    headers,
    redirect: "manual",
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
    signal: AbortSignal.timeout(15_000),
  });
  const text = await response.text();
  const expected = options.expect === undefined ? 200 : options.expect;
  assert.equal(
    response.status,
    expected,
    `${method} ${route} returned ${response.status} (expected ${expected}): ${text.slice(0, 600)}`,
  );
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { /* not a JSON response */ }
  return { response, text, json };
}

async function eventually(action, timeout, label) {
  const deadline = Date.now() + timeout;
  let last = "no result";
  while (Date.now() < deadline) {
    try {
      const value = await action();
      if (value) return value;
      last = "not ready";
    } catch (failure) {
      last = failure.message;
    }
    await delay(500);
  }
  throw new Error(`timed out waiting for ${label}: ${last}`);
}

function prepareScratch(jarSource) {
  assert.ok(fs.statSync(jarSource).isFile(), `jar not found: ${jarSource}`);
  const javaCheck = spawnSync(JAVA, ["-version"], { encoding: "utf8", windowsHide: true });
  assert.equal(javaCheck.status, 0, "java is not on PATH (JDK 25 is required)");
  fs.mkdirSync(base, { recursive: true });
  scratchRoot = fs.mkdtempSync(path.join(base, "run-"));
  jarPath = path.join(scratchRoot, "jahaziel.jar");
  dataDir = path.join(scratchRoot, "data");
  fs.mkdirSync(path.join(dataDir, "tmp"), { recursive: true });
  fs.copyFileSync(jarSource, jarPath);
}

function isScratch(pathToCheck) {
  return path.dirname(pathToCheck) === base && path.basename(pathToCheck).startsWith("run-");
}

function makeWritable(root) {
  if (!fs.existsSync(root)) return;
  // A read-only directory cannot have its contents unlinked; chmod dirs as well as files.
  fs.chmodSync(root, 0o777);
  for (const entry of fs.readdirSync(root, { withFileTypes: true })) {
    const full = path.join(root, entry.name);
    if (entry.isDirectory()) makeWritable(full);
    else fs.chmodSync(full, 0o666);
  }
}

const REMOVE_RETRY_CODES = new Set(["EACCES", "EPERM", "EBUSY", "ENOTEMPTY", "EEXIST"]);

async function removeScratch() {
  if (!scratchRoot || !isScratch(scratchRoot)) return;
  let last;
  for (let attempt = 0; attempt < 5; attempt += 1) {
    try {
      if (!IS_WINDOWS) makeWritable(scratchRoot);
      fs.rmSync(scratchRoot, { recursive: true, force: true });
      if (fs.existsSync(scratchRoot)) {
        throw Object.assign(new Error("scratch dir still present"), { code: "ENOTEMPTY" });
      }
      console.error(`deployment acceptance: removed scratch ${scratchRoot}`);
      return;
    } catch (failure) {
      last = failure;
      if (!REMOVE_RETRY_CODES.has(failure.code) || attempt === 4) break;
      await delay(100 * (attempt + 1));
    }
  }
  console.error(`deployment acceptance: could not remove scratch ${scratchRoot}: ${last.message}`);
  process.exitCode = 1;
}

async function run() {
  const jarSource = parseArgs(process.argv.slice(2));
  prepareScratch(jarSource);
  console.error(`deployment acceptance: scratch ${scratchRoot}`);

  const initOutput = runJar(["init"]);
  assert.ok(fs.existsSync(path.join(scratchRoot, ".env")), "init must write .env beside the jar");
  check(`init (${initOutput.trim().split("\n").length} lines)`);

  const port = await freePort();
  const origin = `http://127.0.0.1:${port}`;
  let server = startServer(port);
  try {
    await waitForHealth(origin, server);
    check("actuator health UP on loopback");

    const login = await request(origin, "GET", "/api/login", { expect: 302 });
    assert.equal(login.response.headers.get("location"), "/", "demo login must return to the shell");
    const authMe = await request(origin, "GET", "/api/auth/me");
    assert.equal(authMe.json.email, "demo@example.com");
    check("demo sign-in");

    const me = await request(origin, "GET", "/api/v1/me");
    assert.equal(me.json.email, "demo@example.com");
    assert.ok(me.json.roles.includes("ROLE_ADMIN"), "demo identity must be an admin");
    check("/api/v1/me");

    const templates = await request(origin, "GET", "/api/v1/templates");
    assert.ok(Array.isArray(templates.json) && templates.json.includes("node"));
    check("template list");

    const stamp = `${Date.now()}-${process.pid}`;
    const created = await request(origin, "POST", "/api/v1/templates/node/apply", {
      body: { workspaceName: `acceptance-${stamp}` },
      expect: 201,
    });
    const workspaceId = created.json.workspaceId;
    assert.ok(workspaceId, "template apply must return a workspace id");
    check("create workspace from template");

    const taskBase = `/api/v1/workspaces/${workspaceId}`;
    await request(origin, "POST", `${taskBase}/start`);
    await eventually(async () => {
      const setup = await request(origin, "GET", `${taskBase}/setup`);
      return setup.json?.status === "READY" ? setup.json : null;
    }, 60_000, "template setup to become READY");
    check("workspace start and setup READY");

    const files = await request(origin, "GET", `/api/v1/workspaces/${workspaceId}/files?path=.`);
    assert.ok(files.json.some((entry) => entry.name === "README.md"));
    check("file list");

    const documentRoute = `/api/v1/workspaces/${workspaceId}/files/document?path=README.md`;
    const document = await request(origin, "GET", documentRoute);
    const etag = document.response.headers.get("etag");
    assert.ok(etag, "file read must return an ETag");
    const marker = `acceptance-${stamp}`;
    const saved = await request(origin, "PUT", documentRoute, {
      headers: { "If-Match": etag },
      body: { content: `# ${marker}\n` },
    });
    const savedEtag = saved.response.headers.get("etag");
    assert.ok(savedEtag && savedEtag !== etag, "save must return a fresh ETag");
    const reread = await request(origin, "GET", documentRoute);
    assert.match(reread.json.content, new RegExp(marker));
    const stale = await fetch(origin + documentRoute, {
      method: "PUT",
      headers: { Origin: origin, "Content-Type": "application/json", "If-Match": etag },
      body: JSON.stringify({ content: "stale\n" }),
      signal: AbortSignal.timeout(15_000),
    });
    assert.notEqual(stale.status, 200, "a stale ETag must not overwrite the file");
    check("file save with ETag (including stale-write rejection)");

    const snapshotRoute = `/api/v1/workspaces/${workspaceId}/snapshots`;
    const snapshot = await request(origin, "POST", snapshotRoute, {
      body: { tag: `acceptance-${stamp}` },
      expect: 201,
    });
    assert.ok(snapshot.json.snapshotId);
    const snapshots = await request(origin, "GET", snapshotRoute);
    assert.ok(snapshots.json.some((entry) => entry.tag === `acceptance-${stamp}`));
    check("snapshot create/list");

    const taskStarted = await request(origin, "POST", `${taskBase}/tasks/typescript-example/run`);
    assert.ok(taskStarted.json.id, "task run must return an id");
    const completed = await eventually(async () => {
      const latest = await request(origin, "GET", `${taskBase}/task-runs/latest`);
      return latest.json?.status === "SUCCEEDED" && latest.json.exitCode === 0
        ? latest.json
        : null;
    }, 60_000, "task run to succeed");
    check(`task run/latest (${completed.id})`);

    assert.equal(await portIsListening(3000), false, "preview port 3000 is already in use");
    const previewStarted = await request(origin, "POST", `${taskBase}/tasks/start/run`);
    const previewId = previewStarted.json.id;
    const candidate = await eventually(async () => {
      const candidates = await request(origin, "GET", `${taskBase}/ports/candidates`);
      return Array.isArray(candidates.json) && candidates.json.some((entry) => entry.port === 3000)
        ? candidates.json
        : null;
    }, 45_000, "preview candidate discovery");
    assert.ok(candidate.some((entry) => entry.port === 3000 && entry.sources?.includes("TASK")));
    await request(origin, "POST", `${taskBase}/task-runs/${previewId}/stop`);
    await waitForPort(3000, false, 15_000);
    check("preview candidates (and task process cleanup)");

    const diagnostics = await request(origin, "GET", "/_admin/diagnostics");
    assert.match(diagnostics.text, /status=ok/);
    await request(origin, "GET", "/api/v1/admin/ops/backups/status");
    check("admin health (demo diagnostics and admin API)");

    const logout = await request(origin, "POST", "/api/auth/logout", { expect: 204 });
    assert.ok(logout.response.headers.getSetCookie().some((cookie) => cookie.startsWith("basis_sites_session=")));
    check("logout");

    await stopChild(server);
    server = null;
    check("server stopped before backup");

    // Windows can hold a freshly released workspace file for a moment after the server exits.
    let backupOutput;
    for (let attempt = 0; attempt < 3; attempt += 1) {
      try {
        backupOutput = runJar(["backup"]);
        break;
      } catch (failure) {
        const locked = /being used|cannot access|another program|另一个程序|sharing violation|AccessDenied/i;
        if (attempt === 2 || !locked.test(failure.message)) throw failure;
        await delay(500 * (attempt + 1));
      }
    }
    const archiveMatch = backupOutput.match(/^jahaziel: backup: (.+\.tar\.gz)/m);
    assert.ok(archiveMatch, `backup did not report an archive:\n${backupOutput}`);
    const archive = path.resolve(scratchRoot, archiveMatch[1]);
    assert.ok(isScratch(path.dirname(archive)) && fs.statSync(archive).isFile());
    check("backup archive created jar-adjacent");

    const readmeOnDisk = path.join(dataDir, "workspaces", workspaceId, "home", "README.md");
    assert.match(fs.readFileSync(readmeOnDisk, "utf8"), new RegExp(marker));
    // Simulate a lost data directory: snapshots are read-only, so tar cannot replace them in place.
    makeWritable(dataDir);
    fs.rmSync(dataDir, { recursive: true, force: true });
    runJar(["restore", archive], 120_000);
    fs.mkdirSync(path.join(dataDir, "tmp"), { recursive: true });
    assert.match(fs.readFileSync(readmeOnDisk, "utf8"), new RegExp(marker));
    check("restore drill (backup restores a lost data directory)");

    server = startServer(port);
    await waitForHealth(origin, server);
    const afterRestore = await request(origin, "GET", documentRoute);
    assert.match(afterRestore.json.content, new RegExp(marker));
    check("restored data survives a fresh boot");
    await stopChild(server);
    server = null;
    await waitForPort(port, false, 10_000);
    check("no listener left on the test port");

    console.error(`deployment acceptance passed: ${CHECKS.length} checks.`);
  } finally {
    await stopChild(server).catch((failure) => console.error(failure));
  }
}

run()
  .catch((failure) => {
    if (failure.message === "HELP") {
      console.error(usage());
      return;
    }
    console.error(failure);
    if (serverLogs.length) console.error(serverLogs.join(""));
    process.exitCode = 1;
  })
  .finally(async () => {
    for (const child of children) await stopChild(child).catch(() => undefined);
    children.clear();
    await removeScratch();
  });
