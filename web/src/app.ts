///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import type { EditorView } from "codemirror";
import { parseDebugLaunchConfigurations, type DebugLaunchConfiguration } from "./debug-launch-config";
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
interface AgentToolStatus {
  id: string;
  name: string;
  version: string;
  architecture: string;
  status: string;
  downloadSizeBytes: number;
  installedSizeBytes: number;
  progressPercent: number;
  error: string;
}
interface WorkspaceTask { id: string; label: string; port: number | null; }
interface SetupStatus { status: string; output: string; error: string; updatedAt: string; correlationId?: string | null; }
interface TaskRun { id: string; workspaceId: string; taskId: string; label: string; status: string; output: string; exitCode: number | null; }
interface GitChange { path: string; indexStatus: string; worktreeStatus: string; }
interface GitStatus { repository: boolean; branch: string; changes: GitChange[]; }
interface GitDiff { path: string; content: string; }
interface DebugSession { id: string; workspaceId: string; languageId: string; adapterId: string; state: string; adapterVersion: string; startedAt: string; error: string; }
interface DapMessage {
  type: string;
  seq?: number;
  request_seq?: number;
  command?: string;
  event?: string;
  success?: boolean;
  message?: string;
  arguments?: Record<string, unknown>;
  body?: Record<string, unknown>;
}
interface DapFrame {
  id: number;
  name: string;
  line?: number;
  column?: number;
  source?: { name?: string; path?: string };
}
interface DapVariable { name: string; value: string; type?: string; variablesReference?: number; }
interface DapPending { resolve: (message: DapMessage) => void; reject: (failure: Error) => void; timer: number; }
interface DapEventWaiter { resolve: () => void; reject: (failure: Error) => void; timer: number; }
interface StreamMessage { type: "input" | "history" | "output" | "resize" | "exit"; data?: string; rows?: number; cols?: number; exitCode?: number; }
interface OpenDocument {
  path: string;
  version: string;
  savedContent: string;
  wrapper: HTMLDivElement;
  view: EditorView;
  dirty: boolean;
  recoveredDraft: boolean;
  conflictRemoteVersion: string | null;
  conflictRemoteContent: string | null;
}
interface LocalDraft { content: string; updatedAt: number; }

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
  agentToolStatus: null as AgentToolStatus | null,
  agentToolPolling: false,
  socket: null as WebSocket | null,
  taskRunId: null as string | null,
  taskPollingId: null as string | null,
  setupPolling: false,
  searchTimer: null as number | null,
  gitHasStaged: false,
  languageServices: null as LanguageServices | null,
  languagePacks: [] as LanguagePack[],
  activePanel: "files" as "files" | "editor" | "run" | "languages" | "debug" | "git" | "terminal" | "preview",
  debugSession: null as DebugSession | null,
  debugInitialized: false,
  debugLaunchConfigurations: [] as DebugLaunchConfiguration[],
  debugSocket: null as WebSocket | null,
  debugSequence: 0,
  dapPending: new Map<number, DapPending>(),
  dapEventWaiters: new Map<string, DapEventWaiter[]>(),
  dapEarlyEvents: new Set<string>(),
  debugThreadId: null as number | null,
  debugFrameId: null as number | null,
  debugPaused: false,
  debugFrames: [] as DapFrame[],
  debugVariables: [] as DapVariable[],
  debugWatches: [] as string[],
  debugWatchValues: new Map<string, string>(),
  debugBreakpoints: new Map<string, number[]>(),
  previewPort: null as number | null,
  previewUrl: null as string | null,
  terminalModifiers: { ctrl: false, alt: false },
  draftTimers: new Map<string, number>(),
  pendingDrafts: new Map<string, string>(),
  draftStorageWarningShown: false,
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

function localDraftKey(workspaceId: string, path: string): string | null {
  if (!state.user) return null;
  return `jahaziel:draft:${encodeURIComponent(state.user.email)}:${workspaceId}:${encodeURIComponent(path)}`;
}

function readLocalDraft(workspaceId: string, path: string): LocalDraft | null {
  const key = localDraftKey(workspaceId, path);
  if (!key) return null;
  try {
    const value = localStorage.getItem(key);
    if (!value) return null;
    const draft = JSON.parse(value) as Partial<LocalDraft>;
    if (typeof draft.content !== "string" || typeof draft.updatedAt !== "number") {
      localStorage.removeItem(key);
      return null;
    }
    return { content: draft.content, updatedAt: draft.updatedAt };
  } catch {
    return null;
  }
}

function persistLocalDraft(key: string): void {
  const content = state.pendingDrafts.get(key);
  if (content === undefined) return;
  state.draftTimers.delete(key);
  state.pendingDrafts.delete(key);
  try {
    localStorage.setItem(key, JSON.stringify({ content, updatedAt: Date.now() } satisfies LocalDraft));
  } catch {
    if (!state.draftStorageWarningShown) {
      state.draftStorageWarningShown = true;
      toast("Browser recovery storage is full or unavailable. Save this file to the server.", "error");
    }
  }
}

function scheduleLocalDraft(workspaceId: string, path: string, content: string): void {
  const key = localDraftKey(workspaceId, path);
  if (!key) return;
  const existing = state.draftTimers.get(key);
  if (existing !== undefined) window.clearTimeout(existing);
  state.pendingDrafts.set(key, content);
  state.draftTimers.set(key, window.setTimeout(() => persistLocalDraft(key), 250));
}

function clearLocalDraft(workspaceId: string, path: string): void {
  const key = localDraftKey(workspaceId, path);
  if (!key) return;
  const timer = state.draftTimers.get(key);
  if (timer !== undefined) window.clearTimeout(timer);
  state.draftTimers.delete(key);
  state.pendingDrafts.delete(key);
  try { localStorage.removeItem(key); } catch { /* Browser storage can be disabled. */ }
}

function flushLocalDrafts(): void {
  for (const [key, timer] of state.draftTimers) {
    window.clearTimeout(timer);
    persistLocalDraft(key);
  }
}

function setPage(page: "home" | "workbench"): void {
  homePage.hidden = page !== "home";
  workbenchPage.hidden = page !== "workbench";
  app.dataset.page = page;
}

function setPanel(panel: typeof state.activePanel): void {
  state.activePanel = panel;
  workbenchPage.dataset.activePanel = panel;
  document.querySelectorAll<HTMLButtonElement>(".workbench-panel-nav [data-panel]").forEach((item) => {
    item.setAttribute("aria-pressed", String(item.dataset.panel === panel));
  });
  if (panel === "preview") {
    if (state.previewPort === null) {
      state.previewPort = state.workspace?.ports.find((port) => port.listening === true)?.port ?? null;
    }
    renderPreviewPanel();
  }
  if (panel === "terminal") window.requestAnimationFrame(() => {
    try { state.fitAddon?.fit(); } catch { /* Panel has no measurable size yet. */ }
  });
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
    void refreshDebugSessions().catch((failure) => toast(errorText(failure), "error"));
    await refreshFiles();
    await refreshTasks();
    await refreshLanguagePacks();
    void refreshAgentToolStatus(true);
    await refreshTerminalSessions();
    return;
  }
  if ([...state.openDocuments.values()].some((opened) => opened.dirty)
      && state.workspace && state.workspace.workspaceId !== workspace.workspaceId
      && !window.confirm("Some files have unsaved changes. Leave this workspace?")) return;
  if (state.workspace?.workspaceId !== workspace.workspaceId) {
    flushLocalDrafts();
    await stopDebugSession(false);
  }
  state.workspace = workspace;
  clearDebugLaunchConfigurations();
  state.previewPort = null;
  state.previewUrl = null;
  state.languageServices?.close();
  state.languageServices = new LanguageServices(
    workspace.workspaceId,
    (status) => { byId("language-state").textContent = status; },
    renderLanguageDiagnostics,
  );
  state.languagePacks = [];
  state.debugBreakpoints.clear();
  state.debugWatches = [];
  state.debugWatchValues.clear();
  byId<HTMLInputElement>("debug-breakpoint-path").value = "";
  byId<HTMLInputElement>("debug-breakpoint-lines").value = "";
  byId("debug-stack").replaceChildren();
  byId("debug-variables").replaceChildren();
  byId("debug-watches").replaceChildren();
  byId("debug-breakpoint-list").replaceChildren();
  byId<HTMLPreElement>("debug-console").textContent = "Debugger output will appear here.";
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
  setPanel("files");
  refreshGitSafely();
  void refreshDebugSessions().catch((failure) => toast(errorText(failure), "error"));
  await refreshFiles();
  await refreshTasks();
  await refreshLanguagePacks();
  void refreshAgentToolStatus(true);
  await refreshTerminalSessions();
  renderTabs();
}

