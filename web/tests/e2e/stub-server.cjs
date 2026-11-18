/* Copyright 2026 The Jahaziel Authors. SPDX-License-Identifier: Apache-2.0.
 * Self-contained, dependency-free backend stub for the Playwright suite.
 * All state is in memory; nothing is written to disk.
 */
"use strict";

const crypto = require("node:crypto");
const fs = require("node:fs");
const http = require("node:http");
const path = require("node:path");

const PORT = Number(process.env.E2E_API_PORT || 5199);
const STATIC_ROOT = process.env.E2E_STATIC_DIR
  ? path.resolve(process.env.E2E_STATIC_DIR) : null;
const MIME = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".mjs": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".ico": "image/x-icon",
  ".webmanifest": "application/manifest+json",
  ".wasm": "application/wasm",
};
const USER = { displayName: "E2E User", email: "e2e@example.invalid" };
const SESSION = "e2e_session";

const workspaces = new Map();
const terminals = new Map();
const debugSessions = new Map();
const taskRuns = new Map();
let latestTaskRunId = null;

function json(res, status, body, headers = {}) {
  const payload = JSON.stringify(body ?? null);
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "content-length": Buffer.byteLength(payload),
    "cache-control": "no-store",
    ...headers,
  });
  res.end(payload);
}
function noContent(res) { res.writeHead(204); res.end(); }
function text(res, status, body, type = "text/plain; charset=utf-8") {
  res.writeHead(status, { "content-type": type });
  res.end(body);
}
function signedIn(req) {
  const cookies = String(req.headers.cookie || "").split(";");
  return cookies.some((entry) => entry.trim() === `${SESSION}=1`);
}
function body(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    req.on("data", (chunk) => chunks.push(chunk));
    req.on("end", () => {
      const raw = Buffer.concat(chunks).toString("utf8");
      if (!raw) return resolve({});
      try { resolve(JSON.parse(raw)); } catch (failure) { reject(failure); }
    });
    req.on("error", reject);
  });
}

function fileEntries(files) {
  const dirs = new Set();
  for (const path of Object.keys(files)) {
    const parts = path.split("/");
    for (let i = 1; i < parts.length; i += 1) dirs.add(parts.slice(0, i).join("/"));
  }
  return (path) => {
    const prefix = path === "." || path === "" ? "" : `${path}/`;
    const entries = [];
    for (const dir of dirs) {
      if (!dir.startsWith(prefix)) continue;
      const rest = dir.slice(prefix.length);
      if (!rest || rest.includes("/")) continue;
      entries.push({ name: rest, path: dir, directory: true, size: 0, modifiedAt: Date.now() });
    }
    for (const [filePath, content] of Object.entries(files)) {
      if (!filePath.startsWith(prefix)) continue;
      const rest = filePath.slice(prefix.length);
      if (!rest || rest.includes("/")) continue;
      entries.push({ name: rest, path: filePath, directory: false, size: Buffer.byteLength(content), modifiedAt: Date.now() });
    }
    return entries.sort((a, b) => Number(b.directory) - Number(a.directory) || a.name.localeCompare(b.name));
  };
}

