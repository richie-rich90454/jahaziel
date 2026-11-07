///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import type { EditorView } from "codemirror";
import { LanguageServices, type LanguageDiagnostic, type LanguagePack } from "./language-services";
import "@xterm/xterm/css/xterm.css";
import "./workbench.css";

interface User { displayName: string; email: string; }
interface PortEntry { port: number; protocol: string; url: string; listening: boolean | null; }
interface Workspace {
  workspaceId: string;
  name: string;
  state: string;
  stateReason?: string | null;
  ports: PortEntry[];
}
interface FileEntry { name: string; path: string; directory: boolean; size: number; modifiedAt: number; }
interface DocumentBody { path: string; content: string; version: string; }
interface SearchResult { path: string; line: number; preview: string; }
interface ApiErrorBody { message?: string; code?: string; }
interface TerminalSession {
  sessionId: string;
  workspaceId: string;
  startedAt: string;
  lastActivityAt: string;
  rows: number;
  cols: number;
}
interface WorkspaceTask { id: string; label: string; port: number | null; }
interface SetupStatus { status: string; output: string; error: string; updatedAt: string; correlationId?: string | null; }
interface TaskRun { id: string; workspaceId: string; taskId: string; label: string; status: string; output: string; exitCode: number | null; }
interface GitChange { path: string; indexStatus: string; worktreeStatus: string; }
interface GitStatus { repository: boolean; branch: string; changes: GitChange[]; }
interface GitDiff { path: string; content: string; }
interface StreamMessage { type: "input" | "output" | "resize" | "exit"; data?: string; rows?: number; cols?: number; exitCode?: number; }
interface OpenDocument {
  path: string;
  version: string;
  savedContent: string;
  wrapper: HTMLDivElement;
  view: EditorView;
  dirty: boolean;
}

const byId = <T extends HTMLElement>(id: string): T => {
  const found = document.getElementById(id);
  if (!found) throw new Error(`Missing page element #${id}`);
  return found as T;
};

const app = byId("app");
const homePage = byId("home-page");
const workbenchPage = byId("workbench-page");
const state = {
  user: null as User | null,
  workspace: null as Workspace | null,
  openDocuments: new Map<string, OpenDocument>(),
  activePath: null as string | null,
  selectedPath: null as string | null,
  terminal: null as import("@xterm/xterm").Terminal | null,
  fitAddon: null as import("@xterm/addon-fit").FitAddon | null,
  terminalObserver: null as ResizeObserver | null,
  terminalSessionId: null as string | null,
  terminalSessions: [] as TerminalSession[],
  socket: null as WebSocket | null,
  taskRunId: null as string | null,
  setupPolling: false,
  searchTimer: null as number | null,
  gitHasStaged: false,
  languageServices: null as LanguageServices | null,
  languagePacks: [] as LanguagePack[],
};

async function api<T>(path: string, init: RequestInit = {}): Promise<{ body: T; response: Response }> {
  let response: Response;
  try {
    response = await fetch(path, {
      credentials: "same-origin",
      ...init,
      headers: {
        ...(init.body ? { "Content-Type": "application/json" } : {}),
        ...init.headers,
      },
    });
  } catch {
    throw new Error("The Jahaziel server could not be reached.");
  }
  if (response.status === 204) return { body: null as T, response };
  if (!response.ok) {
    let detail = "Request failed";
    try {
      const body = (await response.json()) as ApiErrorBody;
      detail = body.message || body.code || detail;
    } catch { /* Keep the status for non-JSON errors. */ }
    throw new Error(`${response.status} ${detail}`);
  }
  if (!(response.headers.get("content-type") || "").includes("json")) return { body: null as T, response };
  return { body: (await response.json()) as T, response };
}

function toast(message: string, kind: "error" | "success" = "success"): void {
  const region = byId(app.dataset.page === "workbench" ? "workbench-toasts" : "toasts");
  const item = document.createElement("div");
  item.className = `toast toast-${kind}`;
  item.textContent = message;
  region.append(item);
  window.setTimeout(() => item.remove(), 4800);
}

function button(label: string, action: () => void | Promise<void>, className = "button"): HTMLButtonElement {
  const element = document.createElement("button");
  element.type = "button";
  element.className = className;
  element.textContent = label;
  element.addEventListener("click", async () => {
    element.disabled = true;
    try { await action(); }
    catch (failure) { toast(errorText(failure), "error"); }
    finally { element.disabled = false; }
  });
  return element;
}

function errorText(failure: unknown): string {
  return failure instanceof Error ? failure.message : "Something went wrong.";
}

function setPage(page: "home" | "workbench"): void {
  homePage.hidden = page !== "home";
  workbenchPage.hidden = page !== "workbench";
  app.dataset.page = page;
}

async function loadIdentity(): Promise<void> {
  try {
    const { body } = await api<User>("/api/v1/me");
    state.user = body;
    byId("user-name").textContent = body.displayName || body.email;
    byId("user-email").textContent = body.email;
    byId("login-link").hidden = true;
    byId("user-badge").hidden = false;
    byId("loading-state").textContent = "SYNCED";
  } catch {
    byId("login-link").hidden = false;
    byId("user-badge").hidden = true;
    byId("loading-state").textContent = "SIGN IN TO CONTINUE";
  }
}

async function loadTemplates(): Promise<void> {
  const { body: names } = await api<string[]>("/api/v1/templates");
  const select = byId("template-select") as HTMLSelectElement;
  select.replaceChildren();
  for (const name of names) {
    const option = document.createElement("option");
    option.value = name;
    option.textContent = name.replaceAll("-", " ");
    select.append(option);
  }
  select.disabled = names.length === 0;
  byId<HTMLButtonElement>("create-workspace").disabled = names.length === 0;
  if (names.length === 0) byId("template-note").textContent = "No templates are configured on this server yet.";
}

async function renderWorkspaces(): Promise<void> {
  if (!state.user) return;
  const { body: workspaces } = await api<Workspace[]>("/api/v1/me/workspaces");
  const list = byId("workspace-list");
  list.replaceChildren();
  byId("empty-workspaces").hidden = workspaces.length !== 0;
  byId("workspace-count").textContent = String(workspaces.length).padStart(2, "0");
  for (const workspace of workspaces) list.append(renderWorkspace(workspace));
}