async function refreshAgentToolStatus(installWhenMissing: boolean): Promise<void> {
  if (state.agentToolPolling) return;
  state.agentToolPolling = true;
  try {
    let status = await readAgentToolStatus();
    renderAgentToolStatus(status);
    if (installWhenMissing && status?.status === "NOT_INSTALLED") {
      const { body } = await api<AgentToolStatus>("/api/v1/me/tools/opencode/install", { method: "POST" });
      status = body;
      renderAgentToolStatus(status);
    }
    while (status?.status === "INSTALLING") {
      await new Promise((resolve) => window.setTimeout(resolve, 1800));
      status = await readAgentToolStatus();
      renderAgentToolStatus(status);
    }
  } catch (failure) {
    byId("opencode-status").textContent = `OpenCode · ${errorText(failure)}`;
  } finally {
    state.agentToolPolling = false;
  }
}

async function readAgentToolStatus(): Promise<AgentToolStatus | null> {
  const { body } = await api<AgentToolStatus[]>("/api/v1/me/tools");
  state.agentToolStatus = body.find((tool) => tool.id === "opencode") ?? null;
  return state.agentToolStatus;
}

function renderAgentToolStatus(status: AgentToolStatus | null): void {
  const label = byId("opencode-status");
  const launch = byId<HTMLButtonElement>("terminal-opencode");
  const update = byId<HTMLButtonElement>("opencode-update");
  const remove = byId<HTMLButtonElement>("opencode-remove");
  if (!status) {
    label.textContent = "OpenCode · unavailable";
    launch.textContent = "Open OpenCode";
    launch.disabled = true;
    update.hidden = true;
    remove.hidden = true;
    return;
  }
  const installed = status.status === "INSTALLED";
  const installing = status.status === "INSTALLING";
  label.textContent = installing
    ? `OpenCode · installing ${status.progressPercent}%`
    : installed
      ? `OpenCode ${status.version} · ready`
      : status.status === "FAILED"
        ? `OpenCode · install failed: ${status.error}`
        : status.status === "UNSUPPORTED"
          ? `OpenCode · unsupported ${status.architecture}`
          : `OpenCode · ${Math.ceil(status.downloadSizeBytes / 1_000_000)} MB install`;
  launch.textContent = installed ? "Open OpenCode" : status.status === "FAILED" ? "Retry install" : "Installing";
  launch.disabled = installing || status.status === "UNSUPPORTED";
  update.hidden = !installed;
  update.disabled = installing;
  remove.hidden = !installed;
  remove.disabled = installing;
}

async function launchOpenCode(): Promise<void> {
  const workspace = state.workspace;
  if (!workspace) return;
  if (state.agentToolStatus?.status !== "INSTALLED") {
    if (state.agentToolStatus?.status !== "INSTALLING") {
      await refreshAgentToolStatus(false);
      if (state.agentToolStatus?.status === "NOT_INSTALLED" || state.agentToolStatus?.status === "FAILED") {
        const { body } = await api<AgentToolStatus>("/api/v1/me/tools/opencode/install", { method: "POST" });
        state.agentToolStatus = body;
        renderAgentToolStatus(body);
        void refreshAgentToolStatus(false);
      }
    }
    toast("OpenCode is not ready yet. Its install status is shown in the terminal panel.", "error");
    return;
  }
  const { body: session } = await api<TerminalSession>(
    `/api/v1/workspaces/${workspace.workspaceId}/terminals/opencode?rows=28&cols=100`,
    { method: "POST" },
  );
  if (state.workspace?.workspaceId !== workspace.workspaceId) {
    await api(`/api/v1/terminals/${session.sessionId}`, { method: "DELETE" }).catch(() => undefined);
    return;
  }
  await connectTerminal(session, workspace.workspaceId);
}

async function updateOpenCode(): Promise<void> {
  const { body } = await api<AgentToolStatus>("/api/v1/me/tools/opencode/update", { method: "POST" });
  state.agentToolStatus = body;
  renderAgentToolStatus(body);
  void refreshAgentToolStatus(false);
}

async function removeOpenCode(): Promise<void> {
  if (!window.confirm("Remove the per-user OpenCode executable? Its profile and provider sign-in will be kept.")) return;
  const { body } = await api<AgentToolStatus>("/api/v1/me/tools/opencode", { method: "DELETE" });
  state.agentToolStatus = body;
  renderAgentToolStatus(body);
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
  const [setupResult, tasksResult, latestRunResult] = await Promise.all([
    api<SetupStatus>(`/api/v1/workspaces/${workspace.workspaceId}/setup`),
    api<WorkspaceTask[]>(`/api/v1/workspaces/${workspace.workspaceId}/tasks`),
    api<TaskRun>(`/api/v1/workspaces/${workspace.workspaceId}/task-runs/latest`),
  ]);
  if (state.workspace?.workspaceId !== workspace.workspaceId) return;
  const setup = setupResult.body;
  renderSetupStatus(setup);
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
    empty.textContent = "No runnable tasks found in this workspace.";
    list.append(empty);
  }
  const latestRun = latestRunResult.body;
  renderTaskRun(latestRun);
  if (latestRun?.status === "RUNNING") void pollTaskRun(workspace.workspaceId, latestRun);
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
  renderTaskRun(run);
  void refreshPortStatuses().catch(() => undefined);
  await pollTaskRun(workspace.workspaceId, run);
}

function renderTaskRun(run: TaskRun | null): void {
  const output = byId<HTMLPreElement>("task-output");
  const stop = byId<HTMLButtonElement>("task-stop");
  const reference = byId("task-correlation");
  if (!run) {
    state.taskRunId = null;
    stop.hidden = true;
    stop.disabled = false;
    reference.hidden = true;
    output.hidden = true;
    output.textContent = "";
    return;
  }
  state.taskRunId = run.id;
  stop.hidden = run.status !== "RUNNING";
  stop.disabled = false;
  stop.onclick = () => { void stopTask(run.id); };
  reference.hidden = false;
  reference.textContent = `TASK REF ${run.id}`;
  reference.title = run.id;
  output.hidden = false;
  output.textContent = `${run.label} · ${run.status}${run.exitCode === null ? "" : ` · exit ${run.exitCode}`}\n${run.output}`;
  output.scrollTop = output.scrollHeight;
}

async function pollTaskRun(workspaceId: string, initialRun: TaskRun): Promise<void> {
  if (state.taskPollingId === initialRun.id) return;
  state.taskPollingId = initialRun.id;
  let current = initialRun;
  try {
    while (state.workspace?.workspaceId === workspaceId
        && state.taskRunId === initialRun.id
        && current.status === "RUNNING") {
      await new Promise((resolve) => window.setTimeout(resolve, 1000));
      if (state.workspace?.workspaceId !== workspaceId || state.taskRunId !== initialRun.id) break;
      const { body } = await api<TaskRun>(`/api/v1/workspaces/${workspaceId}/task-runs/${initialRun.id}`);
      current = body;
      if (state.workspace?.workspaceId === workspaceId && state.taskRunId === initialRun.id) {
        renderTaskRun(current);
      }
      await refreshPortStatuses().catch(() => undefined);
    }
  } catch (failure) {
    if (state.workspace?.workspaceId === workspaceId && state.taskRunId === initialRun.id) {
      toast(`Task status could not be refreshed: ${errorText(failure)}`, "error");
    }
  } finally {
    if (state.taskPollingId === initialRun.id) state.taskPollingId = null;
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
    renderTaskRun(run);
  } finally {
    stop.disabled = false;
    await refreshPortStatuses().catch(() => undefined);
  }
}