function createWorkspace(name, template) {
  const workspaceId = crypto.randomUUID();
  const contents = {
    "README.md": `# ${name}\n\nYour project files live in this workspace.\n`,
    "main.py": "def main():\n    print('Hello from Jahaziel')\n\n\nif __name__ == '__main__':\n    main()\n",
    "notes.txt": "Searchable content lives in this workspace.\nJahaziel E2E marker: hello from the stub.\n",
    "src/index.ts": "export const greet = () => 'Hello from Jahaziel';\n",
    ".vscode/launch.json": '{\n  "version": "0.2.0",\n  "configurations": [\n    {\n      "name": "Python: main.py",\n      "type": "python",\n      "request": "launch",\n      "program": "main.py"\n    }\n  ]\n}\n',
  };
  const versions = new Map(Object.keys(contents).map((path) => [path, 1]));
  const workspace = {
    workspaceId,
    name,
    template,
    state: "ready",
    files: contents,
    versions,
    changes: [{ path: "README.md", indexStatus: " ", worktreeStatus: "M" }],
    branch: "main",
    languagePacks: [
      { id: "python", name: "Pyright", version: "1.1.0", selected: false, installed: false,
        availability: "available", supportedByTemplate: true, supportedArchitectures: ["x64", "arm64"],
        downloadBytes: 12_000_000, checksums: ["sha256:stub"], installationNote: "" },
      { id: "typescript", name: "TypeScript", version: "5.0.0", selected: false, installed: false,
        availability: "available", supportedByTemplate: true, supportedArchitectures: ["x64"],
        downloadBytes: 8_000_000, checksums: ["sha256:stub"], installationNote: "" },
    ],
    previewRegistered: false,
  };
  workspaces.set(workspaceId, workspace);
  return workspace;
}
function publicWorkspace(workspace) {
  return {
    workspaceId: workspace.workspaceId,
    name: workspace.name,
    state: workspace.state,
    ports: workspace.previewRegistered
      ? [{ port: 8000, protocol: "http", url: `/__preview__/${workspace.workspaceId}`, listening: true }]
      : [],
  };
}
function setupStatus() {
  return { status: "READY", output: "Template setup completed.", error: "", updatedAt: new Date().toISOString(), correlationId: "e2e-setup-1" };
}
function tasks() {
  return [
    { id: "example", label: "Run example", port: null },
    { id: "preview", label: "Start Python preview server", port: 8000 },
  ];
}
function taskRun(id, workspaceId, taskId, label, status, output, exitCode) {
  return { id, workspaceId, taskId, label, status, output, exitCode };
}
function emptyRun() {
  return { id: "no-run", workspaceId: "", taskId: "", label: "No run yet", status: "IDLE", output: "", exitCode: null };
}
function debugTargets(workspaceId) {
  const run = latestTaskRunId ? taskRuns.get(latestTaskRunId) : null;
  if (!run || run.workspaceId !== workspaceId || run.taskId !== "preview") return [];
  return [{ id: "target-1", taskRunId: run.id, languageId: "python", label: "Python preview" }];
}
function opencodeStatus() {
  return { id: "opencode", name: "OpenCode", version: "1.2.3", architecture: process.arch || "x64",
    status: "INSTALLED", downloadSizeBytes: 1, installedSizeBytes: 1, progressPercent: 100, error: "" };
}