function renderWorkspace(workspace: Workspace): HTMLLIElement {
  const card = document.createElement("li");
  card.className = "workspace-card";
  const identity = document.createElement("div");
  identity.className = "workspace-identity";
  const initial = document.createElement("span");
  initial.className = "workspace-initial";
  initial.textContent = workspace.name.slice(0, 1).toUpperCase();
  const details = document.createElement("div");
  const name = document.createElement("h3");
  name.textContent = workspace.name;
  const id = document.createElement("p");
  id.className = "workspace-id";
  id.textContent = `ID / ${workspace.workspaceId.slice(0, 8)}`;
  details.append(name, id);
  identity.append(initial, details);
  const status = document.createElement("span");
  status.className = `status status-${workspace.state}`;
  status.textContent = workspace.state;
  const actions = document.createElement("div");
  actions.className = "workspace-actions";
  actions.append(button("Open workspace", () => openWorkspace(workspace), "button button-primary"));
  if (["requested", "suspended", "failed"].includes(workspace.state)) {
    actions.append(button("Start", async () => {
      const { body } = await api<Workspace>(`/api/v1/workspaces/${workspace.workspaceId}/start`, { method: "POST" });
      await renderWorkspaces();
      if (state.workspace?.workspaceId === body.workspaceId) state.workspace = body;
    }, "button button-quiet"));
  } else if (["ready", "active", "idle"].includes(workspace.state)) {
    actions.append(button("Stop", async () => {
      await api(`/api/v1/workspaces/${workspace.workspaceId}/stop`, { method: "POST" });
      await renderWorkspaces();
    }, "button button-quiet"));
  }
  card.append(identity, status, actions);
  if (workspace.stateReason) {
    const reason = document.createElement("p");
    reason.className = "workspace-error";
    reason.textContent = workspace.stateReason;
    card.append(reason);
  }
  if (workspace.ports.length) {
    const previews = document.createElement("div");
    previews.className = "workspace-previews";
    for (const port of workspace.ports) {
      const link = document.createElement("a");
      link.href = port.url;
      link.target = "_blank";
      link.rel = "noreferrer";
      link.textContent = `Preview :${port.port} ↗`;
      previews.append(link);
    }
    card.append(previews);
  }
  return card;
}

async function createWorkspace(event: SubmitEvent): Promise<void> {
  event.preventDefault();
  const nameInput = byId("workspace-name") as HTMLInputElement;
  const template = (byId("template-select") as HTMLSelectElement).value;
  const name = nameInput.value.trim();
  if (!name || !template) return;
  const { body: created } = await api<Workspace>(`/api/v1/templates/${encodeURIComponent(template)}/apply`, {
    method: "POST",
    body: JSON.stringify({ workspaceName: name }),
  });
  nameInput.value = "";
  toast(`${created.name} is ready to edit.`);
  await renderWorkspaces();
  await openWorkspace(created);
}

async function openWorkspace(workspace: Workspace, navigate = true): Promise<void> {
  if (state.workspace?.workspaceId === workspace.workspaceId) {
    state.workspace = workspace;
    byId("workbench-title").textContent = workspace.name;
    byId("workbench-state").textContent = workspace.state;
    byId("workbench-state").className = `status status-${workspace.state}`;
    renderPreviewLinks(workspace);
    void refreshPortStatuses().catch(() => undefined);
    if (navigate && !window.location.hash.endsWith(workspace.workspaceId)) {
      history.pushState({ workspaceId: workspace.workspaceId }, "", `#workspace/${workspace.workspaceId}`);
    }
    setPage("workbench");
    refreshGitSafely();
    await refreshFiles();
    await refreshTasks();
    await refreshLanguagePacks();
    await refreshTerminalSessions();
    return;
  }
  if ([...state.openDocuments.values()].some((opened) => opened.dirty)
      && state.workspace && state.workspace.workspaceId !== workspace.workspaceId
      && !window.confirm("Some files have unsaved changes. Leave this workspace?")) return;
  state.workspace = workspace;
  state.languageServices?.close();
  state.languageServices = new LanguageServices(
    workspace.workspaceId,
    (status) => { byId("language-state").textContent = status; },
    renderLanguageDiagnostics,
  );
  state.languagePacks = [];
  state.openDocuments.forEach((opened) => opened.view.destroy());
  state.openDocuments.clear();
  state.activePath = null;
  state.selectedPath = null;
  state.taskRunId = null;
  clearWorkspaceSearch();
  resetGitPanel();
  void closeTerminal(false);
  byId("workbench-title").textContent = workspace.name;
  byId("root-name").textContent = workspace.name.toUpperCase();
  byId("workbench-state").textContent = workspace.state;
  byId("workbench-state").className = `status status-${workspace.state}`;
  byId("runtime-toggle").textContent = ["ready", "active", "idle"].includes(workspace.state) ? "Stop workspace" : "Start workspace";
  renderPreviewLinks(workspace);
  void refreshPortStatuses().catch(() => undefined);
  if (navigate) history.pushState({ workspaceId: workspace.workspaceId }, "", `#workspace/${workspace.workspaceId}`);
  setPage("workbench");
  refreshGitSafely();
  await refreshFiles();
  await refreshTasks();
  await refreshLanguagePacks();
  await refreshTerminalSessions();
  renderTabs();
}

async function refreshTerminalSessions(): Promise<void> {
  const workspace = state.workspace;
  if (!workspace) return;
  const { body } = await api<TerminalSession[]>(`/api/v1/workspaces/${workspace.workspaceId}/terminals`);
  if (state.workspace?.workspaceId !== workspace.workspaceId) return;
  state.terminalSessions = body;
  const detached = body.filter((session) => session.sessionId !== state.terminalSessionId);
  const select = byId<HTMLSelectElement>("terminal-session-select");
  const reconnect = byId<HTMLButtonElement>("terminal-reconnect");
  select.replaceChildren();
  for (const session of detached) {
    const option = document.createElement("option");
    option.value = session.sessionId;
    const started = new Date(session.startedAt);
    const label = Number.isNaN(started.getTime()) ? "Terminal" : `Terminal · ${started.toLocaleTimeString()}`;
    option.textContent = `${label} · ${session.sessionId.slice(0, 8)}`;
    select.append(option);
  }
  select.hidden = detached.length === 0;
  select.disabled = detached.length === 0;
  reconnect.hidden = detached.length === 0;
  reconnect.disabled = detached.length === 0;
  if (!state.terminalSessionId) {
    byId("terminal-state").textContent = detached.length === 0
      ? "No session"
      : `${detached.length} detached session${detached.length === 1 ? "" : "s"}`;
  }
}

async function refreshLanguagePacks(): Promise<void> {
  if (!state.workspace || !state.languageServices) return;
  state.languagePacks = await state.languageServices.refresh();
  renderLanguagePacks();
}