function setDebugState(message: string): void {
  byId("debug-state").textContent = message.toUpperCase();
}

function updateDebugControls(): void {
  const connected = state.debugSocket?.readyState === WebSocket.OPEN;
  const hasSession = state.debugSession !== null;
  byId<HTMLButtonElement>("debug-start").disabled = hasSession;
  byId<HTMLButtonElement>("debug-load-launch-config").disabled = hasSession || state.workspace === null;
  byId<HTMLSelectElement>("debug-launch-config").disabled = hasSession || state.debugLaunchConfigurations.length === 0;
  byId<HTMLButtonElement>("debug-stop").disabled = !hasSession;
  byId<HTMLButtonElement>("debug-reconnect").hidden = !hasSession || connected;
  byId<HTMLButtonElement>("debug-reconnect").disabled = !hasSession;
  byId<HTMLSelectElement>("debug-language").disabled = hasSession;
  byId<HTMLInputElement>("debug-program").disabled = hasSession;
  byId<HTMLInputElement>("debug-main-class").disabled = hasSession;
  byId<HTMLInputElement>("debug-working-directory").disabled = hasSession;
  byId<HTMLInputElement>("debug-arguments").disabled = hasSession;
  byId<HTMLInputElement>("debug-stop-on-entry").disabled = hasSession;
  byId<HTMLButtonElement>("debug-apply-breakpoints").disabled = !connected;
  byId<HTMLFormElement>("debug-watch-form").querySelector<HTMLButtonElement>("button")!.disabled = !connected;
  byId<HTMLFormElement>("debug-console-form").querySelector<HTMLButtonElement>("button")!.disabled = !connected;
  document.querySelectorAll<HTMLButtonElement>("[data-debug-command]").forEach((item) => {
    const paused = item.dataset.debugCommand !== "pause";
    item.disabled = !connected || state.debugThreadId === null || state.debugPaused !== paused;
  });
}

function configureDebugLanguageFields(): void {
  const isJava = byId<HTMLSelectElement>("debug-language").value === "java";
  byId("debug-program-field").hidden = isJava;
  byId("debug-main-class-field").hidden = !isJava;
}

async function loadDebugLaunchConfigurations(): Promise<void> {
  const workspace = state.workspace;
  if (!workspace) return;
  clearDebugLaunchConfigurations();
  const query = new URLSearchParams({ path: ".vscode/launch.json" });
  const { body } = await api<DocumentBody>(
    `/api/v1/workspaces/${workspace.workspaceId}/files/document?${query}`,
  );
  if (state.workspace?.workspaceId !== workspace.workspaceId) return;
  const configurations = parseDebugLaunchConfigurations(body.content);
  state.debugLaunchConfigurations = configurations;
  const select = byId<HTMLSelectElement>("debug-launch-config");
  select.replaceChildren();
  for (const configuration of configurations) {
    const option = document.createElement("option");
    option.value = configuration.name;
    option.textContent = `${configuration.name} · ${configuration.languageId}`;
    select.append(option);
  }
  byId("debug-launch-config-field").hidden = false;
  select.hidden = false;
  byId("debug-launch-config-status").textContent = `${configurations.length} supported launch configuration${configurations.length === 1 ? "" : "s"} loaded.`;
  select.value = configurations[0].name;
  applyDebugLaunchConfiguration(configurations[0].name);
  updateDebugControls();
}

function applyDebugLaunchConfiguration(name: string): void {
  const configuration = state.debugLaunchConfigurations.find((item) => item.name === name);
  if (!configuration) return;
  byId<HTMLSelectElement>("debug-language").value = configuration.languageId;
  byId<HTMLInputElement>("debug-program").value = configuration.program ?? "";
  byId<HTMLInputElement>("debug-main-class").value = configuration.mainClass ?? "";
  byId<HTMLInputElement>("debug-working-directory").value = configuration.workingDirectory;
  byId<HTMLInputElement>("debug-arguments").value = JSON.stringify(configuration.arguments);
  byId<HTMLInputElement>("debug-stop-on-entry").checked = configuration.stopOnEntry;
  configureDebugLanguageFields();
}

function clearDebugLaunchConfigurations(): void {
  state.debugLaunchConfigurations = [];
  byId<HTMLSelectElement>("debug-launch-config").replaceChildren();
  byId<HTMLSelectElement>("debug-launch-config").hidden = true;
  byId("debug-launch-config-field").hidden = true;
  byId("debug-launch-config-status").textContent = "";
}

function clearDebugInspectors(): void {
  state.debugThreadId = null;
  state.debugFrameId = null;
  state.debugPaused = false;
  state.debugFrames = [];
  state.debugVariables = [];
  state.debugWatchValues.clear();
  byId("debug-stack").replaceChildren();
  byId("debug-variables").replaceChildren();
  renderDebugWatches();
  renderDebugBreakpoints();
  updateDebugControls();
}

function appendDebugConsole(value: string): void {
  const output = byId<HTMLPreElement>("debug-console");
  if (output.textContent === "Debugger output will appear here.") output.textContent = "";
  output.textContent = `${output.textContent ?? ""}${value}`.slice(-100_000);
  output.scrollTop = output.scrollHeight;
}

function rejectDebugRequests(message: string): void {
  for (const [sequence, pending] of state.dapPending) {
    window.clearTimeout(pending.timer);
    pending.reject(new Error(message));
    state.dapPending.delete(sequence);
  }
  for (const waiters of state.dapEventWaiters.values()) {
    for (const waiter of waiters) {
      window.clearTimeout(waiter.timer);
      waiter.reject(new Error(message));
    }
  }
  state.dapEventWaiters.clear();
}

function openDebugSocket(session: DebugSession): Promise<void> {
  const workspaceId = state.workspace?.workspaceId;
  if (!workspaceId || workspaceId !== session.workspaceId) {
    return Promise.reject(new Error("The debug session belongs to a different workspace."));
  }
  if (state.debugSocket?.readyState === WebSocket.OPEN) return Promise.resolve();
  const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
  const socket = new WebSocket(
    `${protocol}//${window.location.host}/api/v1/workspaces/${encodeURIComponent(workspaceId)}/debug-sessions/${encodeURIComponent(session.id)}/stream`,
  );
  state.debugSocket = socket;
  setDebugState("Connecting");
  updateDebugControls();
  return new Promise<void>((resolve, reject) => {
    let opened = false;
    socket.addEventListener("open", () => {
      opened = true;
      if (state.debugSocket !== socket) {
        socket.close();
        reject(new Error("The debug session changed while connecting."));
        return;
      }
      setDebugState("Connected");
      updateDebugControls();
      resolve();
    }, { once: true });
    socket.addEventListener("message", (event) => handleDapMessage(socket, String(event.data)));
    socket.addEventListener("error", () => {
      if (!opened) reject(new Error("Could not connect to the debug stream."));
    });
    socket.addEventListener("close", () => {
      if (state.debugSocket !== socket) return;
      state.debugSocket = null;
      rejectDebugRequests("The debug stream disconnected.");
      updateDebugControls();
      if (state.debugSession) setDebugState("Disconnected · reconnect available");
      if (!opened) reject(new Error("The debug stream closed before connecting."));
    });
  });
}

function sendDap(message: Record<string, unknown>): void {
  const socket = state.debugSocket;
  if (!socket || socket.readyState !== WebSocket.OPEN) {
    throw new Error("Reconnect the debug stream before sending debugger commands.");
  }
  socket.send(JSON.stringify(message));
}

function dapRequest(command: string, args: Record<string, unknown> = {}): Promise<DapMessage> {
  const sequence = ++state.debugSequence;
  return new Promise<DapMessage>((resolve, reject) => {
    const timer = window.setTimeout(() => {
      state.dapPending.delete(sequence);
      reject(new Error(`The debugger did not answer ${command}.`));
    }, 30_000);
    state.dapPending.set(sequence, { resolve, reject, timer });
    try {
      sendDap({ seq: sequence, type: "request", command, arguments: args });
    } catch (failure) {
      window.clearTimeout(timer);
      state.dapPending.delete(sequence);
      reject(failure instanceof Error ? failure : new Error("Could not send debugger request."));
    }
  });
}