async function handleApi(req, res, url) {
  const method = req.method || "GET";
  const parts = url.pathname.split("/").filter(Boolean);

  if (url.pathname === "/actuator/health") return json(res, 200, { status: "UP" });
  if (url.pathname === "/api/login") {
    res.writeHead(302, {
      "set-cookie": `${SESSION}=1; Path=/; HttpOnly; SameSite=Lax`,
      location: "/",
    });
    return res.end();
  }
  if (url.pathname === "/api/logout") {
    res.writeHead(302, { "set-cookie": `${SESSION}=; Path=/; Max-Age=0`, location: "/" });
    return res.end();
  }
  if (!signedIn(req)) {
    if (url.pathname === "/api/v1/me") return json(res, 401, { message: "Sign in required" });
    return json(res, 401, { message: "Sign in required" });
  }

  if (url.pathname === "/api/v1/me" && method === "GET") return json(res, 200, USER);
  if (url.pathname === "/api/v1/me/workspaces" && method === "GET") {
    return json(res, 200, [...workspaces.values()].map(publicWorkspace));
  }
  if (url.pathname === "/api/v1/templates" && method === "GET") {
    return json(res, 200, ["python", "node", "empty"]);
  }
  if (parts[0] === "api" && parts[1] === "v1" && parts[2] === "templates" && parts[4] === "apply" && method === "POST") {
    const data = await body(req);
    const workspace = createWorkspace(String(data.workspaceName || "untitled"), parts[3]);
    return json(res, 201, publicWorkspace(workspace));
  }
  if (url.pathname === "/api/v1/me/tools" && method === "GET") return json(res, 200, [opencodeStatus()]);
  if (url.pathname === "/api/v1/me/tools/opencode/install" && method === "POST") return json(res, 200, opencodeStatus());
  if (url.pathname === "/api/v1/me/tools/opencode/update" && method === "POST") return json(res, 200, opencodeStatus());
  if (url.pathname === "/api/v1/me/tools/opencode" && method === "DELETE") return json(res, 200, opencodeStatus());
  if (parts[0] === "api" && parts[1] === "v1" && parts[2] === "terminals" && parts[4] === "stream") return noContent(res);
  if (parts[0] === "api" && parts[1] === "v1" && parts[2] === "terminals" && method === "DELETE") {
    terminals.delete(parts[3]);
    return noContent(res);
  }

  if (parts[0] !== "api" || parts[1] !== "v1" || parts[2] !== "workspaces" || !parts[3]) {
    return json(res, 404, { message: "Not found" });
  }
  const workspaceId = parts[3];
  const workspace = workspaces.get(workspaceId);
  if (!workspace) return json(res, 404, { message: "Workspace not found" });
  const tail = parts.slice(4);

  if (tail.length === 0 && method === "GET") return json(res, 200, publicWorkspace(workspace));
  if (tail[0] === "start" && method === "POST") { workspace.state = "ready"; return json(res, 200, publicWorkspace(workspace)); }
  if (tail[0] === "stop" && method === "POST") { workspace.state = "idle"; return json(res, 200, publicWorkspace(workspace)); }
  if (tail[0] === "setup" && method === "GET") return json(res, 200, setupStatus());
  if (tail[0] === "setup" && tail[1] === "retry" && method === "POST") return json(res, 200, setupStatus());
  if (tail[0] === "tasks" && tail.length === 1 && method === "GET") return json(res, 200, tasks());
  if (tail[0] === "tasks" && tail[2] === "run" && method === "POST") {
    const taskId = tail[1];
    const id = crypto.randomUUID();
    const label = tasks().find((item) => item.id === taskId)?.label || taskId;
    const run = taskRun(id, workspaceId, taskId, label, "RUNNING", "Starting task…\n", null);
    taskRuns.set(id, run);
    latestTaskRunId = id;
    setTimeout(() => {
      if (taskId === "example") {
        run.status = "SUCCEEDED";
        run.output = "Hello from Jahaziel\n";
        run.exitCode = 0;
      } else {
        run.output = "Preview server listening on port 8000\n";
        workspace.previewRegistered = true;
      }
    }, 250);
    return json(res, 201, run);
  }
  if (tail[0] === "tasks" && tail[1] === "debug-targets" && method === "GET") return json(res, 200, debugTargets(workspaceId));
  if (tail[0] === "task-runs" && tail[1] === "latest" && method === "GET") {
    return json(res, 200, latestTaskRunId ? taskRuns.get(latestTaskRunId) : emptyRun());
  }
  if (tail[0] === "task-runs" && tail[2] === "stop" && method === "POST") {
    const run = taskRuns.get(tail[1]);
    if (!run) return json(res, 404, { message: "Run not found" });
    run.status = "STOPPED";
    return json(res, 200, run);
  }
  if (tail[0] === "task-runs" && tail.length === 2 && method === "GET") {
    const run = taskRuns.get(tail[1]);
    return run ? json(res, 200, run) : json(res, 404, { message: "Run not found" });
  }

  if (tail[0] === "git" && tail.length === 1 && method === "GET") {
    return json(res, 200, { repository: true, branch: workspace.branch, changes: workspace.changes });
  }
  if (tail[0] === "git" && tail[1] === "diff" && method === "GET") {
    return json(res, 200, { path: url.searchParams.get("path") || "README.md",
      content: "diff --git a/README.md b/README.md\n@@ -1 +1,2 @@\n+Changes from the E2E stub.\n" });
  }
  if ((tail[0] === "git" && (tail[1] === "stage" || tail[1] === "unstage")) && method === "POST") {
    const data = await body(req);
    workspace.changes = workspace.changes.map((change) => change.path === data.path
      ? { ...change, indexStatus: tail[1] === "stage" ? "M" : " ", worktreeStatus: tail[1] === "stage" ? " " : "M" }
      : change);
    return json(res, 200, { repository: true, branch: workspace.branch, changes: workspace.changes });
  }
  if (tail[0] === "git" && tail[1] === "commit" && method === "POST") {
    await body(req);
    workspace.changes = [];
    return json(res, 200, { commit: "e2e0000", summary: "E2E commit" });
  }

  if (tail[0] === "language-packs" && tail.length === 1 && method === "GET") return json(res, 200, workspace.languagePacks);
  if (tail[0] === "language-packs" && tail.length === 2 && (method === "POST" || method === "PUT")) {
    const pack = workspace.languagePacks.find((item) => item.id === tail[1]);
    if (!pack) return json(res, 404, { message: "Language pack not found" });
    pack.selected = true;
    pack.installed = true;
    return json(res, 200, pack);
  }
  if (tail[0] === "language-packs" && tail.length === 2 && method === "DELETE") {
    const pack = workspace.languagePacks.find((item) => item.id === tail[1]);
    if (pack) { pack.selected = false; pack.installed = false; }
    return noContent(res);
  }

  if (tail[0] === "ports" && tail.length === 1 && method === "GET") {
    return json(res, 200, publicWorkspace(workspace).ports);
  }
  if (tail[0] === "ports" && tail[1] === "candidates" && method === "GET") {
    return json(res, 200, [{ port: 8000, sources: ["tracked task", "stub"] }, { port: 8080, sources: ["stub"] }]);
  }
  if (tail[0] === "ports" && tail.length === 1 && method === "POST") {
    const data = await body(req);
    workspace.previewRegistered = true;
    return json(res, 201, { port: Number(data.port) || 8000, protocol: "http",
      url: `/__preview__/${workspaceId}`, listening: true });
  }

  if (tail[0] === "terminals" && tail.length === 1 && method === "GET") {
    return json(res, 200, [...terminals.values()].filter((session) => session.workspaceId === workspaceId));
  }
  if (tail[0] === "terminals" && tail.length === 1 && method === "POST") {
    const query = new URLSearchParams({ rows: "28", cols: "100" });
    for (const [key, value] of url.searchParams) query.set(key, value);
    const session = { sessionId: crypto.randomUUID(), workspaceId,
      startedAt: new Date().toISOString(), lastActivityAt: new Date().toISOString(),
      rows: Number(query.get("rows")) || 28, cols: Number(query.get("cols")) || 100 };
    terminals.set(session.sessionId, session);
    return json(res, 201, session);
  }
  if (tail[0] === "terminals" && tail[1] === "opencode" && method === "POST") {
    const session = { sessionId: crypto.randomUUID(), workspaceId, startedAt: new Date().toISOString(),
      lastActivityAt: new Date().toISOString(), rows: 28, cols: 100 };
    terminals.set(session.sessionId, session);
    return json(res, 201, session);
  }

  if (tail[0] === "debug-sessions" && tail.length === 1 && method === "GET") {
    return json(res, 200, [...debugSessions.values()].filter((session) => session.workspaceId === workspaceId));
  }
  if (tail[0] === "debug-sessions" && tail.length === 1 && method === "POST") {
    const data = await body(req);
    const session = { id: crypto.randomUUID(), workspaceId,
      languageId: data.languageId || "python", adapterId: "e2e-adapter", state: "RUNNING",
      adapterVersion: "stub", startedAt: new Date().toISOString(), error: "" };
    debugSessions.set(session.id, session);
    return json(res, 201, session);
  }
  if (tail[0] === "debug-sessions" && tail[1] === "attach" && method === "POST") {
    const data = await body(req);
    const session = { id: crypto.randomUUID(), workspaceId, languageId: data.languageId || "python",
      adapterId: "e2e-adapter", state: "RUNNING", adapterVersion: "stub",
      startedAt: new Date().toISOString(), error: "" };
    debugSessions.set(session.id, session);
    return json(res, 201, session);
  }
  if (tail[0] === "debug-sessions" && tail.length === 2 && method === "GET") {
    const session = debugSessions.get(tail[1]);
    return session ? json(res, 200, session) : json(res, 404, { message: "Session not found" });
  }
  if (tail[0] === "debug-sessions" && tail[2] === "stop" && method === "POST") {
    const session = debugSessions.get(tail[1]);
    if (!session) return json(res, 404, { message: "Session not found" });
    session.state = "STOPPED";
    return json(res, 200, session);
  }

  if (tail[0] === "files" && tail.length === 1 && method === "GET") {
    return json(res, 200, fileEntries(workspace.files)(url.searchParams.get("path") || "."));
  }
  if (tail[0] === "files" && tail[1] === "search" && method === "GET") {
    const query = String(url.searchParams.get("q") || "").toLowerCase();
    const results = [];
    for (const [path, content] of Object.entries(workspace.files)) {
      const lines = content.split("\n");
      for (let i = 0; i < lines.length; i += 1) {
        if (path.toLowerCase().includes(query) || lines[i].toLowerCase().includes(query)) {
          results.push({ path, line: i + 1, preview: lines[i].trim().slice(0, 160) || "(empty line)" });
          break;
        }
      }
    }
    return json(res, 200, results.slice(0, Number(url.searchParams.get("limit")) || 50));
  }
  if (tail[0] === "files" && tail[1] === "download" && method === "GET") {
    const path = url.searchParams.get("path") || "";
    if (!(path in workspace.files)) return json(res, 404, { message: "File not found" });
    return text(res, 200, workspace.files[path]);
  }
  if (tail[0] === "files" && tail[1] === "document" && method === "GET") {
    const path = url.searchParams.get("path") || "";
    if (!(path in workspace.files)) return json(res, 404, { message: "File not found" });
    const version = `"${workspace.versions.get(path)}"`;
    return json(res, 200, { path, content: workspace.files[path], version }, { etag: version });
  }
  if (tail[0] === "files" && tail[1] === "document" && method === "PUT") {
    const path = url.searchParams.get("path") || "";
    const data = await body(req);
    const exists = path in workspace.files;
    if (req.headers["if-none-match"] === "*") {
      if (exists) return json(res, 412, { message: "File already exists" });
      workspace.files[path] = String(data.content ?? "");
      workspace.versions.set(path, 1);
      const version = '"1"';
      return json(res, 201, { path, content: workspace.files[path], version }, { etag: version });
    }
    if (!exists) return json(res, 404, { message: "File not found" });
    const current = workspace.versions.get(path);
    if (req.headers["if-match"] && req.headers["if-match"] !== `"${current}"`) {
      return json(res, 412, { message: "The file changed on disk" });
    }
    workspace.files[path] = String(data.content ?? "");
    workspace.versions.set(path, current + 1);
    const version = `"${current + 1}"`;
    return json(res, 200, { path, content: workspace.files[path], version }, { etag: version });
  }
  if (tail[0] === "files" && tail[1] === "directory" && method === "POST") {
    const data = await body(req);
    const dir = String(data.path || "").replace(/\/$/, "");
    workspace.files[`${dir}/.gitkeep`] = "";
    workspace.versions.set(`${dir}/.gitkeep`, 1);
    return json(res, 201, { path: dir, directory: true });
  }
  if (tail[0] === "files" && tail[1] === "rename" && method === "POST") {
    const data = await body(req);
    const from = String(data.path || "");
    const to = String(data.newPath || "");
    if (!(from in workspace.files)) return json(res, 404, { message: "File not found" });
    workspace.files[to] = workspace.files[from];
    workspace.versions.set(to, workspace.versions.get(from));
    delete workspace.files[from];
    workspace.versions.delete(from);
    return json(res, 200, { path: to });
  }
  if (tail[0] === "files" && tail.length === 1 && method === "DELETE") {
    const path = url.searchParams.get("path") || "";
    delete workspace.files[path];
    workspace.versions.delete(path);
    return noContent(res);
  }

  return json(res, 404, { message: "Not found" });
}