function renderLanguagePacks(): void {
  const list = byId("language-pack-list");
  list.replaceChildren();
  if (state.languagePacks.length === 0) {
    const empty = document.createElement("li");
    empty.className = "language-empty";
    empty.textContent = "No language packs are listed by this server.";
    list.append(empty);
    return;
  }
  for (const pack of state.languagePacks) {
    const row = document.createElement("li");
    row.className = "language-pack";
    const name = document.createElement("span");
    name.className = "language-pack-name";
    name.textContent = `${pack.name} · ${pack.version}`;
    const meta = document.createElement("span");
    meta.className = "language-pack-meta";
    const size = pack.downloadBytes > 0 ? `${formatBytes(pack.downloadBytes)} download` : "size unavailable";
    const status = !pack.supportedByTemplate
      ? "not listed for this template"
      : pack.selected ? (pack.installed ? "enabled" : "selected · missing") : pack.availability;
    meta.textContent = `${status} · ${size}${pack.installationNote ? ` · ${pack.installationNote}` : ""}`;
    meta.title = `${pack.availability}; ${pack.supportedArchitectures.join(", ") || "no supported architecture"}; ${pack.checksums.join("; ")}`;
    const action = document.createElement("button");
    action.type = "button";
    action.className = "language-pack-action";
    action.textContent = pack.selected ? "Disable" : "Enable";
    action.disabled =
      !pack.selected && (!pack.supportedByTemplate || pack.availability !== "available");
    action.addEventListener("click", async () => {
      action.disabled = true;
      try {
        if (pack.selected) {
          await state.languageServices?.disable(pack.id);
          byId("language-state").textContent = "Language pack disabled";
        } else {
          byId("language-state").textContent = `Installing ${pack.name} (${formatBytes(pack.downloadBytes)})…`;
          await state.languageServices?.enable(pack.id);
          toast(`${pack.name} is ready for this workspace.`);
        }
        await refreshLanguagePacks();
        if (state.activePath) activateDocument(state.activePath);
      } catch (failure) {
        byId("language-state").textContent = "Language pack operation failed";
        toast(errorText(failure), "error");
      }
    });
    row.append(name, meta, action);
    list.append(row);
  }
}

function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return "unknown size";
  return bytes >= 1024 * 1024
    ? `${(bytes / (1024 * 1024)).toFixed(0)} MB`
    : `${Math.ceil(bytes / 1024)} KB`;
}

function renderLanguageDiagnostics(diagnostics: LanguageDiagnostic[]): void {
  const panel = byId("language-diagnostics");
  panel.replaceChildren();
  for (const diagnostic of diagnostics) {
    const item = document.createElement("button");
    item.type = "button";
    item.className = `language-diagnostic language-diagnostic-${diagnostic.severity.toLowerCase()}`;
    const location = document.createElement("span");
    location.className = "language-diagnostic-location";
    location.textContent = `${diagnostic.path}:${diagnostic.line}:${diagnostic.column} `;
    item.append(location, document.createTextNode(`${diagnostic.severity}: ${diagnostic.message}`));
    item.title = `${diagnostic.path}:${diagnostic.line}:${diagnostic.column} — ${diagnostic.message}`;
    item.addEventListener("click", async () => {
      await openDocument(diagnostic.path);
      activateDocument(diagnostic.path);
      const opened = state.openDocuments.get(diagnostic.path);
      if (!opened) return;
      const line = Math.min(diagnostic.line, opened.view.state.doc.lines);
      const target = opened.view.state.doc.line(Math.max(1, line));
      const position = Math.min(target.to, target.from + Math.max(0, diagnostic.column - 1));
      opened.view.dispatch({ selection: { anchor: position } });
      opened.view.focus();
    });
    panel.append(item);
  }
}

function languageIdForPath(path: string): string | null {
  const lower = path.toLowerCase();
  if (lower.endsWith(".rs")) return "rust";
  if (lower.endsWith(".go")) return "go";
  if (/\.(c|h|cc|cpp|cxx|hpp|hh|hxx)$/.test(lower)) return "cpp";
  if (lower.endsWith(".java")) return "java";
  if (lower.endsWith(".py")) return "python";
  if (/\.(ts|tsx|mts|cts|js|jsx|mjs|cjs)$/.test(lower)) return "typescript";
  return null;
}

async function refreshTasks(): Promise<void> {
  const workspace = state.workspace;
  if (!workspace) return;
  const [setupResult, tasksResult] = await Promise.all([
    api<SetupStatus>(`/api/v1/workspaces/${workspace.workspaceId}/setup`),
    api<WorkspaceTask[]>(`/api/v1/workspaces/${workspace.workspaceId}/tasks`),
  ]);
  const setup = setupResult.body;
  renderSetupStatus(setup);
  byId("task-correlation").hidden = true;
  const setupOutput = byId<HTMLPreElement>("setup-output");
  setupOutput.hidden = !setup.output;
  setupOutput.textContent = setup.output;
  const list = byId("task-list");
  list.replaceChildren();
  for (const task of tasksResult.body) {
    list.append(button(task.port ? `${task.label}  :${task.port}` : task.label, () => runTask(task), "task-button"));
  }
  if (tasksResult.body.length === 0) {
    const empty = document.createElement("span");
    empty.className = "setup-row";
    empty.textContent = "No run tasks in this template.";
    list.append(empty);
  }
  if (setup.status === "RUNNING" && !state.setupPolling) void pollSetup(workspace.workspaceId);
}

async function pollSetup(workspaceId: string): Promise<void> {
  state.setupPolling = true;
  try {
    while (state.workspace?.workspaceId === workspaceId) {
      await new Promise((resolve) => window.setTimeout(resolve, 1000));
      const { body } = await api<SetupStatus>(`/api/v1/workspaces/${workspaceId}/setup`);
      renderSetupStatus(body);
      const setupOutput = byId<HTMLPreElement>("setup-output");
      setupOutput.textContent = body.output;
      setupOutput.hidden = !body.output;
      if (body.status !== "RUNNING") break;
    }
  } finally {
    state.setupPolling = false;
    if (state.workspace?.workspaceId === workspaceId) await refreshTasks();
  }
}

function renderSetupStatus(setup: SetupStatus): void {
  const workspaceRunning = state.workspace !== null && ["ready", "active", "idle"].includes(state.workspace.state);
  const summaries: Record<string, string> = {
    UNTRACKED: "No template task metadata.",
    PENDING: workspaceRunning ? "Template setup is queued and will start shortly." : "Start the workspace to begin template setup.",
    RUNNING: "Template setup is running…",
    FAILED: "Template setup failed.",
    READY: "Template setup is ready.",
  };
  byId("setup-state").textContent = setup.status;
  byId("setup-summary").textContent = setup.error || summaries[setup.status] || "Template setup status is unavailable.";
  byId<HTMLButtonElement>("setup-retry").hidden =
    !(setup.status === "FAILED" || setup.status === "PENDING") || !workspaceRunning;
  const reference = byId("setup-correlation");
  reference.hidden = !setup.correlationId;
  reference.textContent = setup.correlationId ? `SETUP REF ${setup.correlationId}` : "";
  reference.title = setup.correlationId || "";
}