function waitForDapEvent(name: string): Promise<void> {
  if (state.dapEarlyEvents.delete(name)) return Promise.resolve();
  return new Promise<void>((resolve, reject) => {
    const timer = window.setTimeout(() => {
      const waiters = state.dapEventWaiters.get(name) ?? [];
      state.dapEventWaiters.set(name, waiters.filter((item) => item.timer !== timer));
      reject(new Error(`The debug adapter did not send the ${name} event.`));
    }, 15_000);
    const waiters = state.dapEventWaiters.get(name) ?? [];
    waiters.push({ resolve, reject, timer });
    state.dapEventWaiters.set(name, waiters);
  });
}

function resolveDapEvent(name: string): void {
  const waiters = state.dapEventWaiters.get(name);
  const waiter = waiters?.shift();
  if (!waiter) {
    state.dapEarlyEvents.add(name);
    return;
  }
  window.clearTimeout(waiter.timer);
  waiter.resolve();
  if (waiters?.length === 0) state.dapEventWaiters.delete(name);
}

function handleDapMessage(socket: WebSocket, payload: string): void {
  if (state.debugSocket !== socket) return;
  let message: DapMessage;
  try {
    message = JSON.parse(payload) as DapMessage;
    if (!message || typeof message.type !== "string") throw new Error("invalid DAP message");
  } catch {
    socket.close(1007, "invalid debugger message");
    return;
  }
  if (message.type === "response" && typeof message.request_seq === "number") {
    const pending = state.dapPending.get(message.request_seq);
    if (!pending) return;
    state.dapPending.delete(message.request_seq);
    window.clearTimeout(pending.timer);
    if (message.success === false) pending.reject(new Error(message.message || `${message.command || "Debugger request"} failed.`));
    else pending.resolve(message);
    return;
  }
  if (message.type === "event" && message.event) {
    if (message.event === "initialized") resolveDapEvent("initialized");
    if (message.event === "output") {
      const output = message.body?.output;
      if (typeof output === "string") appendDebugConsole(output);
    }
    if (message.event === "stopped") {
      const threadId = message.body?.threadId;
      state.debugThreadId = typeof threadId === "number" ? threadId : null;
      state.debugPaused = true;
      setDebugState("Paused");
      updateDebugControls();
      void refreshDebugStack().catch((failure) => appendDebugConsole(`\n[debug] ${errorText(failure)}\n`));
    }
    if (message.event === "continued") {
      clearDebugInspectors();
      state.debugPaused = false;
      const threadId = message.body?.threadId;
      if (typeof threadId === "number") state.debugThreadId = threadId;
      else void refreshDebugThread().catch(() => undefined);
      setDebugState("Running");
      updateDebugControls();
    }
    if (message.event === "exited") {
      const code = message.body?.exitCode;
      appendDebugConsole(`\n[debug] program exited${typeof code === "number" ? ` with code ${code}` : ""}\n`);
    }
    if (message.event === "terminated") {
      appendDebugConsole("\n[debug] program terminated\n");
      setDebugState("Finished");
      window.setTimeout(() => { void stopDebugSession(false); }, 0);
    }
    return;
  }
  if (message.type === "request" && typeof message.seq === "number") {
    try {
      sendDap({
        seq: ++state.debugSequence,
        type: "response",
        request_seq: message.seq,
        command: message.command || "",
        success: false,
        message: "This adapter request is not supported by Jahaziel.",
      });
    } catch {
      socket.close(1008, "unsupported adapter request");
    }
  }
}

async function startDebugSession(): Promise<void> {
  const workspace = state.workspace;
  if (!workspace) return;
  if (state.debugSession) await stopDebugSession(false);
  const languageId = byId<HTMLSelectElement>("debug-language").value;
  let argumentsList: unknown;
  try {
    argumentsList = JSON.parse(byId<HTMLInputElement>("debug-arguments").value || "[]") as unknown;
  } catch {
    throw new Error("Arguments must be a JSON array of strings, such as [\"--inspect\"].");
  }
  if (!Array.isArray(argumentsList) || argumentsList.some((value) => typeof value !== "string")) {
    throw new Error("Arguments must be a JSON array of strings.");
  }
  const request = {
    languageId,
    program: languageId === "java" ? null : byId<HTMLInputElement>("debug-program").value.trim(),
    mainClass: languageId === "java" ? byId<HTMLInputElement>("debug-main-class").value.trim() : null,
    arguments: argumentsList,
    workingDirectory: byId<HTMLInputElement>("debug-working-directory").value.trim() || ".",
    stopOnEntry: byId<HTMLInputElement>("debug-stop-on-entry").checked,
  };
  const { body: session } = await api<DebugSession>(
    `/api/v1/workspaces/${workspace.workspaceId}/debug-sessions`,
    { method: "POST", body: JSON.stringify(request) },
  );
  if (state.workspace?.workspaceId !== workspace.workspaceId) {
    await api(`/api/v1/workspaces/${workspace.workspaceId}/debug-sessions/${session.id}/stop`, { method: "POST" }).catch(() => undefined);
    return;
  }
  state.debugSession = session;
  state.debugInitialized = false;
  state.debugSequence = 0;
  state.dapEarlyEvents.clear();
  clearDebugInspectors();
  byId<HTMLPreElement>("debug-console").textContent = "";
  byId<HTMLInputElement>("debug-breakpoint-path").value = state.activePath ?? "";
  setPanel("debug");
  updateDebugControls();
  try {
    await openDebugSocket(session);
    const initialize = await dapRequest("initialize", {
      clientID: "jahaziel",
      clientName: "Jahaziel",
      adapterID: session.adapterId,
      pathFormat: "path",
      linesStartAt1: true,
      columnsStartAt1: true,
      supportsVariableType: true,
      supportsVariablePaging: true,
      supportsRunInTerminalRequest: false,
    });
    const launchResponse = dapRequest("launch", {});
    await waitForDapEvent("initialized");
    state.debugInitialized = true;
    const selectedBreakpointPath =
      byId<HTMLInputElement>("debug-breakpoint-path").value.trim() || state.activePath;
    await applyDebugBreakpoints(false);
    await applyStoredDebugBreakpoints(selectedBreakpointPath);
    if (initialize.body?.supportsConfigurationDoneRequest !== false) {
      await dapRequest("configurationDone", {});
    }
    await launchResponse;
    await refreshDebugThread();
    setDebugState("Running");
  } catch (failure) {
    await stopDebugSession(false);
    throw failure;
  }
}

async function reconnectDebugSession(): Promise<void> {
  const session = state.debugSession;
  if (!session) return;
  const { body: current } = await api<DebugSession>(
    `/api/v1/workspaces/${session.workspaceId}/debug-sessions/${session.id}`,
  );
  if (current.state !== "RUNNING") {
    await stopDebugSession(false);
    throw new Error("This debug session has already ended.");
  }
  state.debugSession = current;
  await openDebugSocket(current);
  state.debugInitialized = true;
  await applyStoredDebugBreakpoints();
  setDebugState("Reconnected");
}

async function refreshDebugSessions(): Promise<void> {
  const workspace = state.workspace;
  if (!workspace) return;
  const { body: sessions } = await api<DebugSession[]>(
    `/api/v1/workspaces/${workspace.workspaceId}/debug-sessions`,
  );
  if (state.workspace?.workspaceId !== workspace.workspaceId) return;
  const current =
    sessions.find((session) => session.id === state.debugSession?.id) ?? sessions[0] ?? null;
  if (state.debugSession && current?.id !== state.debugSession.id && state.debugSocket) {
    state.debugSocket.close(1000, "debug session changed");
    state.debugSocket = null;
    rejectDebugRequests("The debug session changed.");
  }
  state.debugSession = current;
  if (current) {
    setDebugState(
      state.debugSocket?.readyState === WebSocket.OPEN
        ? "Connected"
        : "Disconnected · reconnect available",
    );
  } else {
    clearDebugInspectors();
    setDebugState("Not running");
  }
  updateDebugControls();
}