/* ---- Minimal RFC 6455 server (no dependency) ---- */
const WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
function attachWebSocket(req, socket, onOpen, onMessage) {
  const accept = crypto.createHash("sha1").update(String(req.headers["sec-websocket-key"]) + WS_GUID).digest("base64");
  socket.write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
    + `Sec-WebSocket-Accept: ${accept}\r\n\r\n`);
  let sequence = 1;
  function send(message) {
    if (socket.destroyed) return;
    const payload = Buffer.from(typeof message === "string" ? message : JSON.stringify(message));
    let header;
    if (payload.length < 126) header = Buffer.from([0x81, payload.length]);
    else if (payload.length < 65536) {
      header = Buffer.alloc(4); header[0] = 0x81; header[1] = 126; header.writeUInt16BE(payload.length, 2);
    } else {
      header = Buffer.alloc(10); header[0] = 0x81; header[1] = 127; header.writeBigUInt64BE(BigInt(payload.length), 2);
    }
    socket.write(Buffer.concat([header, payload]));
  }
  let buffer = Buffer.alloc(0);
  socket.on("data", (chunk) => {
    buffer = Buffer.concat([buffer, chunk]);
    while (buffer.length >= 2) {
      const opcode = buffer[0] & 0x0f;
      const masked = (buffer[1] & 0x80) !== 0;
      let length = buffer[1] & 0x7f;
      let offset = 2;
      if (length === 126) { if (buffer.length < 4) return; length = buffer.readUInt16BE(2); offset = 4; }
      else if (length === 127) { if (buffer.length < 10) return; length = Number(buffer.readBigUInt64BE(2)); offset = 10; }
      const maskLength = masked ? 4 : 0;
      if (buffer.length < offset + maskLength + length) return;
      let mask = null;
      if (masked) { mask = buffer.subarray(offset, offset + 4); offset += 4; }
      const payload = Buffer.from(buffer.subarray(offset, offset + length));
      buffer = buffer.subarray(offset + length);
      if (mask) for (let i = 0; i < payload.length; i += 1) payload[i] ^= mask[i % 4];
      if (opcode === 0x8) { socket.end(); return; }
      if (opcode === 0x9) { socket.write(Buffer.concat([Buffer.from([0x8a, payload.length]), payload])); continue; }
      if (opcode !== 0x1) continue;
      try { onMessage(JSON.parse(payload.toString("utf8")), send, () => sequence); }
      catch { /* Ignore malformed client frames. */ }
    }
  });
  onOpen(send, () => sequence++);
}