async function retrySetup(): Promise<void> {
  const workspace = state.workspace;
  if (!workspace) return;
  await api<SetupStatus>(`/api/v1/workspaces/${workspace.workspaceId}/setup/retry`, { method: "POST" });
  await refreshTasks();
}

async function refreshGit(): Promise<void> {
  const workspace = state.workspace;
  if (!workspace) return;
  state.gitHasStaged = false;
  byId<HTMLButtonElement>("git-commit").disabled = true;
  const { body } = await api<GitStatus>(`/api/v1/workspaces/${workspace.workspaceId}/git`);
  if (state.workspace?.workspaceId !== workspace.workspaceId) return;

  const stateLabel = byId("git-state");
  const changes = byId("git-changes");
  const commitButton = byId<HTMLButtonElement>("git-commit");
  changes.replaceChildren();
  byId<HTMLPreElement>("git-diff").hidden = true;
  stateLabel.textContent = body.repository ? `${body.branch} · ${body.changes.length}` : "NO REPO";
  byId<HTMLFormElement>("git-commit-form").hidden = !body.repository;

  const staged = body.changes.some((change) => change.indexStatus !== " " && change.indexStatus !== "?");
  state.gitHasStaged = body.repository && staged;
  commitButton.disabled = !body.repository || !staged || !byId<HTMLTextAreaElement>("git-message").value.trim();
  if (!body.repository) {
    appendGitMessage("No Git repository at the workspace root. Use the terminal to initialize or open one.");
    return;
  }
  if (body.changes.length === 0) {
    appendGitMessage("Working tree clean.");
    return;
  }

  for (const change of body.changes) {
    const item = document.createElement("li");
    item.className = "git-change";
    const openDiff = document.createElement("button");
    openDiff.type = "button";
    openDiff.className = "git-path";
    openDiff.textContent = change.path;
    openDiff.title = change.path;
    openDiff.addEventListener("click", () =>
      loadGitDiff(change.path).catch((failure) => toast(errorText(failure), "error")),
    );
    const code = document.createElement("span");
    code.className = "git-code";
    code.textContent = `${change.indexStatus}${change.worktreeStatus}`;
    item.append(openDiff, code);

    if (change.indexStatus !== " " && change.indexStatus !== "?") {
      item.append(
        gitAction("Unstage", () =>
          updateGitPath("unstage", change.path).catch((failure) => toast(errorText(failure), "error")),
        ),
      );
    }
    if (change.worktreeStatus !== " " || change.indexStatus === "?") {
      item.append(
        gitAction("Stage", () =>
          updateGitPath("stage", change.path).catch((failure) => toast(errorText(failure), "error")),
        ),
      );
    }
    changes.append(item);
  }
}

function appendGitMessage(message: string): void {
  const item = document.createElement("li");
  item.className = "git-empty";
  item.textContent = message;
  byId("git-changes").append(item);
}

function refreshGitSafely(): void {
  refreshGit().catch((failure) => toast(errorText(failure), "error"));
}

function resetGitPanel(): void {
  state.gitHasStaged = false;
  byId<HTMLButtonElement>("git-commit").disabled = true;
  byId("git-state").textContent = "GIT";
  byId("git-changes").replaceChildren();
  byId<HTMLPreElement>("git-diff").hidden = true;
  byId<HTMLTextAreaElement>("git-message").value = "";
}

function gitAction(label: string, action: () => void): HTMLButtonElement {
  const buttonElement = document.createElement("button");
  buttonElement.type = "button";
  buttonElement.className = "git-action";
  buttonElement.textContent = label;
  buttonElement.addEventListener("click", action);
  return buttonElement;
}

async function loadGitDiff(path: string): Promise<void> {
  const workspace = state.workspace;
  if (!workspace) return;
  const query = new URLSearchParams({ path });
  const { body } = await api<GitDiff>(`/api/v1/workspaces/${workspace.workspaceId}/git/diff?${query}`);
  if (state.workspace?.workspaceId !== workspace.workspaceId) return;
  const diff = byId<HTMLPreElement>("git-diff");
  diff.hidden = false;
  diff.textContent = body.content || "No diff to display.";
}

async function updateGitPath(operation: "stage" | "unstage", path: string): Promise<void> {
  const workspace = state.workspace;
  if (!workspace) return;
  await api<GitStatus>(`/api/v1/workspaces/${workspace.workspaceId}/git/${operation}`, {
    method: "POST",
    body: JSON.stringify({ path }),
  });
  refreshGitSafely();
}

async function commitGitChanges(event: SubmitEvent): Promise<void> {
  event.preventDefault();
  const workspace = state.workspace;
  const message = byId<HTMLTextAreaElement>("git-message").value.trim();
  if (!workspace || !message) return;
  const { body } = await api<{ commit: string; summary: string }>(
    `/api/v1/workspaces/${workspace.workspaceId}/git/commit`,
    { method: "POST", body: JSON.stringify({ message }) },
  );
  byId<HTMLTextAreaElement>("git-message").value = "";
  toast(`Committed ${body.commit}.`);
  refreshGitSafely();
}

async function runTask(task: WorkspaceTask): Promise<void> {
  const workspace = state.workspace;
  if (!workspace) return;
  const { body: run } = await api<TaskRun>(`/api/v1/workspaces/${workspace.workspaceId}/tasks/${encodeURIComponent(task.id)}/run`, { method: "POST" });
  state.taskRunId = run.id;
  void refreshPortStatuses().catch(() => undefined);
  const output = byId<HTMLPreElement>("task-output");
  const stop = byId<HTMLButtonElement>("task-stop");
  stop.hidden = run.status !== "RUNNING";
  stop.onclick = () => { void stopTask(run.id); };
  const reference = byId("task-correlation");
  reference.hidden = false;
  reference.textContent = `TASK REF ${run.id}`;
  reference.title = run.id;
  output.hidden = false;
  output.textContent = `${run.label} · ${run.status}\n${run.output}`;
  let current = run;
  while (state.workspace?.workspaceId === workspace.workspaceId && state.taskRunId === run.id && current.status === "RUNNING") {
    await new Promise((resolve) => window.setTimeout(resolve, 1000));
    const response = await api<TaskRun>(`/api/v1/workspaces/${workspace.workspaceId}/task-runs/${run.id}`);
    current = response.body;
    stop.hidden = current.status !== "RUNNING";
    output.textContent = `${current.label} · ${current.status}${current.exitCode === null ? "" : ` · exit ${current.exitCode}`}\n${current.output}`;
    output.scrollTop = output.scrollHeight;
    await refreshPortStatuses().catch(() => undefined);
  }
}