async function stopDebugSession(notifyFailure: boolean): Promise<void> {
  const session = state.debugSession;
  state.debugSession = null;
  state.debugInitialized = false;
  const socket = state.debugSocket;
  state.debugSocket = null;
  if (socket && socket.readyState < WebSocket.CLOSING) socket.close(1000, "debug session stopped");
  rejectDebugRequests("The debug session was stopped.");
  state.dapEarlyEvents.clear();
  clearDebugInspectors();
  if (session) {
    try {
      await api(`/api/v1/workspaces/${session.workspaceId}/debug-sessions/${session.id}/stop`, { method: "POST" });
    } catch (failure) {
      if (notifyFailure) toast(`Could not stop debugging: ${errorText(failure)}`, "error");
    }
  }
  setDebugState("Not running");
  updateDebugControls();
}

async function applyDebugBreakpoints(showToast = true): Promise<void> {
  const workspace = state.workspace;
  if (!workspace || !state.debugSession) return;
  const path = byId<HTMLInputElement>("debug-breakpoint-path").value.trim() || state.activePath;
  if (!path) {
    if (showToast) throw new Error("Open a workspace file or enter a workspace-relative source path.");
    return;
  }
  const lines = [...new Set(
    byId<HTMLInputElement>("debug-breakpoint-lines").value
      .split(/[\s,]+/)
      .filter(Boolean)
      .map(Number)
      .filter((line) => Number.isSafeInteger(line) && line > 0),
  )];
  state.debugBreakpoints.set(path, lines);
  refreshBreakpointGutterForPath(path);
  renderDebugBreakpoints();
  const response = await sendDebugBreakpointSet(path, lines);
  if (showToast) {
    const verified = response.body?.breakpoints;
    const count = Array.isArray(verified) ? verified.filter((value) => (value as { verified?: unknown }).verified === true).length : lines.length;
    const name = path.split("/").at(-1) || path;
    toast(`${count} breakpoint${count === 1 ? "" : "s"} accepted for ${name}.`);
  }
}

async function sendDebugBreakpointSet(path: string, lines: number[]): Promise<DapMessage> {
  const name = path.split("/").at(-1) || path;
  return dapRequest("setBreakpoints", {
    source: { name, path },
    breakpoints: lines.map((line) => ({ line })),
    sourceModified: false,
  });
}

async function applyStoredDebugBreakpoints(skipPath?: string | null): Promise<void> {
  for (const [path, lines] of state.debugBreakpoints) {
    if (path !== skipPath) await sendDebugBreakpointSet(path, lines);
  }
}

async function toggleDebugBreakpoint(path: string, line: number): Promise<void> {
  const lines = new Set(state.debugBreakpoints.get(path) ?? []);
  if (lines.has(line)) lines.delete(line);
  else lines.add(line);
  const next = [...lines].sort((left, right) => left - right);
  state.debugBreakpoints.set(path, next);
  refreshBreakpointGutterForPath(path);
  renderDebugBreakpoints();
  if (state.activePath === path) {
    byId<HTMLInputElement>("debug-breakpoint-lines").value = next.join(", ");
  }
  if (state.debugInitialized && state.debugSocket?.readyState === WebSocket.OPEN) {
    await sendDebugBreakpointSet(path, next);
  }
}

function refreshBreakpointGutterForPath(path: string): void {
  const opened = state.openDocuments.get(path);
  if (opened) refreshBreakpointGutter(opened.wrapper, path);
}

function refreshBreakpointGutter(wrapper: HTMLElement, path: string): void {
  const active = new Set(state.debugBreakpoints.get(path) ?? []);
  wrapper.querySelectorAll<HTMLElement>(".cm-lineNumbers .cm-gutterElement").forEach((element) => {
    const line = Number(element.textContent?.trim());
    if (!Number.isSafeInteger(line) || line < 1) return;
    const enabled = active.has(line);
    element.classList.toggle("cm-debug-breakpoint", enabled);
    element.dataset.debugLine = String(line);
    element.setAttribute("role", "button");
    element.setAttribute("tabindex", "0");
    element.setAttribute("aria-label", `${enabled ? "Clear" : "Set"} breakpoint at ${path}, line ${line}`);
    element.setAttribute("aria-pressed", String(enabled));
  });
}

function renderDebugBreakpoints(): void {
  const list = byId<HTMLUListElement>("debug-breakpoint-list");
  list.replaceChildren();
  for (const [path, lines] of state.debugBreakpoints) {
    const item = document.createElement("li");
    item.className = "debug-empty";
    item.textContent = `${path}: ${lines.length ? lines.join(", ") : "cleared"}`;
    list.append(item);
  }
}

async function sendDebugControl(command: string): Promise<void> {
  if (state.debugThreadId === null) throw new Error("Pause the program before using stepping controls.");
  await dapRequest(command, { threadId: state.debugThreadId });
  if (command === "continue") setDebugState("Running");
}

async function refreshDebugThread(): Promise<void> {
  if (state.debugSocket?.readyState !== WebSocket.OPEN) return;
  const { body } = await dapRequest("threads");
  const threads = Array.isArray(body?.threads) ? body.threads : [];
  const first = threads.find(
    (thread): thread is { id: number } =>
      typeof thread === "object" && thread !== null && typeof (thread as { id?: unknown }).id === "number",
  );
  if (state.debugThreadId === null && first) state.debugThreadId = first.id;
  updateDebugControls();
}

async function refreshDebugStack(): Promise<void> {
  if (state.debugThreadId === null || state.debugSocket?.readyState !== WebSocket.OPEN) return;
  const { body } = await dapRequest("stackTrace", { threadId: state.debugThreadId, levels: 30 });
  const frames = body?.stackFrames;
  state.debugFrames = Array.isArray(frames)
    ? frames.filter((frame): frame is DapFrame => typeof frame === "object" && frame !== null && typeof (frame as DapFrame).id === "number")
    : [];
  renderDebugStack();
  if (state.debugFrames.length) await selectDebugFrame(state.debugFrames[0]);
  else {
    state.debugFrameId = null;
    state.debugVariables = [];
    renderDebugVariables();
  }
}

function renderDebugStack(): void {
  const list = byId<HTMLUListElement>("debug-stack");
  list.replaceChildren();
  if (state.debugFrames.length === 0) {
    const empty = document.createElement("li");
    empty.className = "debug-empty";
    empty.textContent = "No frames are available.";
    list.append(empty);
    return;
  }
  for (const frame of state.debugFrames) {
    const item = document.createElement("li");
    const select = document.createElement("button");
    const source = frame.source?.path || frame.source?.name || "unknown source";
    select.textContent = `${frame.name} · ${source}${frame.line ? `:${frame.line}` : ""}`;
    select.setAttribute("aria-current", String(frame.id === state.debugFrameId));
    select.addEventListener("click", () => {
      void selectDebugFrame(frame).catch((failure) => toast(errorText(failure), "error"));
      if (frame.source?.path && !frame.source.path.startsWith("/")) {
        void openDocument(frame.source.path).catch((failure) => toast(errorText(failure), "error"));
      }
    });
    item.append(select);
    list.append(item);
  }
}

async function selectDebugFrame(frame: DapFrame): Promise<void> {
  state.debugFrameId = frame.id;
  renderDebugStack();
  const { body } = await dapRequest("scopes", { frameId: frame.id });
  const scopes = Array.isArray(body?.scopes) ? body.scopes : [];
  const references = scopes
    .map((scope) => (scope as { variablesReference?: unknown }).variablesReference)
    .filter((reference): reference is number => typeof reference === "number" && reference > 0);
  if (references.length === 0) {
    state.debugVariables = [];
    renderDebugVariables();
  } else {
    const variables = await dapRequest("variables", { variablesReference: references[0] });
    const values = variables.body?.variables;
    state.debugVariables = Array.isArray(values)
      ? values.filter((value): value is DapVariable => typeof value === "object" && value !== null && typeof (value as DapVariable).name === "string")
      : [];
    renderDebugVariables();
  }
  await refreshDebugWatches();
}