function handleDebugMessage(message, send, nextSequence) {
  if (message.type !== "request") return;
  const command = String(message.command || "");
  let result = {};
  if (command === "initialize") result = { supportsConfigurationDoneRequest: true };
  else if (command === "threads") result = { threads: [{ id: 1, name: "E2E thread" }] };
  else if (command === "setBreakpoints") {
    result = { breakpoints: (message.arguments?.breakpoints || []).map((item) => ({ ...item, verified: true })) };
  } else if (command === "stackTrace") {
    result = { stackFrames: [{ id: 1, name: "main", source: { name: "main.py", path: "main.py" }, line: 1, column: 1 }] };
  } else if (command === "scopes") result = { scopes: [{ name: "Locals", variablesReference: 1, expensive: false }] };
  else if (command === "variables") result = { variables: [{ name: "answer", value: "42", type: "int" }] };
  else if (command === "evaluate") result = { result: "42", type: "int" };
  send({ seq: nextSequence(), type: "response", request_seq: message.seq, success: true, command, body: result });
  if (command === "launch" || command === "attach") {
    setTimeout(() => send({ seq: nextSequence(), type: "event", event: "initialized", body: {} }), 50);
  }
}

function serveStatic(res, pathname) {
  if (!STATIC_ROOT) return false;
  const relative = decodeURIComponent(pathname).replace(/^\/+/, "") || "index.html";
  const target = path.resolve(STATIC_ROOT, relative);
  if (target !== STATIC_ROOT && !target.startsWith(STATIC_ROOT + path.sep)) {
    text(res, 403, "Forbidden");
    return true;
  }
  let file = target;
  try {
    if (fs.statSync(file).isDirectory()) file = path.join(file, "index.html");
  } catch { /* fall through to 404 */ }
  if (!fs.existsSync(file) || !fs.statSync(file).isFile()) {
    const fallback = path.join(STATIC_ROOT, "index.html");
    if (!fs.existsSync(fallback)) return false;
    file = fallback;
  }
  res.writeHead(200, { "content-type": MIME[path.extname(file).toLowerCase()] || "application/octet-stream" });
  fs.createReadStream(file).pipe(res);
  return true;
}