async function stopTask(runId: string): Promise<void> {
  const workspace = state.workspace;
  if (!workspace || state.taskRunId !== runId) return;
  const stop = byId<HTMLButtonElement>("task-stop");
  stop.disabled = true;
  try {
    const { body: run } = await api<TaskRun>(
      `/api/v1/workspaces/${workspace.workspaceId}/task-runs/${runId}/stop`,
      { method: "POST" },
    );
    const output = byId<HTMLPreElement>("task-output");
    output.textContent = `${run.label} · ${run.status}${run.exitCode === null ? "" : ` · exit ${run.exitCode}`}\n${run.output}`;
    const reference = byId("task-correlation");
    reference.hidden = false;
    reference.textContent = `TASK REF ${run.id}`;
    reference.title = run.id;
  } finally {
    stop.disabled = false;
    await refreshPortStatuses().catch(() => undefined);
  }
}

function renderPreviewLinks(workspace: Workspace): void {
  const container = byId("preview-links");
  container.replaceChildren();
  byId<HTMLButtonElement>("refresh-preview-ports").hidden = workspace.ports.length === 0;
  for (const port of workspace.ports) {
    const link = document.createElement("a");
    link.className = "preview-chip";
    link.href = port.url;
    link.target = "_blank";
    link.rel = "noreferrer";
    link.textContent = `:${port.port} Preview ↗`;
    const status = document.createElement("span");
    status.className = "preview-port-status";
    status.dataset.listening = String(port.listening === true);
    status.textContent = port.listening === null ? "CHECKING" : port.listening ? "LISTENING" : "NOT LISTENING";
    container.append(link, status);
  }
}

async function refreshPortStatuses(): Promise<void> {
  const workspace = state.workspace;
  if (!workspace) return;
  const { body: ports } = await api<PortEntry[]>(
    `/api/v1/workspaces/${workspace.workspaceId}/ports`,
  );
  if (state.workspace?.workspaceId !== workspace.workspaceId) return;
  state.workspace = { ...state.workspace, ports };
  renderPreviewLinks(state.workspace);
}

async function refreshFiles(path = ".", parent: HTMLElement = byId("file-tree")): Promise<void> {
  const workspace = state.workspace;
  if (!workspace) return;
  const query = new URLSearchParams({ path });
  const { body: entries } = await api<FileEntry[]>(`/api/v1/workspaces/${workspace.workspaceId}/files?${query}`);
  if (parent === byId("file-tree")) parent.replaceChildren();
  for (const entry of entries) parent.append(renderFile(entry));
  if (parent === byId("file-tree") && entries.length === 0) {
    const empty = document.createElement("p");
    empty.className = "tree-empty";
    empty.textContent = "This workspace is empty.";
    parent.append(empty);
  }
}

async function searchWorkspace(query: string): Promise<void> {
  const workspace = state.workspace;
  if (!workspace) return;
  const params = new URLSearchParams({ q: query, limit: "50" });
  const { body } = await api<SearchResult[]>(`/api/v1/workspaces/${workspace.workspaceId}/files/search?${params}`);
  if (state.workspace?.workspaceId !== workspace.workspaceId
      || byId<HTMLInputElement>("workspace-search").value.trim() !== query) return;
  const results = byId("search-results");
  results.replaceChildren();
  for (const match of body) {
    const item = document.createElement("li");
    const open = document.createElement("button");
    open.type = "button";
    open.className = "search-result";
    const path = document.createElement("span");
    path.className = "search-result-path";
    path.textContent = `${match.path}${match.line ? `:${match.line}` : ""}`;
    const preview = document.createElement("small");
    preview.textContent = match.preview;
    open.append(path, preview);
    open.addEventListener("click", () => {
      selectAndOpenFile(match.path);
    });
    item.append(open);
    results.append(item);
  }
  if (body.length === 0) {
    const empty = document.createElement("li");
    empty.className = "search-empty";
    empty.textContent = "No matching files.";
    results.append(empty);
  }
  results.hidden = false;
}

function scheduleWorkspaceSearch(runImmediately = false): void {
  if (state.searchTimer !== null) window.clearTimeout(state.searchTimer);
  const input = byId<HTMLInputElement>("workspace-search");
  const query = input.value.trim();
  const results = byId("search-results");
  if (query.length < 2) {
    results.replaceChildren();
    results.hidden = true;
    return;
  }
  const workspaceId = state.workspace?.workspaceId;
  state.searchTimer = window.setTimeout(
    () => searchWorkspace(query).catch((failure) => {
      if (state.workspace?.workspaceId === workspaceId
          && byId<HTMLInputElement>("workspace-search").value.trim() === query) {
        toast(errorText(failure), "error");
      }
    }),
    runImmediately ? 0 : 250,
  );
}

function clearWorkspaceSearch(): void {
  if (state.searchTimer !== null) window.clearTimeout(state.searchTimer);
  state.searchTimer = null;
  byId<HTMLInputElement>("workspace-search").value = "";
  const results = byId("search-results");
  results.replaceChildren();
  results.hidden = true;
  byId<HTMLButtonElement>("download-selected").disabled = true;
}

function selectAndOpenFile(path: string): void {
  state.selectedPath = path;
  byId<HTMLButtonElement>("download-selected").disabled = false;
  openDocument(path).catch((failure) => {
    const message = errorText(failure);
    if (message.includes("binary files cannot be opened") || message.includes("too large to open")) {
      toast("This file cannot be edited as text. Use Download selected to download it.", "error");
      return;
    }
    toast(message, "error");
  });
}

function downloadSelected(): void {
  const workspace = state.workspace;
  const path = state.selectedPath;
  if (!workspace || !path) return;
  const query = new URLSearchParams({ path });
  const anchor = document.createElement("a");
  anchor.href = `/api/v1/workspaces/${workspace.workspaceId}/files/download?${query}`;
  anchor.download = "";
  anchor.rel = "noreferrer";
  document.body.append(anchor);
  anchor.click();
  anchor.remove();
}

function renderFile(entry: FileEntry): HTMLLIElement {
  const item = document.createElement("li");
  item.className = entry.directory ? "tree-directory" : "tree-file";
  const row = document.createElement("div");
  row.className = "tree-row";
  const toggle = document.createElement("button");
  toggle.type = "button";
  toggle.className = "tree-item";
  toggle.textContent = `${entry.directory ? "▸" : "·"} ${entry.name}`;
  toggle.title = entry.path;
  if (entry.directory) {
    toggle.addEventListener("click", async () => {
      const existing = item.querySelector(":scope > ul");
      if (existing) {
        existing.remove();
        toggle.textContent = `▸ ${entry.name}`;
        return;
      }
      const children = document.createElement("ul");
      toggle.textContent = `▾ ${entry.name}`;
      item.append(children);
      try { await refreshFiles(entry.path, children); }
      catch (failure) { toast(errorText(failure), "error"); }
    });
  } else {
    toggle.addEventListener("click", () => {
      selectAndOpenFile(entry.path);
    });
  }
  row.append(toggle);
  item.append(row);
  return item;
}