function renderDebugVariables(): void {
  const list = byId<HTMLUListElement>("debug-variables");
  list.replaceChildren();
  if (state.debugVariables.length === 0) {
    const empty = document.createElement("li");
    empty.className = "debug-empty";
    empty.textContent = "No variables in this scope.";
    list.append(empty);
    return;
  }
  for (const variable of state.debugVariables) {
    const item = document.createElement("li");
    item.className = "debug-watch-row";
    const name = document.createElement("span");
    name.className = "debug-watch-expression";
    name.textContent = variable.name;
    const value = document.createElement("span");
    value.className = "debug-watch-value";
    value.textContent = variable.value;
    if (variable.type) value.title = variable.type;
    item.append(name, value);
    list.append(item);
  }
}

function renderDebugWatches(): void {
  const list = byId<HTMLUListElement>("debug-watches");
  list.replaceChildren();
  if (state.debugWatches.length === 0) {
    const empty = document.createElement("li");
    empty.className = "debug-empty";
    empty.textContent = "Add an expression to inspect it when paused.";
    list.append(empty);
    return;
  }
  for (const expression of state.debugWatches) {
    const item = document.createElement("li");
    item.className = "debug-watch-row";
    const name = document.createElement("span");
    name.className = "debug-watch-expression";
    name.textContent = expression;
    const value = document.createElement("span");
    value.className = "debug-watch-value";
    value.textContent = state.debugWatchValues.get(expression) ?? "—";
    item.append(name, value);
    list.append(item);
  }
}

async function refreshDebugWatches(): Promise<void> {
  if (state.debugFrameId === null || state.debugSocket?.readyState !== WebSocket.OPEN) return;
  for (const expression of state.debugWatches) {
    try {
      const { body } = await dapRequest("evaluate", {
        expression,
        frameId: state.debugFrameId,
        context: "watch",
      });
      state.debugWatchValues.set(expression, typeof body?.result === "string" ? body.result : "<no value>");
    } catch (failure) {
      state.debugWatchValues.set(expression, errorText(failure));
    }
  }
  renderDebugWatches();
}

async function addDebugWatch(event: SubmitEvent): Promise<void> {
  event.preventDefault();
  const input = byId<HTMLInputElement>("debug-watch-expression");
  const expression = input.value.trim();
  if (!expression) return;
  if (state.debugWatches.length >= 32) throw new Error("A debug session can track up to 32 watch expressions.");
  if (!state.debugWatches.includes(expression)) state.debugWatches.push(expression);
  input.value = "";
  renderDebugWatches();
  await refreshDebugWatches();
}

async function evaluateDebugConsole(event: SubmitEvent): Promise<void> {
  event.preventDefault();
  const input = byId<HTMLInputElement>("debug-console-input");
  const expression = input.value.trim();
  if (!expression) return;
  input.value = "";
  try {
    const { body } = await dapRequest("evaluate", {
      expression,
      ...(state.debugFrameId === null ? {} : { frameId: state.debugFrameId }),
      context: "repl",
    });
    const result = typeof body?.result === "string" ? body.result : "<no value>";
    appendDebugConsole(`> ${expression}\n${result}\n`);
  } catch (failure) {
    appendDebugConsole(`> ${expression}\n[error] ${errorText(failure)}\n`);
  }
}

function renderPreviewLinks(workspace: Workspace): void {
  const container = byId("preview-links");
  container.replaceChildren();
  byId<HTMLButtonElement>("refresh-preview-ports").hidden = workspace.ports.length === 0;
  for (const port of workspace.ports) {
    const link = document.createElement("button");
    link.type = "button";
    link.className = "preview-chip";
    link.textContent = `:${port.port} Preview ↗`;
    link.disabled = port.listening !== true;
    link.title = port.listening === true ? `Open preview on port ${port.port}` : `Port ${port.port} is not listening`;
    link.addEventListener("click", () => openPreview(port));
    const status = document.createElement("span");
    status.className = "preview-port-status";
    status.dataset.listening = String(port.listening === true);
    status.textContent = port.listening === null ? "CHECKING" : port.listening ? "LISTENING" : "NOT LISTENING";
    container.append(link, status);
  }
  renderPreviewPanel();
}

function renderPreviewPanel(): void {
  const workspace = state.workspace;
  const select = byId<HTMLSelectElement>("preview-port-select");
  select.replaceChildren();
  const placeholder = document.createElement("option");
  placeholder.value = "";
  placeholder.textContent = "Select a listening port";
  placeholder.disabled = true;
  select.append(placeholder);
  for (const port of workspace?.ports ?? []) {
    const option = document.createElement("option");
    option.value = String(port.port);
    option.textContent = `:${port.port} · ${port.listening === true ? "listening" : port.listening === false ? "not listening" : "not checked"}`;
    option.disabled = port.listening !== true;
    select.append(option);
  }
  const current = workspace?.ports.find((port) => port.port === state.previewPort && port.listening === true);
  const frame = byId<HTMLIFrameElement>("preview-frame");
  const link = byId<HTMLAnchorElement>("preview-new-tab");
  const empty = byId("preview-empty");
  const reload = byId<HTMLButtonElement>("preview-reload");
  if (!current) {
    const selected = workspace?.ports.find((port) => port.port === state.previewPort);
    byId("preview-state").textContent = selected
      ? `Port :${selected.port} is not listening.`
      : "Select a listening port to open its preview.";
    frame.hidden = true;
    empty.hidden = false;
    reload.disabled = true;
    link.href = "#";
    link.setAttribute("aria-disabled", "true");
    select.value = "";
    select.disabled = !workspace?.ports.some((port) => port.listening === true);
    return;
  }
  select.value = String(current.port);
  byId("preview-state").textContent = `Listening on :${current.port}`;
  empty.hidden = true;
  frame.hidden = false;
  if (state.previewUrl !== current.url || !frame.src || frame.src === window.location.href) {
    state.previewUrl = current.url;
    frame.src = current.url;
  }
  reload.disabled = false;
  link.href = current.url;
  link.setAttribute("aria-disabled", "false");
  select.disabled = false;
}