function handleLspMessage(message, send) {
  if (message.jsonrpc !== "2.0") return;
  if (message.method === "initialize") {
    send({ jsonrpc: "2.0", id: message.id, result: { capabilities: { textDocumentSync: 1 } } });
    return;
  }
  if (message.method === "textDocument/didOpen" || message.method === "textDocument/didChange") {
    const uri = message.params?.textDocument?.uri;
    if (!uri) return;
    send({ jsonrpc: "2.0", method: "textDocument/publishDiagnostics", params: {
      uri,
      diagnostics: [{ range: { start: { line: 0, character: 0 }, end: { line: 0, character: 1 } },
        severity: 1, message: "E2E stub diagnostic on line 1" }],
    } });
  }
}

const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, `http://127.0.0.1:${PORT}`);
    if (url.pathname.startsWith("/__preview__/")) {
      return text(res, 200, "<!doctype html><html><body><h1>Hello from Jahaziel</h1></body></html>", "text/html; charset=utf-8");
    }
    if (url.pathname.startsWith("/api/") || url.pathname.startsWith("/actuator/")) {
      return await handleApi(req, res, url);
    }
    if (serveStatic(res, url.pathname)) return;
    return json(res, 404, { message: "Not found" });
  } catch (failure) {
    if (!res.headersSent) json(res, 500, { message: failure.message || "stub failure" });
    else res.end();
  }
});