async function languageFor(path: string) {
  const lower = path.toLowerCase();
  if (lower.endsWith(".html") || lower.endsWith(".htm")) return (await import("@codemirror/lang-html")).html();
  if (lower.endsWith(".json") || lower.endsWith(".jsonc")) return (await import("@codemirror/lang-json")).json();
  if (lower.endsWith(".py")) return (await import("@codemirror/lang-python")).python();
  if (/\.(js|jsx|ts|tsx|mjs|cjs)$/.test(lower)) {
    return (await import("@codemirror/lang-javascript")).javascript({ typescript: /\.(ts|tsx)$/.test(lower) });
  }
  return [];
}

async function openDocument(path: string): Promise<void> {
  const existing = state.openDocuments.get(path);
  if (existing) { activateDocument(path); return; }
  const workspace = state.workspace;
  if (!workspace) return;
  const query = new URLSearchParams({ path });
  const { body, response } = await api<DocumentBody>(`/api/v1/workspaces/${workspace.workspaceId}/files/document?${query}`);
  const wrapper = document.createElement("div");
  wrapper.className = "editor-instance";
  const documentState = {} as OpenDocument;
  const [editor, language] = await Promise.all([import("codemirror"), languageFor(path)]);
  const { EditorView } = editor;
  const view = new EditorView({
    doc: body.content,
    parent: wrapper,
    extensions: [
      editor.basicSetup,
      language,
      EditorView.updateListener.of((update) => {
        if (!update.docChanged) return;
        documentState.dirty = update.state.doc.toString() !== documentState.savedContent;
        state.languageServices?.changeDocument(path, update.state.doc.toString());
        renderTabs();
        updateSaveState();
      }),
    ],
  });
  wrapper.hidden = true;
  byId("editor-surface").append(wrapper);
  Object.assign(documentState, {
    path,
    version: response.headers.get("ETag") || body.version,
    savedContent: body.content,
    wrapper,
    view,
    dirty: false,
  });
  state.openDocuments.set(path, documentState);
  state.selectedPath = path;
  activateDocument(path);
}

function activateDocument(path: string): void {
  const next = state.openDocuments.get(path);
  if (!next) return;
  state.openDocuments.forEach((opened) => { opened.wrapper.hidden = opened.path !== path; });
  state.activePath = path;
  byId("active-file").textContent = path;
  byId("editor-empty").hidden = true;
  byId("status-language").textContent = languageName(path);
  if (state.workspace && !["ready", "active", "idle"].includes(state.workspace.state)) {
    state.languageServices?.closeDocument(path);
    byId("language-state").textContent = "Start the workspace to use language services";
  } else {
    state.languageServices?.openDocument(languageIdForPath(path), path, next.view.state.doc.toString());
  }
  renderTabs();
  updateSaveState();
  window.requestAnimationFrame(() => next.view.requestMeasure());
}

function languageName(path: string): string {
  const extension = path.split(".").at(-1)?.toUpperCase();
  return extension && extension !== path.toUpperCase() ? extension : "PLAIN TEXT";
}

function renderTabs(): void {
  const tabs = byId("editor-tabs");
  tabs.replaceChildren();
  for (const opened of state.openDocuments.values()) {
    const tab = document.createElement("button");
    tab.type = "button";
    tab.className = `editor-tab${opened.path === state.activePath ? " active" : ""}`;
    tab.textContent = `${opened.dirty ? "● " : ""}${opened.path.split("/").at(-1) || opened.path}`;
    tab.title = opened.path;
    tab.addEventListener("click", () => activateDocument(opened.path));
    const close = document.createElement("span");
    close.className = "tab-close";
    close.textContent = "×";
    close.addEventListener("click", (event) => {
      event.stopPropagation();
      if (opened.dirty && !window.confirm(`Discard unsaved changes to ${opened.path}?`)) return;
      opened.view.destroy();
      opened.wrapper.remove();
      state.openDocuments.delete(opened.path);
      if (state.activePath === opened.path) {
        state.activePath = null;
        const replacement = state.openDocuments.keys().next().value as string | undefined;
        if (replacement) activateDocument(replacement);
        else {
          state.languageServices?.closeDocument(opened.path);
          byId("active-file").textContent = "No file selected";
          byId("editor-empty").hidden = false;
          renderTabs();
        }
      } else renderTabs();
    });
    tab.append(close);
    tabs.append(tab);
  }
}

function updateSaveState(): void {
  const current = state.activePath ? state.openDocuments.get(state.activePath) : undefined;
  byId<HTMLButtonElement>("save-file").disabled = !current || !current.dirty;
  byId("save-state").textContent = current?.dirty ? "Unsaved changes" : current ? "Saved" : "";
  byId("save-state").className = current?.dirty ? "save-state dirty" : "save-state";
}

async function saveActiveDocument(): Promise<void> {
  const workspace = state.workspace;
  const path = state.activePath;
  const opened = path ? state.openDocuments.get(path) : undefined;
  if (!workspace || !path || !opened || !opened.dirty) return;
  try {
    const query = new URLSearchParams({ path });
    const { body, response } = await api<DocumentBody>(
      `/api/v1/workspaces/${workspace.workspaceId}/files/document?${query}`,
      { method: "PUT", headers: { "If-Match": opened.version }, body: JSON.stringify({ content: opened.view.state.doc.toString() }) },
    );
    opened.version = response.headers.get("ETag") || body.version;
    opened.savedContent = body.content;
    opened.dirty = false;
    state.languageServices?.saveDocument(path);
    renderTabs();
    updateSaveState();
    toast("Saved.");
    refreshGitSafely();
  } catch (failure) {
    if (failure instanceof Error && failure.message.startsWith("412")) {
      byId("save-state").textContent = "Changed on disk — reload before saving";
      byId("save-state").className = "save-state conflict";
      toast("This file changed outside the editor. Copy your edits before reloading.", "error");
      return;
    }
    throw failure;
  }
}

async function createFile(): Promise<void> {
  const workspace = state.workspace;
  if (!workspace) return;
  const path = window.prompt("New file path relative to the workspace root");
  if (!path?.trim()) return;
  const query = new URLSearchParams({ path: path.trim() });
  await api<DocumentBody>(`/api/v1/workspaces/${workspace.workspaceId}/files/document?${query}`, {
    method: "PUT", headers: { "If-None-Match": "*" }, body: JSON.stringify({ content: "" }),
  });
  await refreshFiles();
  refreshGitSafely();
  await openDocument(path.trim());
}