function openPreview(port: PortEntry): void {
  if (port.listening !== true) {
    toast(`Port ${port.port} is not listening yet. Start the project task and check ports again.`, "error");
    return;
  }
  state.previewPort = port.port;
  state.previewUrl = null;
  setPanel("preview");
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
  if (existing) {
    activateDocument(path);
    if (window.matchMedia("(max-width: 900px)").matches) setPanel("editor");
    return;
  }
  const workspace = state.workspace;
  if (!workspace) return;
  const query = new URLSearchParams({ path });
  const { body, response } = await api<DocumentBody>(`/api/v1/workspaces/${workspace.workspaceId}/files/document?${query}`);
  const localDraft = readLocalDraft(workspace.workspaceId, path);
  const initialContent = localDraft?.content ?? body.content;
  const recoveredDraft = localDraft !== null && localDraft.content !== body.content;
  const wrapper = document.createElement("div");
  wrapper.className = "editor-instance";
  const documentState = {} as OpenDocument;
  const toggleFromGutter = (event: Event): void => {
    if (!(event.target instanceof Element)) return;
    const gutterLine = event.target.closest<HTMLElement>(".cm-lineNumbers .cm-gutterElement");
    if (!gutterLine) return;
    const line = Number(gutterLine.dataset.debugLine || gutterLine.textContent?.trim());
    if (!Number.isSafeInteger(line) || line < 1) return;
    event.preventDefault();
    event.stopPropagation();
    void toggleDebugBreakpoint(path, line).catch((failure) => toast(errorText(failure), "error"));
  };
  wrapper.addEventListener("click", toggleFromGutter, true);
  wrapper.addEventListener("keydown", (event: KeyboardEvent) => {
    if (event.key === "Enter" || event.key === " ") toggleFromGutter(event);
  }, true);
  const [editor, language] = await Promise.all([import("codemirror"), languageFor(path)]);
  const { EditorView } = editor;
  const view = new EditorView({
    doc: initialContent,
    parent: wrapper,
    extensions: [
      editor.basicSetup,
      language,
      EditorView.updateListener.of((update) => {
        if (update.docChanged || update.viewportChanged) {
          window.requestAnimationFrame(() => refreshBreakpointGutter(wrapper, path));
        }
        if (!update.docChanged) return;
        documentState.dirty = update.state.doc.toString() !== documentState.savedContent;
        documentState.recoveredDraft = false;
        if (documentState.dirty) scheduleLocalDraft(workspace.workspaceId, path, update.state.doc.toString());
        else clearLocalDraft(workspace.workspaceId, path);
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
    dirty: initialContent !== body.content,
    recoveredDraft,
    conflictRemoteVersion: null,
    conflictRemoteContent: null,
  });
  state.openDocuments.set(path, documentState);
  refreshBreakpointGutter(wrapper, path);
  state.selectedPath = path;
  activateDocument(path);
  if (window.matchMedia("(max-width: 900px)").matches) setPanel("editor");
}

function activateDocument(path: string): void {
  const next = state.openDocuments.get(path);
  if (!next) return;
  state.openDocuments.forEach((opened) => { opened.wrapper.hidden = opened.path !== path; });
  state.activePath = path;
  byId("active-file").textContent = path;
  byId<HTMLInputElement>("debug-breakpoint-path").value = path;
  byId<HTMLInputElement>("debug-breakpoint-lines").value = (state.debugBreakpoints.get(path) ?? []).join(", ");
  byId("editor-empty").hidden = true;
  byId("status-language").textContent = languageName(path);
  if (state.workspace && !["ready", "active", "idle"].includes(state.workspace.state)) {
    state.languageServices?.closeDocument(path);
    byId("language-state").textContent = "Start the workspace to use language services";
  } else {
    state.languageServices?.openDocument(languageIdForPath(path), path, next.view.state.doc.toString());
  }
  refreshBreakpointGutter(next.wrapper, path);
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
      if (opened.dirty && state.workspace) clearLocalDraft(state.workspace.workspaceId, opened.path);
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
  byId("save-state").textContent = current?.dirty
    ? current.recoveredDraft ? "Recovered local draft · unsaved" : "Unsaved changes · saved locally"
    : current ? "Saved" : "";
  byId("save-state").className = current?.dirty ? "save-state dirty" : "save-state";
  const conflict = byId("save-conflict-actions");
  conflict.hidden = current?.conflictRemoteVersion === null || current?.conflictRemoteContent === null || !current;
}

function useServerVersion(): void {
  const workspace = state.workspace;
  const opened = state.activePath ? state.openDocuments.get(state.activePath) : undefined;
  if (!workspace || !opened || opened.conflictRemoteVersion === null || opened.conflictRemoteContent === null) return;
  if (opened.dirty && !window.confirm(`Discard your local edits to ${opened.path} and use the server version?`)) return;
  opened.version = opened.conflictRemoteVersion;
  opened.savedContent = opened.conflictRemoteContent;
  opened.conflictRemoteVersion = null;
  opened.conflictRemoteContent = null;
  opened.recoveredDraft = false;
  opened.view.dispatch({
    changes: { from: 0, to: opened.view.state.doc.length, insert: opened.savedContent },
  });
  opened.dirty = false;
  clearLocalDraft(workspace.workspaceId, opened.path);
  renderTabs();
  updateSaveState();
  toast("Loaded the latest server version.");
}

function keepLocalVersion(): void {
  const workspace = state.workspace;
  const opened = state.activePath ? state.openDocuments.get(state.activePath) : undefined;
  if (!workspace || !opened || opened.conflictRemoteVersion === null || opened.conflictRemoteContent === null) return;
  opened.version = opened.conflictRemoteVersion;
  opened.savedContent = opened.conflictRemoteContent;
  opened.conflictRemoteVersion = null;
  opened.conflictRemoteContent = null;
  opened.recoveredDraft = false;
  opened.dirty = opened.view.state.doc.toString() !== opened.savedContent;
  if (opened.dirty) scheduleLocalDraft(workspace.workspaceId, opened.path, opened.view.state.doc.toString());
  renderTabs();
  updateSaveState();
  toast("Your edits are ready to save against the latest version. Review them, then choose Save.");
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
    opened.recoveredDraft = false;
    clearLocalDraft(workspace.workspaceId, path);
    state.languageServices?.saveDocument(path);
    renderTabs();
    updateSaveState();
    toast("Saved.");
    refreshGitSafely();
  } catch (failure) {
    if (failure instanceof Error && failure.message.startsWith("412")) {
      const latest = await api<DocumentBody>(
        `/api/v1/workspaces/${workspace.workspaceId}/files/document?${new URLSearchParams({ path })}`,
      );
      if (state.workspace?.workspaceId !== workspace.workspaceId || state.openDocuments.get(path) !== opened) return;
      opened.conflictRemoteVersion = latest.response.headers.get("ETag") || latest.body.version;
      opened.conflictRemoteContent = latest.body.content;
      byId("save-state").textContent = "Changed on disk — reload before saving";
      byId("save-state").className = "save-state conflict";
      updateSaveState();
      byId("save-state").textContent = "Server version changed";
      byId("save-state").className = "save-state conflict";
      toast("Your local edits are preserved. Choose the server copy or keep your edits before saving again.", "error");
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
  byId("terminal-key-strip").hidden = true;
  setTerminalModifier("ctrl", false);
  setTerminalModifier("alt", false);
  const sessionId = state.terminalSessionId;
  state.terminalSessionId = null;
  byId("terminal-surface").replaceChildren();
  if (closeServerSession && sessionId) {
    return api(`/api/v1/terminals/${sessionId}`, { method: "DELETE" })
      .then(() => refreshSessions ? refreshTerminalSessions() : undefined);
  }
  return Promise.resolve();
}

function setTerminalModifier(modifier: "ctrl" | "alt", enabled: boolean): void {
  state.terminalModifiers[modifier] = enabled;
  const button = document.querySelector<HTMLButtonElement>(`[data-terminal-modifier="${modifier}"]`);
  button?.setAttribute("aria-pressed", String(enabled));
}

function applyTerminalModifiers(data: string): string {
  let output = data;
  if (state.terminalModifiers.ctrl) {
    const chars = Array.from(output);
    const first = chars.shift();
    if (first) {
      const code = first.toUpperCase().codePointAt(0) ?? 0;
      if (code >= 64 && code <= 95) output = String.fromCodePoint(code - 64) + chars.join("");
      else if (first === "?") output = "\u007f" + chars.join("");
    }
    setTerminalModifier("ctrl", false);
  }
  if (state.terminalModifiers.alt) {
    output = `\u001b${output}`;
    setTerminalModifier("alt", false);
  }
  return output;
}

function sendTerminalInput(data: string): void {
  const socket = state.socket;
  if (!socket || socket.readyState !== WebSocket.OPEN) {
    toast("Open or reconnect a terminal session first.", "error");
    return;
  }
  setTerminalModifier("ctrl", false);
  setTerminalModifier("alt", false);
  socket.send(JSON.stringify({ type: "input", data } satisfies StreamMessage));
  state.terminal?.focus();
}

async function copyTerminalSelection(): Promise<void> {
  const selection = state.terminal?.getSelection() ?? "";
  if (!selection) {
    toast("Select terminal text first.", "error");
    return;
  }
  try {
    await navigator.clipboard.writeText(selection);
    toast("Terminal selection copied.");
  } catch {
    toast("The browser did not allow clipboard access. Select and copy the text manually.", "error");
  }
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
    rows: session.rows,
    cols: session.cols,
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
  setPanel("terminal");
  byId("terminal-key-strip").hidden = false;
  setTerminalModifier("ctrl", false);
  setTerminalModifier("alt", false);
  byId<HTMLButtonElement>("close-terminal").hidden = false;
  byId("terminal-state").textContent = "Connecting…";
  const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
  const socket = new WebSocket(`${protocol}//${window.location.host}/api/v1/terminals/${session.sessionId}/stream`);
  state.socket = socket;
  let exited = false;
  socket.addEventListener("open", () => {
    if (state.socket !== socket || state.terminalSessionId !== session.sessionId) {
      socket.close();
      return;
    }
    terminal.focus();
    byId("terminal-state").textContent = "Restoring…";
  });
  socket.addEventListener("message", (event) => {
    try {
      const message = JSON.parse(String(event.data)) as StreamMessage;
      if (message.type === "history") {
        const restore = (): void => {
          if (state.socket !== socket || state.terminalSessionId !== session.sessionId) return;
          fit.fit();
          if (socket.readyState === WebSocket.OPEN) {
            socket.send(JSON.stringify({ type: "resize", rows: terminal.rows, cols: terminal.cols } satisfies StreamMessage));
          }
          terminal.scrollToBottom();
          terminal.focus();
          if (!exited) byId("terminal-state").textContent = "Connected";
        };
        if (message.data) terminal.write(message.data, restore);
        else restore();
      }
      if (message.type === "output" && message.data) terminal.write(message.data);
      if (message.type === "exit") {
        exited = true;
        byId("terminal-state").textContent = `Exited (${message.exitCode ?? "unknown"})`;
      }
    } catch { terminal.write("\r\n[terminal] invalid server message\r\n"); }
  });
  socket.addEventListener("close", () => {
    if (state.socket === socket) byId("terminal-state").textContent = "Disconnected";
  });
  socket.addEventListener("error", () => {
    if (state.socket === socket) byId("terminal-state").textContent = "Connection error";
  });
  terminal.onData((data) => {
    const input = applyTerminalModifiers(data);
    if (socket.readyState === WebSocket.OPEN) socket.send(JSON.stringify({ type: "input", data: input } satisfies StreamMessage));
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
  flushLocalDrafts();
  void stopDebugSession(false);
  void closeTerminal(false);
  clearWorkspaceSearch();
  resetGitPanel();
  state.openDocuments.forEach((opened) => opened.view.destroy());
  state.openDocuments.clear();
  state.activePath = null;
  state.selectedPath = null;
  state.workspace = null;
  clearDebugLaunchConfigurations();
  state.previewPort = null;
  state.previewUrl = null;
  state.languageServices?.close();
  state.languageServices = null;
  state.languagePacks = [];
  byId("language-pack-list").replaceChildren();
  byId("language-diagnostics").replaceChildren();
  byId("language-state").textContent = "OPTIONAL";
  if (navigate) history.pushState({}, "", "/");
  setPanel("files");
  setPage("home");
  renderWorkspaces().catch((failure) => toast(errorText(failure), "error"));
}

function wireUi(): void {
  document.querySelectorAll<HTMLButtonElement>(".workbench-panel-nav [data-panel]").forEach((item) => {
    item.addEventListener("click", () => setPanel(item.dataset.panel as typeof state.activePanel));
  });
  byId<HTMLSelectElement>("preview-port-select").addEventListener("change", (event) => {
    const portNumber = Number((event.currentTarget as HTMLSelectElement).value);
    const port = state.workspace?.ports.find((candidate) => candidate.port === portNumber);
    if (port) openPreview(port);
  });
  byId("preview-reload").addEventListener("click", () => {
    const frame = byId<HTMLIFrameElement>("preview-frame");
    if (state.previewUrl) frame.src = state.previewUrl;
  });
  byId<HTMLAnchorElement>("preview-new-tab").addEventListener("click", (event) => {
    if ((event.currentTarget as HTMLAnchorElement).getAttribute("aria-disabled") === "true") event.preventDefault();
  });
  byId<HTMLSelectElement>("debug-language").addEventListener("change", configureDebugLanguageFields);
  byId<HTMLButtonElement>("debug-load-launch-config").addEventListener("click", async (event) => {
    const control = event.currentTarget as HTMLButtonElement;
    control.disabled = true;
    try { await loadDebugLaunchConfigurations(); }
    catch (failure) { toast(errorText(failure), "error"); }
    finally { updateDebugControls(); }
  });
  byId<HTMLSelectElement>("debug-launch-config").addEventListener("change", (event) =>
    applyDebugLaunchConfiguration((event.currentTarget as HTMLSelectElement).value),
  );
  byId<HTMLButtonElement>("debug-start").addEventListener("click", () =>
    startDebugSession().catch((failure) => toast(errorText(failure), "error")),
  );
  byId<HTMLButtonElement>("debug-reconnect").addEventListener("click", () =>
    reconnectDebugSession().catch((failure) => toast(errorText(failure), "error")),
  );
  byId<HTMLButtonElement>("debug-stop").addEventListener("click", () =>
    void stopDebugSession(true),
  );
  byId<HTMLButtonElement>("debug-apply-breakpoints").addEventListener("click", () =>
    applyDebugBreakpoints().catch((failure) => toast(errorText(failure), "error")),
  );
  document.querySelectorAll<HTMLButtonElement>("[data-debug-command]").forEach((item) => {
    item.addEventListener("click", () => {
      const command = item.dataset.debugCommand;
      if (command) sendDebugControl(command).catch((failure) => toast(errorText(failure), "error"));
    });
  });
  byId<HTMLFormElement>("debug-watch-form").addEventListener("submit", (event) =>
    addDebugWatch(event as SubmitEvent).catch((failure) => toast(errorText(failure), "error")),
  );
  byId<HTMLFormElement>("debug-console-form").addEventListener("submit", (event) =>
    evaluateDebugConsole(event as SubmitEvent).catch((failure) => toast(errorText(failure), "error")),
  );
  configureDebugLanguageFields();
  updateDebugControls();
  document.querySelectorAll<HTMLButtonElement>("[data-terminal-modifier]").forEach((item) => {
    item.addEventListener("click", () => {
      const modifier = item.dataset.terminalModifier as "ctrl" | "alt";
      setTerminalModifier(modifier, !state.terminalModifiers[modifier]);
      state.terminal?.focus();
    });
  });
  document.querySelectorAll<HTMLButtonElement>("[data-terminal-input]").forEach((item) => {
    item.addEventListener("click", () => sendTerminalInput(item.dataset.terminalInput ?? ""));
  });
  document.querySelectorAll<HTMLButtonElement>("[data-terminal-shortcut]").forEach((item) => {
    item.addEventListener("click", () => sendTerminalInput(String.fromCharCode((item.dataset.terminalShortcut ?? "c").charCodeAt(0) & 0x1f)));
  });
  byId("terminal-copy-selection").addEventListener("click", () => void copyTerminalSelection());
  window.addEventListener("pagehide", flushLocalDrafts);
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
  byId("use-server-version").addEventListener("click", useServerVersion);
  byId("keep-local-version").addEventListener("click", keepLocalVersion);
  byId("runtime-toggle").addEventListener("click", () => toggleWorkspace().catch((failure) => toast(errorText(failure), "error")));
  byId("refresh-preview-ports").addEventListener("click", () =>
    refreshPortStatuses().catch((failure) => toast(errorText(failure), "error")),
  );
  byId("back-home").addEventListener("click", () => backToWorkspaces());
  byId("terminal-new").addEventListener("click", () => openTerminal().catch((failure) => toast(errorText(failure), "error")));
  byId("terminal-opencode").addEventListener("click", () => launchOpenCode().catch((failure) => toast(errorText(failure), "error")));
  byId("opencode-update").addEventListener("click", () => updateOpenCode().catch((failure) => toast(errorText(failure), "error")));
  byId("opencode-remove").addEventListener("click", () => removeOpenCode().catch((failure) => toast(errorText(failure), "error")));
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
    if (keyboard.ctrlKey || keyboard.metaKey) {
      const key = keyboard.key.toLowerCase();
      if (key === "s") {
        keyboard.preventDefault();
        void saveActiveDocument().catch((failure) => toast(errorText(failure), "error"));
      }
      if (key === "p") {
        keyboard.preventDefault();
        if (window.matchMedia("(max-width: 900px)").matches) setPanel("files");
        byId<HTMLInputElement>("workspace-search").focus();
      }
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