server.on("upgrade", (req, socket) => {
  const url = new URL(req.url, `http://127.0.0.1:${PORT}`);
  if (!signedIn(req)) { socket.destroy(); return; }
  const segments = url.pathname.split("/").filter(Boolean);
  if (segments[0] === "api" && segments[1] === "v1" && segments[2] === "terminals" && segments[4] === "stream") {
    const session = terminals.get(segments[3]);
    if (!session) { socket.destroy(); return; }
    attachWebSocket(req, socket,
      (send) => send({ type: "history", data: "\x1b[32mJahaziel E2E terminal\x1b[0m\r\n$ " }),
      (message, send) => {
        if (message.type === "resize") {
          session.rows = Number(message.rows) || session.rows;
          session.cols = Number(message.cols) || session.cols;
          session.lastActivityAt = new Date().toISOString();
        } else if (message.type === "input") {
          // Echo like a real PTY: characters stay contiguous until Enter.
          send({ type: "output", data: String(message.data ?? "").replace(/[\r\n]+/g, "\r\n$ ") });
        }
      });
    return;
  }
  const debugIndex = segments.indexOf("debug-sessions");
  if (debugIndex > 0 && segments[debugIndex + 2] === "stream") {
    if (!debugSessions.has(segments[debugIndex + 1])) { socket.destroy(); return; }
    attachWebSocket(req, socket, () => undefined, handleDebugMessage);
    return;
  }
  const lspIndex = segments.indexOf("language-services");
  if (lspIndex > 0) {
    attachWebSocket(req, socket, () => undefined, handleLspMessage);
    return;
  }
  socket.destroy();
});

server.listen(PORT, "127.0.0.1", () => {
  process.stdout.write(`E2E stub backend listening on http://127.0.0.1:${PORT}\n`);
});