async function createFolder(): Promise<void> {
  const workspace = state.workspace;
  if (!workspace) return;
  const path = window.prompt("New folder path relative to the workspace root");
  if (!path?.trim()) return;
  await api(`/api/v1/workspaces/${workspace.workspaceId}/files/directory`, {
    method: "POST", body: JSON.stringify({ path: path.trim() }),
  });
  await refreshFiles();
  refreshGitSafely();
}

async function renameSelected(): Promise<void> {
  const workspace = state.workspace;
  if (!workspace || !state.selectedPath) { toast("Select a file or folder first.", "error"); return; }
  const newPath = window.prompt("New path relative to the workspace root", state.selectedPath);
  if (!newPath?.trim() || newPath === state.selectedPath) return;
  await api(`/api/v1/workspaces/${workspace.workspaceId}/files/rename`, {
    method: "POST", body: JSON.stringify({ path: state.selectedPath, newPath: newPath.trim() }),
  });
  const previousPath = state.selectedPath;
  const renamedPath = newPath.trim().replaceAll("\\", "/");
  const affected = [...state.openDocuments.entries()]
    .filter(([path]) => path === previousPath || path.startsWith(`${previousPath}/`));
  for (const [oldPath, opened] of affected) {
    const updatedPath = `${renamedPath}${oldPath.substring(previousPath.length)}`;
    state.openDocuments.delete(oldPath);
    opened.path = updatedPath;
    state.openDocuments.set(updatedPath, opened);
    if (state.activePath === oldPath) state.activePath = updatedPath;
  }
  state.selectedPath = renamedPath;
  await refreshFiles();
  refreshGitSafely();
  renderTabs();
  if (state.activePath) byId("active-file").textContent = state.activePath;
}

async function deleteSelected(): Promise<void> {
  const workspace = state.workspace;
  const path = state.selectedPath;
  if (!workspace || !path) { toast("Select a file or empty folder first.", "error"); return; }
  if (!window.confirm(`Delete ${path}? Folders must be empty.`)) return;
  await api(`/api/v1/workspaces/${workspace.workspaceId}/files?${new URLSearchParams({ path })}`, { method: "DELETE" });
  const opened = state.openDocuments.get(path);
  if (opened) {
    state.languageServices?.closeDocument(path);
    opened.view.destroy();
    opened.wrapper.remove();
    state.openDocuments.delete(path);
    if (state.activePath === path) state.activePath = null;
  }
  state.selectedPath = null;
  await refreshFiles();
  refreshGitSafely();
  renderTabs();
  if (!state.activePath) byId("editor-empty").hidden = false;
}

async function toggleWorkspace(): Promise<void> {
  const workspace = state.workspace;
  if (!workspace) return;
  const running = ["ready", "active", "idle"].includes(workspace.state);
  const action = running ? "stop" : "start";
  const { body } = await api<Workspace>(`/api/v1/workspaces/${workspace.workspaceId}/${action}`, { method: "POST" });
  state.workspace = body;
  byId("workbench-state").textContent = body.state;
  byId("workbench-state").className = `status status-${body.state}`;
  byId("runtime-toggle").textContent = running ? "Start workspace" : "Stop workspace";
  renderPreviewLinks(body);
  void refreshPortStatuses().catch(() => undefined);
  if (running) state.languageServices?.close();
  await refreshTasks();
}

function closeTerminal(closeServerSession: boolean, refreshSessions = false): Promise<void> {
  state.terminalObserver?.disconnect();
  state.terminalObserver = null;
  state.socket?.close();
  state.socket = null;
  state.terminal?.dispose();
  state.terminal = null;
  state.fitAddon = null;
  byId<HTMLButtonElement>("close-terminal").hidden = true;
  byId<HTMLSelectElement>("terminal-session-select").hidden = true;
  byId<HTMLButtonElement>("terminal-reconnect").hidden = true;
  const sessionId = state.terminalSessionId;
  state.terminalSessionId = null;
  byId("terminal-surface").replaceChildren();
  if (closeServerSession && sessionId) {
    return api(`/api/v1/terminals/${sessionId}`, { method: "DELETE" })
      .then(() => refreshSessions ? refreshTerminalSessions() : undefined);
  }
  return Promise.resolve();
}

async function openTerminal(): Promise<void> {
  const workspace = state.workspace;
  if (!workspace) return;
  await closeTerminal(true);
  const { body: session } = await api<TerminalSession>(
    `/api/v1/workspaces/${workspace.workspaceId}/terminals?rows=28&cols=100`, { method: "POST" },
  );
  if (state.workspace?.workspaceId !== workspace.workspaceId) {
    await api(`/api/v1/terminals/${session.sessionId}`, { method: "DELETE" }).catch(() => undefined);
    return;
  }
  await connectTerminal(session, workspace.workspaceId);
}

async function reconnectTerminal(): Promise<void> {
  const workspace = state.workspace;
  const sessionId = byId<HTMLSelectElement>("terminal-session-select").value;
  const session = state.terminalSessions.find((candidate) => candidate.sessionId === sessionId);
  if (!workspace || !session) return;
  await closeTerminal(false);
  await connectTerminal(session, workspace.workspaceId);
}

async function connectTerminal(session: TerminalSession, workspaceId: string): Promise<void> {
  if (state.workspace?.workspaceId !== workspaceId || session.workspaceId !== workspaceId) return;
  state.terminalSessionId = session.sessionId;
  let Terminal: typeof import("@xterm/xterm").Terminal;
  let FitAddon: typeof import("@xterm/addon-fit").FitAddon;
  try {
    [{ Terminal }, { FitAddon }] = await Promise.all([
      import("@xterm/xterm"),
      import("@xterm/addon-fit"),
    ]);
  } catch (failure) {
    await closeTerminal(true).catch(() => undefined);
    throw failure;
  }
  if (state.workspace?.workspaceId !== workspaceId || state.terminalSessionId !== session.sessionId) return;
  const terminal = new Terminal({
    cursorBlink: true,
    fontFamily: '"IBM Plex Mono", "Cascadia Code", monospace',
    fontSize: 13,
    theme: { background: "#101410", foreground: "#e2e9dc", cursor: "#b6d797", selectionBackground: "#526543" },
    convertEol: false,
  });
  const fit = new FitAddon();
  terminal.loadAddon(fit);
  terminal.open(byId("terminal-surface"));
  state.terminal = terminal;
  state.fitAddon = fit;
  byId<HTMLButtonElement>("close-terminal").hidden = false;
  byId("terminal-state").textContent = "Connecting…";
  const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
  const socket = new WebSocket(`${protocol}//${window.location.host}/api/v1/terminals/${session.sessionId}/stream`);
  state.socket = socket;
  socket.addEventListener("open", () => {
    if (state.socket !== socket || state.terminalSessionId !== session.sessionId) {
      socket.close();
      return;
    }
    fit.fit();
    socket.send(JSON.stringify({ type: "resize", rows: terminal.rows, cols: terminal.cols } satisfies StreamMessage));
    terminal.focus();
    byId("terminal-state").textContent = "Connected";
  });
  socket.addEventListener("message", (event) => {
    try {
      const message = JSON.parse(String(event.data)) as StreamMessage;
      if (message.type === "output" && message.data) terminal.write(message.data);
      if (message.type === "exit") byId("terminal-state").textContent = `Exited (${message.exitCode ?? "unknown"})`;
    } catch { terminal.write("\r\n[terminal] invalid server message\r\n"); }
  });
  socket.addEventListener("close", () => {
    if (state.socket === socket) byId("terminal-state").textContent = "Disconnected";
  });
  socket.addEventListener("error", () => {
    if (state.socket === socket) byId("terminal-state").textContent = "Connection error";
  });
  terminal.onData((data) => {
    if (socket.readyState === WebSocket.OPEN) socket.send(JSON.stringify({ type: "input", data } satisfies StreamMessage));
  });
  terminal.onResize(({ rows, cols }) => {
    if (socket.readyState === WebSocket.OPEN) socket.send(JSON.stringify({ type: "resize", rows, cols } satisfies StreamMessage));
  });
  const observer = new ResizeObserver(() => { try { fit.fit(); } catch { /* Collapsed terminal panel. */ } });
  observer.observe(byId("terminal-surface"));
  state.terminalObserver = observer;
  await refreshTerminalSessions();
}

function backToWorkspaces(navigate = true): void {
  if ([...state.openDocuments.values()].some((opened) => opened.dirty)
      && !window.confirm("Some files have unsaved changes. Leave the workspace?")) return;
  void closeTerminal(false);
  clearWorkspaceSearch();
  resetGitPanel();
  state.openDocuments.forEach((opened) => opened.view.destroy());
  state.openDocuments.clear();
  state.activePath = null;
  state.selectedPath = null;
  state.workspace = null;
  state.languageServices?.close();
  state.languageServices = null;
  state.languagePacks = [];
  byId("language-pack-list").replaceChildren();
  byId("language-diagnostics").replaceChildren();
  byId("language-state").textContent = "OPTIONAL";
  if (navigate) history.pushState({}, "", "/");
  setPage("home");
  renderWorkspaces().catch((failure) => toast(errorText(failure), "error"));
}

function wireUi(): void {
  byId("workspace-form").addEventListener("submit", (event) => createWorkspace(event as SubmitEvent).catch((failure) => toast(errorText(failure), "error")));
  byId("new-file").addEventListener("click", () => createFile().catch((failure) => toast(errorText(failure), "error")));
  byId("new-folder").addEventListener("click", () => createFolder().catch((failure) => toast(errorText(failure), "error")));
  byId("rename-selected").addEventListener("click", () => renameSelected().catch((failure) => toast(errorText(failure), "error")));
  byId("delete-selected").addEventListener("click", () => deleteSelected().catch((failure) => toast(errorText(failure), "error")));
  byId("download-selected").addEventListener("click", downloadSelected);
  byId<HTMLInputElement>("workspace-search").addEventListener("input", () => scheduleWorkspaceSearch());
  byId<HTMLInputElement>("workspace-search").addEventListener("keydown", (event) => {
    const keyboard = event as KeyboardEvent;
    if (keyboard.key === "Enter") { keyboard.preventDefault(); scheduleWorkspaceSearch(true); }
    if (keyboard.key === "Escape") {
      byId<HTMLInputElement>("workspace-search").value = "";
      scheduleWorkspaceSearch();
    }
  });
  byId("save-file").addEventListener("click", () => saveActiveDocument().catch((failure) => toast(errorText(failure), "error")));
  byId("runtime-toggle").addEventListener("click", () => toggleWorkspace().catch((failure) => toast(errorText(failure), "error")));
  byId("refresh-preview-ports").addEventListener("click", () =>
    refreshPortStatuses().catch((failure) => toast(errorText(failure), "error")),
  );
  byId("back-home").addEventListener("click", () => backToWorkspaces());
  byId("terminal-new").addEventListener("click", () => openTerminal().catch((failure) => toast(errorText(failure), "error")));
  byId("terminal-reconnect").addEventListener("click", () => reconnectTerminal().catch((failure) => toast(errorText(failure), "error")));
  byId("close-terminal").addEventListener("click", () =>
    void closeTerminal(true, true).catch((failure) => toast(errorText(failure), "error")),
  );
  byId("setup-retry").addEventListener("click", () => retrySetup().catch((failure) => toast(errorText(failure), "error")));
  byId("git-refresh").addEventListener("click", refreshGitSafely);
  byId<HTMLFormElement>("git-commit-form").addEventListener("submit", (event) =>
    commitGitChanges(event as SubmitEvent).catch((failure) => toast(errorText(failure), "error")),
  );
  byId<HTMLTextAreaElement>("git-message").addEventListener("input", () => {
    byId<HTMLButtonElement>("git-commit").disabled =
      !state.gitHasStaged || !byId<HTMLTextAreaElement>("git-message").value.trim();
  });
  document.addEventListener("keydown", (event) => {
    const keyboard = event as KeyboardEvent;
    if ((keyboard.ctrlKey || keyboard.metaKey) && keyboard.key.toLowerCase() === "p") {
      keyboard.preventDefault();
      byId<HTMLInputElement>("workspace-search").focus();
    }
  });
}

function restoreWorkspaceFromLocation(): void {
  const match = /^#workspace\/([^/]+)$/.exec(window.location.hash);
  if (!match) {
    if (state.workspace) backToWorkspaces(false);
    return;
  }
  const workspaceId = match[1];
  if (state.workspace?.workspaceId === workspaceId) return;
  void api<Workspace>(`/api/v1/workspaces/${encodeURIComponent(workspaceId)}`)
    .then(({ body }) => {
      if (window.location.hash === `#workspace/${workspaceId}`) {
        return openWorkspace(body, false);
      }
    })
    .catch((failure) => toast(errorText(failure), "error"));
}

async function main(): Promise<void> {
  wireUi();
  await loadIdentity();
  if (state.user) {
    await Promise.all([loadTemplates(), renderWorkspaces()]);
    restoreWorkspaceFromLocation();
    window.setInterval(() => {
      if (state.workspace === null) renderWorkspaces().catch(() => undefined);
    }, 5000);
  }
  window.addEventListener("popstate", restoreWorkspaceFromLocation);
  window.addEventListener("hashchange", restoreWorkspaceFromLocation);
}

main().catch((failure: unknown) => {
  toast(errorText(failure), "error");
  byId("loading-state").textContent = "Unable to connect to this Jahaziel server.";
});
