///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import type { EditorView } from "codemirror";
import { parseDebugLaunchConfigurations, type DebugLaunchConfiguration } from "../debug-launch-config";
import { LanguageServices, type LanguageDiagnostic, type LanguagePack } from "../language-services";
import { api, errorText } from "./api";
import { byId, effect } from "./dom";
import {
  setPage,
  setPanel,
  setState,
  state,
  toast,
  type AgentToolStatus,
  type CandidatePort,
  type DapFrame,
  type DapVariable,
  type DebugSession,
  type DebugTarget,
  type DocumentBody,
  type FileEntry,
  type GitChange,
  type Panel,
  type PortEntry,
  type QuickResult,
  type SearchResult,
  type SetupStatus,
  type TabInfo,
  type TaskRun,
  type TerminalSession,
  type User,
  type Workspace,
  type WorkspaceTask,
} from "./store";

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
type BreakpointEntry = { path: string; lines: number[] };

const runtime = {
  openDocuments: new Map<string, OpenDocument>(),
  terminal: null as import("@xterm/xterm").Terminal | null,
  fitAddon: null as import("@xterm/addon-fit").FitAddon | null,
  terminalObserver: null as ResizeObserver | null,
  terminalSessionId: null as string | null,
  socket: null as WebSocket | null,
  taskRunId: null as string | null,
  taskPollingId: null as string | null,
  setupPolling: false,
  searchTimer: null as number | null,
  gitHasStaged: false,
  languageServices: null as LanguageServices | null,
  agentToolPolling: false,
  debugSession: null as DebugSession | null,
  debugInitialized: false,
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
  draftTimers: new Map<string, number>(),
  pendingDrafts: new Map<string, string>(),
  resizingPreview: false,
  previewStartY: 0,
  previewStartHeight: 0,
};

const languages: Record<string, string> = {
  node: "Node, TypeScript",
  python: "Python",
  java: "Java",
  cpp: "C, C++",
  go: "Go",
  rust: "Rust",
};

const setupSummaries: Record<string, string> = {
  UNTRACKED: "No template task metadata.",
  PENDING: "Template setup is queued and will start shortly.",
  RUNNING: "Template setup is running…",
  FAILED: "Template setup failed.",
  READY: "Template setup is ready.",
};

export { setPage, setPanel };

function workspaceRunning(): boolean {
  return state.workspace !== null && ["ready", "active", "idle"].includes(state.workspace.state);
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
  const content = runtime.pendingDrafts.get(key);
  if (content === undefined) return;
  runtime.draftTimers.delete(key);
  runtime.pendingDrafts.delete(key);
  try {
    localStorage.setItem(key, JSON.stringify({ content, updatedAt: Date.now() } satisfies LocalDraft));
  } catch {
    if (!state.draftStorageWarningShown) {
      setState("draftStorageWarningShown", true);
      toast("Browser recovery storage is full or unavailable. Save this file to the server.", "error");
    }
  }
}

function scheduleLocalDraft(workspaceId: string, path: string, content: string): void {
  const key = localDraftKey(workspaceId, path);
  if (!key) return;
  const existing = runtime.draftTimers.get(key);
  if (existing !== undefined) window.clearTimeout(existing);
  runtime.pendingDrafts.set(key, content);
  runtime.draftTimers.set(key, window.setTimeout(() => persistLocalDraft(key), 250));
}

function clearLocalDraft(workspaceId: string, path: string): void {
  const key = localDraftKey(workspaceId, path);
  if (!key) return;
  const timer = runtime.draftTimers.get(key);
  if (timer !== undefined) window.clearTimeout(timer);
  runtime.draftTimers.delete(key);
  runtime.pendingDrafts.delete(key);
  try { localStorage.removeItem(key); } catch { /* Browser storage can be disabled. */ }
}

function flushLocalDrafts(): void {
  for (const [key, timer] of runtime.draftTimers) {
    window.clearTimeout(timer);
    persistLocalDraft(key);
  }
}

export async function loadIdentity(): Promise<void> {
  try {
    const { body } = await api<User>("/api/v1/me");
    setState("user", body);
    byId("user-name").textContent = body.displayName || body.email;
    byId("user-email").textContent = body.email;
    byId("login-link").hidden = true;
    byId("user-badge").hidden = false;
    setState("loadingState", "Synced");
  } catch {
    byId("login-link").hidden = false;
    byId("user-badge").hidden = true;
    setState("loadingState", "Sign in to continue");
  }
}

export async function loadTemplates(): Promise<void> {
  const { body: names } = await api<string[]>("/api/v1/templates");
  setState("templates", names);
  if (names.length === 0) {
    byId("template-note").textContent = "No templates are configured on this server yet.";
  }
}

export async function renderWorkspaces(): Promise<void> {
  if (!state.user) return;
  const { body: workspaces } = await api<Workspace[]>("/api/v1/me/workspaces");
  setState("workspaces", workspaces);
  byId("empty-workspaces").hidden = workspaces.length !== 0;
  byId("workspace-count").textContent = String(workspaces.length).padStart(2, "0");
}

export async function createWorkspace(event: SubmitEvent): Promise<void> {
  event.preventDefault();
  const input = byId<HTMLInputElement>("workspace-name");
  const template = byId<HTMLSelectElement>("template-select").value;
  const name = input.value.trim();
  if (!name || !template) return;
  const { body: created } = await api<Workspace>(`/api/v1/templates/${encodeURIComponent(template)}/apply`, {
    method: "POST",
    body: JSON.stringify({ workspaceName: name }),
  });
  input.value = "";
  toast(`${created.name} is ready to edit.`);
  await renderWorkspaces();
  await openWorkspace(created);
}

export async function startWorkspaceFromCard(workspace: Workspace): Promise<void> {
  const { body } = await api<Workspace>(`/api/v1/workspaces/${workspace.workspaceId}/start`, { method: "POST" });
  await renderWorkspaces();
  if (state.workspace?.workspaceId === body.workspaceId) applyWorkspace(body);
}

export async function stopWorkspaceFromCard(workspace: Workspace): Promise<void> {
  await api(`/api/v1/workspaces/${workspace.workspaceId}/stop`, { method: "POST" });
  await renderWorkspaces();
  if (state.workspace?.workspaceId === workspace.workspaceId) applyWorkspace({ ...workspace, state: "suspended" });
}

function applyWorkspace(workspace: Workspace): void {
  setState("workspace", workspace);
  setState("workbenchState", workspace.state);
  setState("workbenchStateClass", `status status-${workspace.state}`);
  renderPreviewLinks(workspace);
  void refreshPortStatuses().catch(() => undefined);
}

export async function openWorkspace(workspace: Workspace, navigate = true): Promise<void> {
  if (state.workspace?.workspaceId === workspace.workspaceId) {
    setState("workspace", workspace);
    setState("workbenchTitle", workspace.name);
    setState("workbenchState", workspace.state);
    setState("workbenchStateClass", `status status-${workspace.state}`);
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
    void autoReconnectTerminal().catch(() => undefined);
    return;
  }
  if ([...runtime.openDocuments.values()].some((opened) => opened.dirty)
      && state.workspace && state.workspace.workspaceId !== workspace.workspaceId
      && !window.confirm("Some files have unsaved changes. Leave this workspace?")) return;
  if (state.workspace?.workspaceId !== workspace.workspaceId) {
    flushLocalDrafts();
    if (!await stopDebugSession(true)) {
      const currentWorkspaceId = state.workspace?.workspaceId;
      if (!navigate && currentWorkspaceId) {
        history.replaceState({ workspaceId: currentWorkspaceId }, "", `#workspace/${currentWorkspaceId}`);
      }
      return;
    }
  }
  setState("workspace", workspace);
  clearDebugLaunchConfigurations();
  setState("debugTargets", []);
  setState("previewPort", null);
  setState("previewUrl", null);
  runtime.languageServices?.close();
  runtime.languageServices = new LanguageServices(
    workspace.workspaceId,
    (status) => setState("languageState", status),
    (diagnostics) => renderLanguageDiagnostics(diagnostics),
  );
  setState("languagePacks", []);
  setState("debugBreakpoints", []);
  setState("debugWatches", []);
  setState("debugWatchValues", {});
  setState("debugFrames", []);
  setState("debugVariables", []);
  setState("debugConsole", "Debugger output will appear here.");
  runtime.debugWatches = [];
  runtime.debugWatchValues.clear();
  runtime.openDocuments.forEach((opened) => opened.view.destroy());
  runtime.openDocuments.clear();
  const surface = byId("editor-surface");
  surface.replaceChildren();
  setState("tabs", []);
  setState("activePath", null);
  setState("activeFileLabel", "No file selected");
  setState("editorEmptyVisible", true);
  setState("taskRunId", null);
  runtime.taskRunId = null;
  clearWorkspaceSearch();
  resetGitPanel();
  void closeTerminal(false);
  setState("workbenchTitle", workspace.name);
  setState("rootName", workspace.name);
  setState("workbenchState", workspace.state);
  setState("workbenchStateClass", `status status-${workspace.state}`);
  setState("runtimeLabel", workspaceRunning() ? "Stop workspace" : "Start workspace");
  renderPreviewLinks(workspace);
  void refreshPortStatuses().catch(() => undefined);
  if (navigate) history.pushState({ workspaceId: workspace.workspaceId }, "", `#workspace/${workspace.workspaceId}`);
  setPage("workbench");
  setPanel("files");
  refreshGitSafely();
  void refreshDebugSessions().catch((failure) => toast(errorText(failure), "error"));
  await refreshFiles();
  // The store can paint the workbench before the new workspace is committed to
  // every reactive binding. A second pass guarantees starter files are visible.
  if (state.files.length === 0) {
    await new Promise<void>((resolve) => window.setTimeout(resolve, 50));
    await refreshFiles();
  }
  await refreshTasks();
  await refreshLanguagePacks();
  void refreshAgentToolStatus(true);
  await refreshTerminalSessions();
  void autoReconnectTerminal().catch(() => undefined);
  renderTabs();
}

export async function toggleWorkspace(): Promise<void> {
  const workspace = state.workspace;
  if (!workspace) return;
  const running = ["ready", "active", "idle"].includes(workspace.state);
  const action = running ? "stop" : "start";
  const { body } = await api<Workspace>(`/api/v1/workspaces/${workspace.workspaceId}/${action}`, { method: "POST" });
  setState("workspace", body);
  setState("workbenchState", body.state);
  setState("workbenchStateClass", `status status-${body.state}`);
  setState("runtimeLabel", running ? "Start workspace" : "Stop workspace");
  renderPreviewLinks(body);
  void refreshPortStatuses().catch(() => undefined);
  if (running) {
    runtime.languageServices?.close();
    setState("languageState", "Start the workspace to use language services");
  }
  await refreshTasks();
}

function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return "unknown size";
  return bytes >= 1024 * 1024
    ? `${(bytes / (1024 * 1024)).toFixed(0)} MB`
    : `${Math.ceil(bytes / 1024)} KB`;
}

export async function refreshAgentToolStatus(installWhenMissing: boolean): Promise<void> {
  if (runtime.agentToolPolling) return;
  runtime.agentToolPolling = true;
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
    setState("opencodeStatus", `OpenCode, ${errorText(failure)}`);
  } finally {
    runtime.agentToolPolling = false;
  }
}

async function readAgentToolStatus(): Promise<AgentToolStatus | null> {
  const { body } = await api<AgentToolStatus[]>("/api/v1/me/tools");
  return body.find((tool) => tool.id === "opencode") ?? null;
}

function renderAgentToolStatus(status: AgentToolStatus | null): void {
  if (!status) {
    setState("opencodeStatus", "OpenCode, unavailable");
    setState("opencodeLaunchLabel", "Open OpenCode");
    setState("opencodeLaunchDisabled", true);
    setState("opencodeUpdateHidden", true);
    setState("opencodeRemoveHidden", true);
    return;
  }
  const installed = status.status === "INSTALLED";
  const installing = status.status === "INSTALLING";
  let label: string;
  if (installing) label = `OpenCode, installing ${status.progressPercent}%`;
  else if (installed) label = `OpenCode ${status.version}, ready`;
  else if (status.status === "FAILED") label = `OpenCode, install failed: ${status.error}`;
  else if (status.status === "UNSUPPORTED") label = `OpenCode, unsupported ${status.architecture}`;
  else label = `OpenCode, ${Math.ceil(status.downloadSizeBytes / 1_000_000)} MB install`;
  setState("opencodeStatus", label);
  setState("opencodeLaunchLabel", installed ? "Open OpenCode" : status.status === "FAILED" ? "Retry install" : "Installing");
  setState("opencodeLaunchDisabled", installing || status.status === "UNSUPPORTED");
  setState("opencodeUpdateHidden", !installed);
  setState("opencodeUpdateDisabled", installing);
  setState("opencodeRemoveHidden", !installed);
  setState("opencodeRemoveDisabled", installing);
}

export async function launchOpenCode(): Promise<void> {
  const workspace = state.workspace;
  if (!workspace) return;
  const { body } = await api<AgentToolStatus[]>("/api/v1/me/tools");
  const toolStatus = body.find((tool) => tool.id === "opencode") ?? null;
  renderAgentToolStatus(toolStatus);
  if (toolStatus?.status !== "INSTALLED") {
    if (toolStatus?.status !== "INSTALLING") {
      if (toolStatus?.status === "NOT_INSTALLED" || toolStatus?.status === "FAILED") {
        const installed = await api<AgentToolStatus>("/api/v1/me/tools/opencode/install", { method: "POST" });
        renderAgentToolStatus(installed.body);
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

export async function updateOpenCode(): Promise<void> {
  const { body } = await api<AgentToolStatus>("/api/v1/me/tools/opencode/update", { method: "POST" });
  renderAgentToolStatus(body);
  void refreshAgentToolStatus(false);
}

export async function removeOpenCode(): Promise<void> {
  if (!window.confirm("Remove the per-user OpenCode executable? Its profile and provider sign-in will be kept.")) return;
  const { body } = await api<AgentToolStatus>("/api/v1/me/tools/opencode", { method: "DELETE" });
  renderAgentToolStatus(body);
}

export async function refreshTerminalSessions(): Promise<void> {
  const workspace = state.workspace;
  if (!workspace) return;
  const { body } = await api<TerminalSession[]>(`/api/v1/workspaces/${workspace.workspaceId}/terminals`);
  if (state.workspace?.workspaceId !== workspace.workspaceId) return;
  setState("terminalSessions", body);
  const detached = body.filter((session) => session.sessionId !== runtime.terminalSessionId);
  setState("terminalSelectHidden", detached.length === 0);
  setState("terminalSelectDisabled", detached.length === 0);
  setState("terminalReconnectHidden", detached.length === 0);
  setState("terminalReconnectDisabled", detached.length === 0);
  if (!runtime.terminalSessionId) {
    setState("terminalState", detached.length === 0
      ? "No session"
      : `${detached.length} detached session${detached.length === 1 ? "" : "s"}`);
  }
}

async function autoReconnectTerminal(): Promise<void> {
  const workspace = state.workspace;
  if (!workspace || runtime.terminalSessionId) return;
  const detached = state.terminalSessions.filter((session) => session.sessionId !== runtime.terminalSessionId);
  if (detached.length === 0) return;
  await closeTerminal(false);
  await connectTerminal(detached[0], workspace.workspaceId);
}

export async function refreshLanguagePacks(): Promise<void> {
  if (!state.workspace || !runtime.languageServices) return;
  const packs = await runtime.languageServices.refresh();
  setState("languagePacks", packs);
}

export async function toggleLanguagePack(pack: LanguagePack): Promise<void> {
  try {
    if (pack.selected) {
      await runtime.languageServices?.disable(pack.id);
      setState("languageState", "Language pack disabled");
    } else {
      setState("languageState", `Installing ${pack.name} (${formatBytes(pack.downloadBytes)})…`);
      await runtime.languageServices?.enable(pack.id);
      toast(`${pack.name} is ready for this workspace.`);
    }
    await refreshLanguagePacks();
    if (state.activePath) activateDocument(state.activePath);
  } catch (failure) {
    setState("languageState", "Language pack operation failed");
    toast(errorText(failure), "error");
  }
}

function renderLanguageDiagnostics(diagnostics: LanguageDiagnostic[]): void {
  setState("diagnostics", diagnostics);
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

export async function focusDiagnostic(diagnostic: LanguageDiagnostic): Promise<void> {
  await openDocument(diagnostic.path);
  activateDocument(diagnostic.path);
  const opened = runtime.openDocuments.get(diagnostic.path);
  if (!opened) return;
  const line = Math.min(diagnostic.line, opened.view.state.doc.lines);
  const target = opened.view.state.doc.line(Math.max(1, line));
  const position = Math.min(target.to, target.from + Math.max(0, diagnostic.column - 1));
  opened.view.dispatch({ selection: { anchor: position } });
  opened.view.focus();
}

export async function refreshTasks(): Promise<void> {
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
  byId("setup-output").textContent = setup.output;
  setState("setupOutput", setup.output);
  setState("setupOutputHidden", !setup.output);
  setState("tasks", tasksResult.body);
  const latestRun = latestRunResult.body;
  recordTaskRun(latestRun);
  renderTaskRun(latestRun);
  await refreshDebugTargets().catch(() => {
    setState("debugTargets", []);
    renderDebugTargets();
  });
  if (latestRun?.status === "RUNNING") void pollTaskRun(workspace.workspaceId, latestRun);
  if (setup.status === "RUNNING" && !runtime.setupPolling) void pollSetup(workspace.workspaceId);
}

export async function refreshDebugTargets(): Promise<void> {
  const workspace = state.workspace;
  if (!workspace) return;
  const { body } = await api<DebugTarget[]>(`/api/v1/workspaces/${workspace.workspaceId}/tasks/debug-targets`);
  if (state.workspace?.workspaceId !== workspace.workspaceId) return;
  setState("debugTargets", body);
  renderDebugTargets();
}

function renderDebugTargets(): void {
  const targets = state.debugTargets;
  const previous = state.debugAttachTarget;
  setState("debugAttachTarget", targets.length === 0 ? "" : targets.some((target) => target.id === previous) ? previous : targets[0].id);
  const selectedConfig = state.debugConfigs.find((configuration) => configuration.name === state.debugConfigName);
  if (selectedConfig?.request === "attach") {
    const matchingTarget = targets.find((target) => target.languageId === selectedConfig.languageId);
    if (matchingTarget) {
      setState("debugAttachTarget", matchingTarget.id);
      setState("debugConfigStatus", `${selectedConfig.name} selected. Attach to the matching running task.`);
    }
  }
  updateDebugControls();
}

async function pollSetup(workspaceId: string): Promise<void> {
  runtime.setupPolling = true;
  try {
    while (state.workspace?.workspaceId === workspaceId) {
      await new Promise((resolve) => window.setTimeout(resolve, 1000));
      const { body } = await api<SetupStatus>(`/api/v1/workspaces/${workspaceId}/setup`);
      renderSetupStatus(body);
      setState("setupOutput", body.output);
      setState("setupOutputHidden", !body.output);
      if (body.status !== "RUNNING") break;
    }
  } finally {
    runtime.setupPolling = false;
    if (state.workspace?.workspaceId === workspaceId) await refreshTasks();
  }
}

function renderSetupStatus(setup: SetupStatus): void {
  setState("setupState", setup.status);
  setState("setupSummary", setup.error || setupSummaries[setup.status] || "Template setup status is unavailable.");
  setState("setupRetryHidden", !(setup.status === "FAILED" || setup.status === "PENDING") || !workspaceRunning());
  setState("setupCorrelation", setup.correlationId ? `Setup reference ${setup.correlationId}` : "");
  setState("setupCorrelationHidden", !setup.correlationId);
}

export async function retrySetup(): Promise<void> {
  const workspace = state.workspace;
  if (!workspace) return;
  await api(`/api/v1/workspaces/${workspace.workspaceId}/setup/retry`, { method: "POST" });
  await refreshTasks();
}

export async function runTask(task: WorkspaceTask): Promise<void> {
  const workspace = state.workspace;
  if (!workspace) return;
  const { body: run } = await api<TaskRun>(
    `/api/v1/workspaces/${workspace.workspaceId}/tasks/${encodeURIComponent(task.id)}/run`,
    { method: "POST" },
  );
  recordTaskRun(run);
  renderTaskRun(run);
  void refreshPortStatuses().catch(() => undefined);
  await pollTaskRun(workspace.workspaceId, run);
}

function renderTaskRun(run: TaskRun | null): void {
  if (!run) {
    runtime.taskRunId = null;
    setState("taskStopHidden", true);
    setState("taskStopDisabled", false);
    setState("taskCorrelationHidden", true);
    setState("taskOutputHidden", true);
    setState("taskOutput", "");
    return;
  }
  runtime.taskRunId = run.id;
  setState("taskStopHidden", run.status !== "RUNNING");
  setState("taskStopDisabled", false);
  setState("taskCorrelationHidden", false);
  setState("taskCorrelation", `Task reference ${run.id}`);
  setState("taskOutputHidden", false);
  setState("taskOutput", `${run.label}, ${run.status}${run.exitCode === null ? "" : `, exit ${run.exitCode}`}\n${run.output}`);
  const output = byId<HTMLPreElement>("task-output");
  output.scrollTop = output.scrollHeight;
}

function recordTaskRun(run: TaskRun | null): void {
  if (!run) return;
  const existing = state.runHistory.findIndex((item) => item.id === run.id);
  if (existing >= 0) setState("runHistory", existing, run);
  else setState("runHistory", (items) => [run, ...items].slice(0, 10));
}

export function showRun(run: TaskRun): void {
  renderTaskRun(run);
}

async function pollTaskRun(workspaceId: string, initialRun: TaskRun): Promise<void> {
  if (runtime.taskPollingId === initialRun.id) return;
  runtime.taskPollingId = initialRun.id;
  let current = initialRun;
  try {
    while (state.workspace?.workspaceId === workspaceId
        && runtime.taskRunId === initialRun.id
        && current.status === "RUNNING") {
      await new Promise((resolve) => window.setTimeout(resolve, 1000));
      if (state.workspace?.workspaceId !== workspaceId || runtime.taskRunId !== initialRun.id) break;
      const { body } = await api<TaskRun>(`/api/v1/workspaces/${workspaceId}/task-runs/${initialRun.id}`);
      current = body;
      if (state.workspace?.workspaceId === workspaceId && runtime.taskRunId === initialRun.id) {
        recordTaskRun(current);
        renderTaskRun(current);
      }
      await refreshDebugTargets().catch(() => undefined);
      await refreshPortStatuses().catch(() => undefined);
    }
  } catch (failure) {
    if (state.workspace?.workspaceId === workspaceId && runtime.taskRunId === initialRun.id) {
      toast(`Task status could not be refreshed: ${errorText(failure)}`, "error");
    }
  } finally {
    if (runtime.taskPollingId === initialRun.id) runtime.taskPollingId = null;
  }
}

export async function stopTask(): Promise<void> {
  const workspace = state.workspace;
  const runId = runtime.taskRunId;
  if (!workspace || !runId) return;
  setState("taskStopDisabled", true);
  try {
    const { body: run } = await api<TaskRun>(
      `/api/v1/workspaces/${workspace.workspaceId}/task-runs/${runId}/stop`,
      { method: "POST" },
    );
    renderTaskRun(run);
  } finally {
    setState("taskStopDisabled", false);
    await refreshPortStatuses().catch(() => undefined);
  }
}

export async function refreshGit(): Promise<void> {
  const workspace = state.workspace;
  if (!workspace) return;
  runtime.gitHasStaged = false;
  setState("gitCommitDisabled", true);
  const { body } = await api<import("./store").GitStatus>(`/api/v1/workspaces/${workspace.workspaceId}/git`);
  if (state.workspace?.workspaceId !== workspace.workspaceId) return;
  setState("gitChanges", []);
  setState("gitDiffHidden", true);
  setState("gitFormHidden", !body.repository);
  setState("gitState", body.repository ? `${body.branch} · ${body.changes.length}` : "No repo");
  const staged = body.changes.some((change) => change.indexStatus !== " " && change.indexStatus !== "?");
  runtime.gitHasStaged = body.repository && staged;
  setState("gitCommitDisabled", !body.repository || !staged || !state.gitMessage.trim());
  if (!body.repository) {
    setState("gitChanges", [{ path: "No Git repository at the workspace root. Use the terminal to initialize or open one.", indexStatus: "", worktreeStatus: "" }]);
    return;
  }
  if (body.changes.length === 0) {
    setState("gitChanges", [{ path: "Working tree clean.", indexStatus: "", worktreeStatus: "" }]);
    return;
  }
  setState("gitChanges", body.changes);
}

function refreshGitSafely(): void {
  refreshGit().catch((failure) => toast(errorText(failure), "error"));
}

export { refreshGitSafely };

export function resetGitPanel(): void {
  runtime.gitHasStaged = false;
  setState("gitCommitDisabled", true);
  setState("gitState", "Git");
  setState("gitChanges", []);
  setState("gitDiffHidden", true);
  setState("gitMessage", "");
}

export async function loadGitDiff(path: string): Promise<void> {
  const workspace = state.workspace;
  if (!workspace) return;
  const query = new URLSearchParams({ path });
  const { body } = await api<import("./store").GitDiff>(`/api/v1/workspaces/${workspace.workspaceId}/git/diff?${query}`);
  if (state.workspace?.workspaceId !== workspace.workspaceId) return;
  setState("gitDiff", body.content || "No diff to display.");
  setState("gitDiffHidden", false);
}

export async function updateGitPath(operation: "stage" | "unstage", path: string): Promise<void> {
  const workspace = state.workspace;
  if (!workspace) return;
  await api(`/api/v1/workspaces/${workspace.workspaceId}/git/${operation}`, {
    method: "POST",
    body: JSON.stringify({ path }),
  });
  refreshGitSafely();
}

export async function commitGitChanges(event: SubmitEvent): Promise<void> {
  event.preventDefault();
  const workspace = state.workspace;
  const message = state.gitMessage.trim();
  if (!workspace || !message) return;
  const { body } = await api<{ commit: string; summary: string }>(
    `/api/v1/workspaces/${workspace.workspaceId}/git/commit`,
    { method: "POST", body: JSON.stringify({ message }) },
  );
  setState("gitMessage", "");
  toast(`Committed ${body.commit}.`);
  refreshGitSafely();
}

export function updateGitMessage(value: string): void {
  setState("gitMessage", value);
  setState("gitCommitDisabled", !runtime.gitHasStaged || !value.trim());
}

export function renderPreviewLinks(workspace: Workspace): void {
  setState("refreshPortsHidden", workspace.ports.length === 0);
  renderPreviewPanelState();
}

function renderPreviewPanelState(): void {
  const workspace = state.workspace;
  const current = workspace?.ports.find((port) => port.port === state.previewPort && port.listening === true);
  const selectOptions = [{ value: "", label: "Select a listening port", disabled: true }, ...(workspace?.ports ?? []).map((port) => ({
    value: String(port.port),
    label: `:${port.port}, ${port.listening === true ? "listening" : port.listening === false ? "not listening" : "not checked"}`,
    disabled: port.listening !== true,
  }))];
  const portSelect = byId<HTMLSelectElement>("preview-port-select");
  if (portSelect) {
    portSelect.replaceChildren(...selectOptions.map((option) => {
      const element = document.createElement("option");
      element.value = option.value;
      element.textContent = option.label;
      element.disabled = option.disabled;
      return element;
    }));
    portSelect.value = current ? String(current.port) : "";
    portSelect.disabled = !workspace?.ports.some((port) => port.listening === true);
  }
  setState("previewSelectDisabled", !workspace?.ports.some((port) => port.listening === true));
  if (!current) {
    const selected = workspace?.ports.find((port) => port.port === state.previewPort);
    setState("previewState", selected
      ? `Port :${selected.port} is not listening.`
      : "Select a listening port to open its preview.");
    setState("previewFrameHidden", true);
    setState("previewEmptyHidden", false);
    setState("previewReloadDisabled", true);
    setState("previewNewTabDisabled", true);
    return;
  }
  setState("previewState", `Listening on :${current.port}`);
  setState("previewEmptyHidden", true);
  setState("previewFrameHidden", false);
  setState("previewUrl", current.url);
  const frame = byId<HTMLIFrameElement>("preview-frame");
  if (frame && (frame.src !== current.url || frame.src === window.location.href)) frame.src = current.url;
  setState("previewReloadDisabled", false);
  setState("previewNewTabDisabled", false);
}

export function openPreview(port: PortEntry): void {
  if (port.listening !== true) {
    toast(`Port ${port.port} is not listening yet. Start the project task and check ports again.`, "error");
    return;
  }
  setState("previewPort", port.port);
  setState("previewUrl", null);
  setPanel("preview");
  renderPreviewPanelState();
}

export function selectPreviewPort(portNumber: number): void {
  const port = state.workspace?.ports.find((candidate) => candidate.port === portNumber);
  if (port) openPreview(port);
}

export function reloadPreview(): void {
  const frame = byId<HTMLIFrameElement>("preview-frame");
  if (frame && state.previewUrl) frame.src = state.previewUrl;
}

export function resizePreviewStart(event: PointerEvent): void {
  const dock = byId<HTMLElement>("preview-dock");
  runtime.resizingPreview = true;
  runtime.previewStartY = event.clientY;
  runtime.previewStartHeight = dock.getBoundingClientRect().height;
  window.addEventListener("pointermove", resizePreviewMove);
  window.addEventListener("pointerup", resizePreviewEnd);
}
function resizePreviewMove(event: PointerEvent): void {
  if (!runtime.resizingPreview) return;
  const dock = byId<HTMLElement>("preview-dock");
  dock.style.height = `${Math.max(120, runtime.previewStartHeight + runtime.previewStartY - event.clientY)}px`;
}
function resizePreviewEnd(): void {
  runtime.resizingPreview = false;
  window.removeEventListener("pointermove", resizePreviewMove);
  window.removeEventListener("pointerup", resizePreviewEnd);
}

export async function findPreviewPorts(): Promise<void> {
  const workspace = state.workspace;
  if (!workspace) return;
  let candidates: CandidatePort[] = [];
  try {
    const { body } = await api<Array<{ port: number; sources?: string[] }>>(
      `/api/v1/workspaces/${workspace.workspaceId}/ports/candidates`,
    );
    candidates = (body ?? []).map((item) => ({ port: item.port, sources: item.sources ?? [] }));
  } catch {
    candidates = (workspace.ports ?? []).map((port) => ({ port: port.port, sources: ["tracked"] }));
  }
  setState("previewCandidates", candidates.length === 0 ? [] : candidates);
}

export async function registerPreviewPort(port: number): Promise<void> {
  const workspace = state.workspace;
  if (!workspace || !window.confirm(`Register and open preview on port ${port}?`)) return;
  try {
    const { body } = await api<PortEntry>(
      `/api/v1/workspaces/${workspace.workspaceId}/ports`,
      { method: "POST", body: JSON.stringify({ port, protocol: "http" }) },
    );
    await refreshPortStatuses();
    openPreview(body);
    toast(`Registered port ${port}.`);
  } catch (failure) {
    toast(errorText(failure), "error");
  }
}

export async function refreshPortStatuses(): Promise<void> {
  const workspace = state.workspace;
  if (!workspace) return;
  const { body: ports } = await api<PortEntry[]>(`/api/v1/workspaces/${workspace.workspaceId}/ports`);
  if (state.workspace?.workspaceId !== workspace.workspaceId) return;
  setState("workspace", "ports", ports);
  renderPreviewLinks(state.workspace);
}

export async function refreshFiles(path = ".", parent?: HTMLElement): Promise<void> {
  const workspace = state.workspace;
  if (!workspace) return;
  const query = new URLSearchParams({ path });
  const { body: entries } = await api<FileEntry[]>(`/api/v1/workspaces/${workspace.workspaceId}/files?${query}`);
  if (!parent) {
    setState("files", entries);
    return;
  }
  parent.replaceChildren(...entries.map((entry) => renderFile(entry)));
}

export function renderFile(entry: FileEntry): HTMLLIElement {
  const item = document.createElement("li");
  item.className = entry.directory ? "tree-directory" : "tree-file";
  const row = document.createElement("div");
  row.className = "tree-row";
  const toggle = document.createElement("button");
  toggle.type = "button";
  toggle.className = "tree-item";
  toggle.textContent = entry.directory ? `${entry.name} ▸` : entry.name;
  toggle.title = entry.path;
  if (entry.directory) {
    toggle.setAttribute("aria-expanded", "false");
    toggle.addEventListener("click", () => {
      const existing = item.querySelector(":scope > ul");
      if (existing) {
        existing.remove();
        toggle.textContent = `${entry.name} ▸`;
        toggle.setAttribute("aria-expanded", "false");
        return;
      }
      const children = document.createElement("ul");
      toggle.textContent = `${entry.name} ▾`;
      toggle.setAttribute("aria-expanded", "true");
      item.append(children);
      refreshFiles(entry.path, children).catch((failure) => toast(errorText(failure), "error"));
    });
  } else {
    toggle.addEventListener("click", () => selectAndOpenFile(entry.path));
  }
  row.append(toggle);
  item.append(row);
  return item;
}

export function selectAndOpenFile(path: string): void {
  setState("selectedPath", path);
  setState("downloadDisabled", false);
  openDocument(path).catch((failure) => {
    const message = errorText(failure);
    if (message.includes("binary files cannot be opened") || message.includes("too large to open")) {
      toast("This file cannot be edited as text. Use Download selected to download it.", "error");
      return;
    }
    toast(message, "error");
  });
}

export function downloadSelected(): void {
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

async function languageFor(path: string): Promise<any[]> {
  const lower = path.toLowerCase();
  if (lower.endsWith(".html") || lower.endsWith(".htm")) return [(await import("@codemirror/lang-html")).html()];
  if (lower.endsWith(".json") || lower.endsWith(".jsonc")) return [(await import("@codemirror/lang-json")).json()];
  if (lower.endsWith(".py")) return [(await import("@codemirror/lang-python")).python()];
  if (/\.(js|jsx|ts|tsx|mjs|cjs)$/.test(lower)) {
    return [(await import("@codemirror/lang-javascript")).javascript({ typescript: /\.(ts|tsx)$/.test(lower) })];
  }
  return [];
}

export async function openDocument(path: string): Promise<void> {
  const existing = runtime.openDocuments.get(path);
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
    toggleDebugBreakpoint(path, line).catch((failure) => toast(errorText(failure), "error"));
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
        runtime.languageServices?.changeDocument(path, update.state.doc.toString());
        syncTab(path);
        updateSaveState();
      }),
    ],
  });
  wrapper.hidden = true;
  effect(() => { wrapper.hidden = state.activePath !== path; });
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
  runtime.openDocuments.set(path, documentState);
  setState("tabs", (tabs) => [...tabs, { path, dirty: documentState.dirty, recoveredDraft }]);
  refreshBreakpointGutter(wrapper, path);
  setState("selectedPath", path);
  setState("downloadDisabled", false);
  activateDocument(path);
  if (window.matchMedia("(max-width: 900px)").matches) setPanel("editor");
}

function syncTab(path: string): void {
  const opened = runtime.openDocuments.get(path);
  if (!opened) return;
  setState("tabs", (tabs) => tabs.map((tab: TabInfo) => tab.path === path
    ? { ...tab, dirty: opened.dirty, recoveredDraft: opened.recoveredDraft }
    : tab));
}

export function activateDocument(path: string): void {
  const next = runtime.openDocuments.get(path);
  if (!next) return;
  setState("activePath", path);
  setState("activeFileLabel", path);
  setState("editorEmptyVisible", false);
  setState("statusLanguage", languageName(path));
  setState("debugBreakpointPath", path);
  setState("debugBreakpointLines", (state.debugBreakpoints.find((entry) => entry.path === path)?.lines ?? []).join(", "));
  if (state.workspace && !workspaceRunning()) {
    runtime.languageServices?.closeDocument(path);
    setState("languageState", "Start the workspace to use language services");
  } else {
    runtime.languageServices?.openDocument(languageIdForPath(path), path, next.view.state.doc.toString());
  }
  refreshBreakpointGutter(next.wrapper, path);
  renderTabs();
  updateSaveState();
  window.requestAnimationFrame(() => next.view.requestMeasure());
}

function languageName(path: string): string {
  const extension = path.split(".").at(-1)?.toLowerCase();
  if (!extension || extension === path.toLowerCase()) return "Plain text";
  const names: Record<string, string> = {
    py: "Python", md: "Markdown", ts: "TypeScript", tsx: "TypeScript", js: "JavaScript",
    jsx: "JavaScript", json: "JSON", html: "HTML", htm: "HTML", css: "CSS",
    java: "Java", rs: "Rust", go: "Go", c: "C", h: "C", cpp: "C++", cc: "C++",
  };
  return names[extension] ?? extension;
}

function renderTabs(): void {
  setState("tabs", (tabs) => tabs.map((tab: TabInfo) => {
    const opened = runtime.openDocuments.get(tab.path);
    return opened ? { ...tab, dirty: opened.dirty, recoveredDraft: opened.recoveredDraft } : tab;
  }));
}

export function closeTab(path: string): void {
  const opened = runtime.openDocuments.get(path);
  if (!opened) return;
  if (opened.dirty && !window.confirm(`Discard unsaved changes to ${path}?`)) return;
  if (opened.dirty && state.workspace) clearLocalDraft(state.workspace.workspaceId, path);
  opened.view.destroy();
  opened.wrapper.remove();
  runtime.openDocuments.delete(path);
  setState("tabs", (tabs) => tabs.filter((tab: TabInfo) => tab.path !== path));
  if (state.activePath === path) {
    setState("activePath", null);
    const replacement = runtime.openDocuments.keys().next().value as string | undefined;
    if (replacement) activateDocument(replacement);
    else {
      runtime.languageServices?.closeDocument(path);
      setState("activeFileLabel", "No file selected");
      setState("editorEmptyVisible", true);
      updateSaveState();
    }
  } else updateSaveState();
}

function updateSaveState(): void {
  const current = state.activePath ? runtime.openDocuments.get(state.activePath) : undefined;
  setState("saveDisabled", !current || !current.dirty);
  setState("saveState", current?.dirty
    ? current.recoveredDraft ? "Recovered local draft, unsaved" : "Unsaved changes, saved locally"
    : current ? "Saved" : "");
  setState("saveStateClass", current?.dirty ? "save-state dirty" : "save-state");
  setState("conflictVisible", Boolean(current && current.conflictRemoteVersion !== null && current.conflictRemoteContent !== null));
}

export function useServerVersion(): void {
  const workspace = state.workspace;
  const opened = state.activePath ? runtime.openDocuments.get(state.activePath) : undefined;
  if (!workspace || !opened || opened.conflictRemoteVersion === null || opened.conflictRemoteContent === null) return;
  if (opened.dirty && !window.confirm(`Discard your local edits to ${opened.path} and use the server version?`)) return;
  opened.version = opened.conflictRemoteVersion;
  opened.savedContent = opened.conflictRemoteContent;
  opened.conflictRemoteVersion = null;
  opened.conflictRemoteContent = null;
  opened.recoveredDraft = false;
  opened.view.dispatch({ changes: { from: 0, to: opened.view.state.doc.length, insert: opened.savedContent } });
  opened.dirty = false;
  clearLocalDraft(workspace.workspaceId, opened.path);
  renderTabs();
  updateSaveState();
  toast("Loaded the latest server version.");
}

export function keepLocalVersion(): void {
  const workspace = state.workspace;
  const opened = state.activePath ? runtime.openDocuments.get(state.activePath) : undefined;
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

export async function saveActiveDocument(): Promise<void> {
  const workspace = state.workspace;
  const path = state.activePath;
  const opened = path ? runtime.openDocuments.get(path) : undefined;
  if (!workspace || !path || !opened || !opened.dirty) return;
  try {
    const query = new URLSearchParams({ path });
    const { body, response } = await api<DocumentBody>(
      `/api/v1/workspaces/${workspace.workspaceId}/files/document?${query}`,
      {
        method: "PUT",
        headers: { "If-Match": opened.version },
        body: JSON.stringify({ content: opened.view.state.doc.toString() }),
      },
    );
    opened.version = response.headers.get("ETag") || body.version;
    opened.savedContent = body.content;
    opened.dirty = false;
    opened.recoveredDraft = false;
    clearLocalDraft(workspace.workspaceId, path);
    runtime.languageServices?.saveDocument(path);
    renderTabs();
    updateSaveState();
    toast("Saved.");
    refreshGitSafely();
  } catch (failure) {
    if (failure instanceof Error && failure.message.startsWith("412")) {
      const latest = await api<DocumentBody>(
        `/api/v1/workspaces/${workspace.workspaceId}/files/document?${new URLSearchParams({ path })}`,
      );
      if (state.workspace?.workspaceId !== workspace.workspaceId || runtime.openDocuments.get(path) !== opened) return;
      opened.conflictRemoteVersion = latest.response.headers.get("ETag") || latest.body.version;
      opened.conflictRemoteContent = latest.body.content;
      updateSaveState();
      setState("saveState", "Changed on disk — reload before saving");
      setState("saveStateClass", "save-state conflict");
      toast("Your local edits are preserved. Choose the server copy or keep your edits before saving again.", "error");
      return;
    }
    throw failure;
  }
}

export async function createFile(): Promise<void> {
  const workspace = state.workspace;
  if (!workspace) return;
  const path = window.prompt("New file path relative to the workspace root");
  if (!path?.trim()) return;
  const query = new URLSearchParams({ path: path.trim() });
  await api(`/api/v1/workspaces/${workspace.workspaceId}/files/document?${query}`, {
    method: "PUT",
    headers: { "If-None-Match": "*" },
    body: JSON.stringify({ content: "" }),
  });
  await refreshFiles();
  refreshGitSafely();
  await openDocument(path.trim());
}

export async function createFolder(): Promise<void> {
  const workspace = state.workspace;
  if (!workspace) return;
  const path = window.prompt("New folder path relative to the workspace root");
  if (!path?.trim()) return;
  await api(`/api/v1/workspaces/${workspace.workspaceId}/files/directory`, {
    method: "POST",
    body: JSON.stringify({ path: path.trim() }),
  });
  await refreshFiles();
  refreshGitSafely();
}

export async function renameSelected(): Promise<void> {
  const workspace = state.workspace;
  if (!workspace || !state.selectedPath) { toast("Select a file or folder first.", "error"); return; }
  const newPath = window.prompt("New path relative to the workspace root", state.selectedPath);
  if (!newPath?.trim() || newPath === state.selectedPath) return;
  await api(`/api/v1/workspaces/${workspace.workspaceId}/files/rename`, {
    method: "POST",
    body: JSON.stringify({ path: state.selectedPath, newPath: newPath.trim() }),
  });
  const previousPath = state.selectedPath;
  const renamedPath = newPath.trim().replaceAll("\\", "/");
  for (const [oldPath, opened] of [...runtime.openDocuments.entries()]) {
    if (oldPath !== previousPath && !oldPath.startsWith(`${previousPath}/`)) continue;
    const updatedPath = `${renamedPath}${oldPath.substring(previousPath.length)}`;
    runtime.openDocuments.delete(oldPath);
    opened.path = updatedPath;
    runtime.openDocuments.set(updatedPath, opened);
    if (state.activePath === oldPath) setState("activePath", updatedPath);
  }
  setState("selectedPath", renamedPath);
  setState("tabs", (tabs) => tabs.map((tab: TabInfo) => ({
    ...tab,
    path: tab.path === previousPath || tab.path.startsWith(`${previousPath}/`)
      ? `${renamedPath}${tab.path.substring(previousPath.length)}`
      : tab.path,
  })));
  await refreshFiles();
  refreshGitSafely();
  if (state.activePath) setState("activeFileLabel", state.activePath);
}

export async function deleteSelected(): Promise<void> {
  const workspace = state.workspace;
  const path = state.selectedPath;
  if (!workspace || !path) { toast("Select a file or empty folder first.", "error"); return; }
  if (!window.confirm(`Delete ${path}? Folders must be empty.`)) return;
  await api(`/api/v1/workspaces/${workspace.workspaceId}/files?${new URLSearchParams({ path })}`, { method: "DELETE" });
  const opened = runtime.openDocuments.get(path);
  if (opened) {
    runtime.languageServices?.closeDocument(path);
    opened.view.destroy();
    opened.wrapper.remove();
    runtime.openDocuments.delete(path);
    setState("tabs", (tabs) => tabs.filter((tab: TabInfo) => tab.path !== path));
    if (state.activePath === path) setState("activePath", null);
  }
  setState("selectedPath", null);
  setState("downloadDisabled", true);
  await refreshFiles();
  refreshGitSafely();
  if (!state.activePath) setState("editorEmptyVisible", true);
  updateSaveState();
}

export async function searchWorkspace(query: string): Promise<void> {
  const workspace = state.workspace;
  if (!workspace) return;
  const params = new URLSearchParams({ q: query, limit: "50" });
  const { body } = await api<SearchResult[]>(`/api/v1/workspaces/${workspace.workspaceId}/files/search?${params}`);
  if (state.workspace?.workspaceId !== workspace.workspaceId || state.searchQuery.trim() !== query) return;
  setState("searchResults", body);
  setState("searchVisible", true);
}

export function scheduleWorkspaceSearch(query: string, runImmediately = false): void {
  if (runtime.searchTimer !== null) window.clearTimeout(runtime.searchTimer);
  setState("searchQuery", query);
  if (query.length < 2) {
    setState("searchResults", []);
    setState("searchVisible", false);
    return;
  }
  const workspaceId = state.workspace?.workspaceId;
  runtime.searchTimer = window.setTimeout(
    () => searchWorkspace(query).catch((failure) => {
      if (state.workspace?.workspaceId === workspaceId && state.searchQuery.trim() === query) {
        toast(errorText(failure), "error");
      }
    }),
    runImmediately ? 0 : 250,
  );
}

export function clearWorkspaceSearch(): void {
  if (runtime.searchTimer !== null) window.clearTimeout(runtime.searchTimer);
  runtime.searchTimer = null;
  setState("searchQuery", "");
  setState("searchResults", []);
  setState("searchVisible", false);
  setState("downloadDisabled", true);
}

export function openQuickOpen(): void {
  setState("quickOpenVisible", true);
  setState("quickOpenQuery", "");
  byId("quick-open-palette").hidden = false;
  renderQuickOpen("");
  const input = byId<HTMLInputElement>("quick-open-input");
  input.value = "";
  input.focus();
}

export function closeQuickOpen(): void {
  setState("quickOpenVisible", false);
}

export async function renderQuickOpen(query: string): Promise<void> {
  setState("quickOpenQuery", query);
  const panels: Panel[] = ["files", "editor", "run", "languages", "debug", "git", "terminal", "preview"];
  const trimmed = query.trim();
  if (trimmed.length < 2) {
    const results: QuickResult[] = panels.map((panel) => ({
      label: `Panel: ${panel}`, detail: "", kind: "panel", value: panel,
    }));
    for (const opened of runtime.openDocuments.keys()) {
      results.push({ label: `Open: ${opened}`, detail: "", kind: "document", value: opened });
    }
    setState("quickOpenResults", results);
    return;
  }
  const workspace = state.workspace;
  if (!workspace) return;
  try {
    const params = new URLSearchParams({ q: trimmed, limit: "30" });
    const { body } = await api<SearchResult[]>(`/api/v1/workspaces/${workspace.workspaceId}/files/search?${params}`);
    const results: QuickResult[] = body.map((match) => ({
      label: `${match.path}${match.line ? `:${match.line}` : ""}`,
      detail: match.preview,
      kind: "match",
      value: match.path,
    }));
    if (results.length === 0) results.push({ label: "No matching files.", detail: "", kind: "empty", value: "" });
    setState("quickOpenResults", results);
  } catch (failure) {
    toast(errorText(failure), "error");
  }
}

export function chooseQuickResult(result: QuickResult): void {
  if (result.kind === "empty") return;
  if (result.kind === "panel") setPanel(result.value as Panel);
  else selectAndOpenFile(result.value);
  closeQuickOpen();
}

function setDebugState(message: string): void {
  setState("debugState", message.replace(/ · /g, " ").toUpperCase());
}

export function setDebugLanguage(value: string): void {
  setState("debugLanguage", value);
  configureDebugLanguageFields();
}
export function setDebugProgram(value: string): void { setState("debugProgram", value); }
export function setDebugMainClass(value: string): void { setState("debugMainClass", value); }
export function setDebugWorkingDirectory(value: string): void { setState("debugWorkingDirectory", value); }
export function setDebugArguments(value: string): void { setState("debugArguments", value); }
export function setDebugStopOnEntry(value: boolean): void { setState("debugStopOnEntry", value); }
export function setDebugAttachTarget(value: string): void {
  setState("debugAttachTarget", value);
  updateDebugControls();
}
export function setDebugBreakpointPath(value: string): void { setState("debugBreakpointPath", value); }
export function setDebugBreakpointLines(value: string): void { setState("debugBreakpointLines", value); }
export function setDebugWatchExpression(_value: string): void { /* expression is read from the form on submit */ }

function configureDebugLanguageFields(): void {
  const isJava = state.debugLanguage === "java";
  setState("debugProgramFieldHidden", isJava);
  setState("debugMainClassFieldHidden", !isJava);
}

export async function loadDebugLaunchConfigurations(): Promise<void> {
  const workspace = state.workspace;
  if (!workspace) return;
  clearDebugLaunchConfigurations();
  const query = new URLSearchParams({ path: ".vscode/launch.json" });
  const { body } = await api<DocumentBody>(
    `/api/v1/workspaces/${workspace.workspaceId}/files/document?${query}`,
  );
  if (state.workspace?.workspaceId !== workspace.workspaceId) return;
  const configurations = parseDebugLaunchConfigurations(body.content);
  setState("debugConfigs", configurations);
  setState("debugConfigFieldHidden", false);
  setState("debugConfigSelectHidden", false);
  setState("debugConfigStatus", `${configurations.length} supported launch configuration${configurations.length === 1 ? "" : "s"} loaded.`);
  setState("debugConfigName", configurations[0].name);
  applyDebugLaunchConfiguration(configurations[0].name);
  updateDebugControls();
}

export function chooseDebugLaunchConfiguration(name: string): void {
  setState("debugConfigName", name);
  applyDebugLaunchConfiguration(name);
}

function applyDebugLaunchConfiguration(name: string): void {
  const configuration = state.debugConfigs.find((item) => item.name === name);
  if (!configuration) return;
  setState("debugLanguage", configuration.languageId);
  if (configuration.request === "attach") {
    const matchingTarget = state.debugTargets.find((target) => target.languageId === configuration.languageId);
    setState("debugAttachTarget", matchingTarget?.id ?? "");
    setState("debugConfigStatus", matchingTarget
      ? `${configuration.name} selected. Attach to the matching running task.`
      : `${configuration.name} selected. Start a debuggable ${configuration.languageId} task first.`);
    configureDebugLanguageFields();
    updateDebugControls();
    return;
  }
  setState("debugProgram", configuration.program ?? "");
  setState("debugMainClass", configuration.mainClass ?? "");
  setState("debugWorkingDirectory", configuration.workingDirectory);
  setState("debugArguments", JSON.stringify(configuration.arguments));
  setState("debugStopOnEntry", configuration.stopOnEntry);
  setState("debugConfigStatus", `${configuration.name} launch settings loaded.`);
  configureDebugLanguageFields();
  updateDebugControls();
}

function clearDebugLaunchConfigurations(): void {
  setState("debugConfigs", []);
  setState("debugConfigName", "");
  setState("debugConfigSelectHidden", true);
  setState("debugConfigFieldHidden", true);
  setState("debugConfigStatus", "");
}

function clearDebugInspectors(): void {
  runtime.debugThreadId = null;
  runtime.debugFrameId = null;
  runtime.debugPaused = false;
  runtime.debugFrames = [];
  runtime.debugVariables = [];
  runtime.debugWatchValues.clear();
  setState("debugFrames", []);
  setState("debugVariables", []);
  setState("debugWatchValues", {});
  updateDebugControls();
}

function appendDebugConsole(value: string): void {
  const current = state.debugConsole;
  const base = current === "Debugger output will appear here." ? "" : current;
  setState("debugConsole", `${base}${value}`.slice(-100_000));
  const output = byId<HTMLPreElement>("debug-console");
  if (output) output.scrollTop = output.scrollHeight;
}

function rejectDebugRequests(message: string): void {
  for (const [sequence, pending] of runtime.dapPending) {
    window.clearTimeout(pending.timer);
    pending.reject(new Error(message));
    runtime.dapPending.delete(sequence);
  }
  for (const waiters of runtime.dapEventWaiters.values()) {
    for (const waiter of waiters) {
      window.clearTimeout(waiter.timer);
      waiter.reject(new Error(message));
    }
  }
  runtime.dapEventWaiters.clear();
}

function openDebugSocket(session: DebugSession): Promise<void> {
  const workspaceId = state.workspace?.workspaceId;
  if (!workspaceId || workspaceId !== session.workspaceId) {
    return Promise.reject(new Error("The debug session belongs to a different workspace."));
  }
  if (runtime.debugSocket?.readyState === WebSocket.OPEN) return Promise.resolve();
  const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
  const socket = new WebSocket(
    `${protocol}//${window.location.host}/api/v1/workspaces/${encodeURIComponent(workspaceId)}/debug-sessions/${encodeURIComponent(session.id)}/stream`,
  );
  runtime.debugSocket = socket;
  setDebugState("Connecting");
  updateDebugControls();
  return new Promise<void>((resolve, reject) => {
    let opened = false;
    socket.addEventListener("open", () => {
      opened = true;
      if (runtime.debugSocket !== socket) {
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
      if (runtime.debugSocket !== socket) return;
      runtime.debugSocket = null;
      rejectDebugRequests("The debug stream disconnected.");
      updateDebugControls();
      if (runtime.debugSession) setDebugState("Disconnected, reconnect available");
      if (!opened) reject(new Error("The debug stream closed before connecting."));
    });
  });
}

function sendDap(message: Record<string, unknown>): void {
  const socket = runtime.debugSocket;
  if (!socket || socket.readyState !== WebSocket.OPEN) {
    throw new Error("Reconnect the debug stream before sending debugger commands.");
  }
  socket.send(JSON.stringify(message));
}

function dapRequest(command: string, args: Record<string, unknown> = {}): Promise<DapMessage> {
  const sequence = ++runtime.debugSequence;
  return new Promise<DapMessage>((resolve, reject) => {
    const timer = window.setTimeout(() => {
      runtime.dapPending.delete(sequence);
      reject(new Error(`The debugger did not answer ${command}.`));
    }, 30_000);
    runtime.dapPending.set(sequence, { resolve, reject, timer });
    try {
      sendDap({ seq: sequence, type: "request", command, arguments: args });
    } catch (failure) {
      window.clearTimeout(timer);
      runtime.dapPending.delete(sequence);
      reject(failure instanceof Error ? failure : new Error("Could not send debugger request."));
    }
  });
}

function waitForDapEvent(name: string): Promise<void> {
  if (runtime.dapEarlyEvents.delete(name)) return Promise.resolve();
  return new Promise<void>((resolve, reject) => {
    const timer = window.setTimeout(() => {
      const waiters = runtime.dapEventWaiters.get(name) ?? [];
      runtime.dapEventWaiters.set(name, waiters.filter((item) => item.timer !== timer));
      reject(new Error(`The debug adapter did not send the ${name} event.`));
    }, 15_000);
    const waiters = runtime.dapEventWaiters.get(name) ?? [];
    waiters.push({ resolve, reject, timer });
    runtime.dapEventWaiters.set(name, waiters);
  });
}

function resolveDapEvent(name: string): void {
  const waiters = runtime.dapEventWaiters.get(name);
  const waiter = waiters?.shift();
  if (!waiter) {
    runtime.dapEarlyEvents.add(name);
    return;
  }
  window.clearTimeout(waiter.timer);
  waiter.resolve();
  if (waiters?.length === 0) runtime.dapEventWaiters.delete(name);
}

function handleDapMessage(socket: WebSocket, payload: string): void {
  if (runtime.debugSocket !== socket) return;
  let message: DapMessage;
  try {
    message = JSON.parse(payload) as DapMessage;
    if (!message || typeof message.type !== "string") throw new Error("invalid DAP message");
  } catch {
    socket.close(1007, "invalid debugger message");
    return;
  }
  if (message.type === "response" && typeof message.request_seq === "number") {
    const pending = runtime.dapPending.get(message.request_seq);
    if (!pending) return;
    runtime.dapPending.delete(message.request_seq);
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
      runtime.debugThreadId = typeof threadId === "number" ? threadId : null;
      runtime.debugPaused = true;
      setDebugState("Paused");
      updateDebugControls();
      void refreshDebugStack().catch((failure) => appendDebugConsole(`\n[debug] ${errorText(failure)}\n`));
    }
    if (message.event === "continued") {
      clearDebugInspectors();
      runtime.debugPaused = false;
      const threadId = message.body?.threadId;
      if (typeof threadId === "number") runtime.debugThreadId = threadId;
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
        seq: ++runtime.debugSequence,
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

export async function startDebugSession(attachTargetId?: string): Promise<void> {
  const workspace = state.workspace;
  if (!workspace) return;
  if (runtime.debugSession && !await stopDebugSession(true)) return;
  const requestMode: "launch" | "attach" = attachTargetId ? "attach" : "launch";
  let requestBody: Record<string, unknown>;
  if (attachTargetId) {
    requestBody = { targetId: attachTargetId };
  } else {
    const languageId = state.debugLanguage;
    let argumentsList: unknown;
    try {
      argumentsList = JSON.parse(state.debugArguments || "[]") as unknown;
    } catch {
      throw new Error("Arguments must be a JSON array of strings, such as [\"--inspect\"].");
    }
    if (!Array.isArray(argumentsList) || argumentsList.some((value) => typeof value !== "string")) {
      throw new Error("Arguments must be a JSON array of strings.");
    }
    requestBody = {
      languageId,
      program: languageId === "java" ? null : state.debugProgram.trim(),
      mainClass: languageId === "java" ? state.debugMainClass.trim() : null,
      arguments: argumentsList,
      workingDirectory: state.debugWorkingDirectory.trim() || ".",
      stopOnEntry: state.debugStopOnEntry,
    };
  }
  const { body: session } = await api<DebugSession>(
    `/api/v1/workspaces/${workspace.workspaceId}/debug-sessions${attachTargetId ? "/attach" : ""}`,
    { method: "POST", body: JSON.stringify(requestBody) },
  );
  if (state.workspace?.workspaceId !== workspace.workspaceId) {
    await api(`/api/v1/workspaces/${workspace.workspaceId}/debug-sessions/${session.id}/stop`, { method: "POST" }).catch(() => undefined);
    return;
  }
  runtime.debugSession = session;
  if (attachTargetId) {
    setState("debugLanguage", session.languageId);
    configureDebugLanguageFields();
  }
  runtime.debugInitialized = false;
  runtime.debugSequence = 0;
  runtime.dapEarlyEvents.clear();
  clearDebugInspectors();
  setState("debugConsole", "");
  setState("debugBreakpointPath", state.activePath ?? "");
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
    const startResponse = dapRequest(requestMode, {});
    await waitForDapEvent("initialized");
    runtime.debugInitialized = true;
    const selectedBreakpointPath = state.debugBreakpointPath.trim() || state.activePath;
    await applyDebugBreakpoints(false);
    await applyStoredDebugBreakpoints(selectedBreakpointPath);
    if (initialize.body?.supportsConfigurationDoneRequest !== false) {
      await dapRequest("configurationDone", {});
    }
    await startResponse;
    await refreshDebugThread();
    setDebugState("Running");
  } catch (failure) {
    await stopDebugSession(false);
    throw failure;
  }
}

export async function attachDebugSession(): Promise<void> {
  const targetId = state.debugAttachTarget;
  if (targetId) await startDebugSession(targetId);
}

export async function reconnectDebugSession(): Promise<void> {
  const session = runtime.debugSession;
  if (!session) return;
  const { body: current } = await api<DebugSession>(
    `/api/v1/workspaces/${session.workspaceId}/debug-sessions/${session.id}`,
  );
  if (current.state !== "RUNNING") {
    await stopDebugSession(false);
    throw new Error("This debug session has already ended.");
  }
  runtime.debugSession = current;
  await openDebugSocket(current);
  runtime.debugInitialized = true;
  await applyStoredDebugBreakpoints();
  setDebugState("Reconnected");
}

export async function refreshDebugSessions(): Promise<void> {
  const workspace = state.workspace;
  if (!workspace) return;
  const { body: sessions } = await api<DebugSession[]>(
    `/api/v1/workspaces/${workspace.workspaceId}/debug-sessions`,
  );
  if (state.workspace?.workspaceId !== workspace.workspaceId) return;
  const activeSessions = sessions.filter((session) => session.state !== "STOPPED" && session.state !== "FAILED");
  const current = activeSessions.find((session) => session.id === runtime.debugSession?.id) ?? activeSessions[0] ?? null;
  if (runtime.debugSession && current?.id !== runtime.debugSession.id && runtime.debugSocket) {
    runtime.debugSocket.close(1000, "debug session changed");
    runtime.debugSocket = null;
    rejectDebugRequests("The debug session changed.");
  }
  runtime.debugSession = current;
  if (current) {
    setDebugState(runtime.debugSocket?.readyState === WebSocket.OPEN
      ? "Connected"
      : "Disconnected, reconnect available");
  } else {
    clearDebugInspectors();
    setDebugState("Not running");
  }
  updateDebugControls();
}

export async function stopDebugSession(notifyFailure: boolean): Promise<boolean> {
  const session = runtime.debugSession;
  if (session) {
    try {
      const { body: stopped } = await api<DebugSession>(
        `/api/v1/workspaces/${session.workspaceId}/debug-sessions/${session.id}/stop`,
        { method: "POST" },
      );
      if (stopped.state === "FAILED") {
        runtime.debugSession = stopped;
        setDebugState(stopped.error || "The debugger did not confirm disconnect; the workspace task is still running.");
        updateDebugControls();
        if (notifyFailure) toast(stopped.error || "The debugger did not confirm disconnect.", "error");
        return false;
      }
    } catch (failure) {
      setDebugState(`Stop request failed: ${errorText(failure)}`);
      updateDebugControls();
      if (notifyFailure) toast(`Could not stop debugging: ${errorText(failure)}`, "error");
      return false;
    }
  }
  runtime.debugSession = null;
  runtime.debugInitialized = false;
  const socket = runtime.debugSocket;
  runtime.debugSocket = null;
  if (socket && socket.readyState < WebSocket.CLOSING) socket.close(1000, "debug session stopped");
  rejectDebugRequests("The debug session was stopped.");
  runtime.dapEarlyEvents.clear();
  clearDebugInspectors();
  setDebugState("Not running");
  updateDebugControls();
  return true;
}

export async function applyDebugBreakpoints(showToast = true): Promise<void> {
  const workspace = state.workspace;
  if (!workspace || !runtime.debugSession) return;
  const path = state.debugBreakpointPath.trim() || state.activePath;
  if (!path) {
    if (showToast) throw new Error("Open a workspace file or enter a workspace-relative source path.");
    return;
  }
  const lines = [...new Set(
    state.debugBreakpointLines.split(/[\s,]+/).filter(Boolean).map(Number)
      .filter((line) => Number.isSafeInteger(line) && line > 0),
  )];
  setState("debugBreakpoints", (entries) => [...entries.filter((entry: BreakpointEntry) => entry.path !== path), { path, lines }]);
  refreshBreakpointGutterForPath(path);
  const response = await sendDebugBreakpointSet(path, lines);
  if (showToast) {
    const verified = response.body?.breakpoints;
    const count = Array.isArray(verified)
      ? verified.filter((value) => (value as { verified?: unknown }).verified === true).length
      : lines.length;
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
  for (const entry of state.debugBreakpoints) {
    if (entry.path !== skipPath) await sendDebugBreakpointSet(entry.path, entry.lines);
  }
}

export async function toggleDebugBreakpoint(path: string, line: number): Promise<void> {
  const current = state.debugBreakpoints.find((entry) => entry.path === path)?.lines ?? [];
  const lines = new Set(current);
  if (lines.has(line)) lines.delete(line);
  else lines.add(line);
  const next = [...lines].sort((left, right) => left - right);
  setState("debugBreakpoints", (entries) => [...entries.filter((entry: BreakpointEntry) => entry.path !== path), { path, lines: next }]);
  refreshBreakpointGutterForPath(path);
  if (state.activePath === path) setState("debugBreakpointLines", next.join(", "));
  if (runtime.debugInitialized && runtime.debugSocket?.readyState === WebSocket.OPEN) {
    await sendDebugBreakpointSet(path, next);
  }
}

export function refreshBreakpointGutters(): void {
  for (const opened of runtime.openDocuments.values()) {
    refreshBreakpointGutter(opened.wrapper, opened.path);
  }
}

function refreshBreakpointGutterForPath(path: string): void {
  const opened = runtime.openDocuments.get(path);
  if (opened) refreshBreakpointGutter(opened.wrapper, path);
}

function refreshBreakpointGutter(wrapper: HTMLElement, path: string): void {
  const active = new Set(state.debugBreakpoints.find((entry) => entry.path === path)?.lines ?? []);
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

async function sendDebugControl(command: string): Promise<void> {
  if (runtime.debugThreadId === null) throw new Error("Pause the program before using stepping controls.");
  await dapRequest(command, { threadId: runtime.debugThreadId });
  if (command === "continue") setDebugState("Running");
}

export function debugCommand(command: string): void {
  sendDebugControl(command).catch((failure) => toast(errorText(failure), "error"));
}

async function refreshDebugThread(): Promise<void> {
  if (runtime.debugSocket?.readyState !== WebSocket.OPEN) return;
  const { body } = await dapRequest("threads");
  const threads = Array.isArray(body?.threads) ? body.threads : [];
  const first = threads.find(
    (thread): thread is { id: number } =>
      typeof thread === "object" && thread !== null && typeof (thread as { id?: unknown }).id === "number",
  );
  if (runtime.debugThreadId === null && first) runtime.debugThreadId = first.id;
  updateDebugControls();
}

async function refreshDebugStack(): Promise<void> {
  if (runtime.debugThreadId === null || runtime.debugSocket?.readyState !== WebSocket.OPEN) return;
  const { body } = await dapRequest("stackTrace", { threadId: runtime.debugThreadId, levels: 30 });
  const frames = body?.stackFrames;
  const typed = Array.isArray(frames)
    ? frames.filter((frame): frame is DapFrame => typeof frame === "object" && frame !== null && typeof (frame as DapFrame).id === "number")
    : [];
  runtime.debugFrames = typed;
  setState("debugFrames", typed);
  if (typed.length) await selectDebugFrame(typed[0]);
  else {
    runtime.debugFrameId = null;
    runtime.debugVariables = [];
    setState("debugVariables", []);
  }
}

export function selectDebugStackFrame(frame: DapFrame): void {
  void selectDebugFrame(frame).catch((failure) => toast(errorText(failure), "error"));
  if (frame.source?.path && !frame.source.path.startsWith("/")) {
    openDocument(frame.source.path).catch((failure) => toast(errorText(failure), "error"));
  }
}

async function selectDebugFrame(frame: DapFrame): Promise<void> {
  runtime.debugFrameId = frame.id;
  const { body } = await dapRequest("scopes", { frameId: frame.id });
  const scopes = Array.isArray(body?.scopes) ? body.scopes : [];
  const references = scopes
    .map((scope) => (scope as { variablesReference?: unknown }).variablesReference)
    .filter((reference): reference is number => typeof reference === "number" && reference > 0);
  if (references.length === 0) {
    runtime.debugVariables = [];
    setState("debugVariables", []);
  } else {
    const variables = await dapRequest("variables", { variablesReference: references[0] });
    const values = variables.body?.variables;
    const typed = Array.isArray(values)
      ? values.filter((value): value is DapVariable => typeof value === "object" && value !== null && typeof (value as DapVariable).name === "string")
      : [];
    runtime.debugVariables = typed;
    setState("debugVariables", typed);
  }
  await refreshDebugWatches();
}

async function refreshDebugWatches(): Promise<void> {
  if (runtime.debugFrameId === null || runtime.debugSocket?.readyState !== WebSocket.OPEN) return;
  for (const expression of runtime.debugWatches) {
    try {
      const { body } = await dapRequest("evaluate", {
        expression,
        frameId: runtime.debugFrameId,
        context: "watch",
      });
      runtime.debugWatchValues.set(expression, typeof body?.result === "string" ? body.result : "<no value>");
    } catch (failure) {
      runtime.debugWatchValues.set(expression, errorText(failure));
    }
  }
  setState("debugWatchValues", Object.fromEntries(runtime.debugWatchValues));
}

export function addDebugWatch(event: SubmitEvent): void {
  event.preventDefault();
  const input = byId<HTMLInputElement>("debug-watch-expression");
  const expression = input.value.trim();
  if (!expression) return;
  if (runtime.debugWatches.length >= 32) {
    toast("A debug session can track up to 32 watch expressions.", "error");
    return;
  }
  if (!runtime.debugWatches.includes(expression)) runtime.debugWatches.push(expression);
  setState("debugWatches", [...runtime.debugWatches]);
  input.value = "";
  void refreshDebugWatches();
}

export function evaluateDebugConsole(event: SubmitEvent): void {
  event.preventDefault();
  const input = byId<HTMLInputElement>("debug-console-input");
  const expression = input.value.trim();
  if (!expression) return;
  input.value = "";
  void (async () => {
    try {
      const { body } = await dapRequest("evaluate", {
        expression,
        ...(runtime.debugFrameId === null ? {} : { frameId: runtime.debugFrameId }),
        context: "repl",
      });
      const result = typeof body?.result === "string" ? body.result : "<no value>";
      appendDebugConsole(`> ${expression}\n${result}\n`);
    } catch (failure) {
      appendDebugConsole(`> ${expression}\n[error] ${errorText(failure)}\n`);
    }
  })();
}

export function updateDebugControls(): void {
  const connected = runtime.debugSocket?.readyState === WebSocket.OPEN;
  const hasSession = runtime.debugSession !== null;
  setState("debugStartDisabled", hasSession);
  setState("debugLoadLaunchConfigDisabled", hasSession || state.workspace === null);
  setState("debugConfigSelectDisabled", hasSession || state.debugConfigs.length === 0);
  setState("debugStopDisabled", !hasSession);
  setState("debugReconnectHidden", !hasSession || connected);
  setState("debugLanguageDisabled", hasSession);
  setState("debugAttachTargetDisabled", hasSession || state.debugTargets.length === 0);
  setState("debugAttachDisabled", hasSession || !state.debugTargets.some((target) => target.id === state.debugAttachTarget));
  setState("debugProgramDisabled", hasSession);
  setState("debugMainClassDisabled", hasSession);
  setState("debugWorkingDirectoryDisabled", hasSession);
  setState("debugArgumentsDisabled", hasSession);
  setState("debugStopOnEntryDisabled", hasSession);
  setState("debugApplyBreakpointsDisabled", !connected);
  setState("debugWatchSubmitDisabled", !connected);
  setState("debugConsoleSubmitDisabled", !connected);
  const paused = (command: string): boolean => command !== "pause";
  setState("debugControls", {
    continue: !connected || runtime.debugThreadId === null || runtime.debugPaused !== paused("continue"),
    pause: !connected || runtime.debugThreadId === null || runtime.debugPaused !== paused("pause"),
    next: !connected || runtime.debugThreadId === null || runtime.debugPaused !== paused("next"),
    stepIn: !connected || runtime.debugThreadId === null || runtime.debugPaused !== paused("stepIn"),
    stepOut: !connected || runtime.debugThreadId === null || runtime.debugPaused !== paused("stepOut"),
  });
}

function setTerminalModifier(modifier: "ctrl" | "alt", enabled: boolean): void {
  setState("terminalModifiers", modifier, enabled);
}
export function toggleTerminalModifier(modifier: "ctrl" | "alt"): void {
  setTerminalModifier(modifier, !state.terminalModifiers[modifier]);
  runtime.terminal?.focus();
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
  const socket = runtime.socket;
  if (!socket || socket.readyState !== WebSocket.OPEN) {
    toast("Open or reconnect a terminal session first.", "error");
    return;
  }
  setTerminalModifier("ctrl", false);
  setTerminalModifier("alt", false);
  socket.send(JSON.stringify({ type: "input", data } satisfies StreamMessage));
  runtime.terminal?.focus();
}

export function terminalInput(data: string): void { sendTerminalInput(data); }
export function terminalShortcut(key: string): void {
  sendTerminalInput(String.fromCharCode((key || "c").charCodeAt(0) & 0x1f));
}

export async function copyTerminalSelection(): Promise<void> {
  const selection = runtime.terminal?.getSelection() ?? "";
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

function closeTerminal(closeServerSession: boolean, refreshSessions = false): Promise<void> {
  runtime.terminalObserver?.disconnect();
  runtime.terminalObserver = null;
  runtime.socket?.close();
  runtime.socket = null;
  runtime.terminal?.dispose();
  runtime.terminal = null;
  runtime.fitAddon = null;
  setState("terminalCloseHidden", true);
  setState("terminalSelectHidden", true);
  setState("terminalReconnectHidden", true);
  setState("keyStripHidden", true);
  setTerminalModifier("ctrl", false);
  setTerminalModifier("alt", false);
  const sessionId = runtime.terminalSessionId;
  runtime.terminalSessionId = null;
  byId("terminal-surface").replaceChildren();
  if (closeServerSession && sessionId) {
    return api(`/api/v1/terminals/${sessionId}`, { method: "DELETE" })
      .then(() => refreshSessions ? refreshTerminalSessions() : undefined);
  }
  return Promise.resolve();
}

export function closeActiveTerminal(): void {
  closeTerminal(true, true).catch((failure) => toast(errorText(failure), "error"));
}

export async function openTerminal(): Promise<void> {
  const workspace = state.workspace;
  if (!workspace) return;
  await closeTerminal(true);
  const { body: session } = await api<TerminalSession>(
    `/api/v1/workspaces/${workspace.workspaceId}/terminals?rows=28&cols=100`,
    { method: "POST" },
  );
  if (state.workspace?.workspaceId !== workspace.workspaceId) {
    await api(`/api/v1/terminals/${session.sessionId}`, { method: "DELETE" }).catch(() => undefined);
    return;
  }
  await connectTerminal(session, workspace.workspaceId);
}

export async function reconnectTerminal(): Promise<void> {
  const workspace = state.workspace;
  const sessionId = byId<HTMLSelectElement>("terminal-session-select").value;
  const session = state.terminalSessions.find((candidate) => candidate.sessionId === sessionId);
  if (!workspace || !session) return;
  await closeTerminal(false);
  await connectTerminal(session, workspace.workspaceId);
}

async function connectTerminal(session: TerminalSession, workspaceId: string): Promise<void> {
  if (state.workspace?.workspaceId !== workspaceId || session.workspaceId !== workspaceId) return;
  runtime.terminalSessionId = session.sessionId;
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
  if (state.workspace?.workspaceId !== workspaceId || runtime.terminalSessionId !== session.sessionId) return;
  const terminal = new Terminal({
    rows: session.rows,
    cols: session.cols,
    cursorBlink: true,
    fontFamily: '"IBM Plex Mono", "Cascadia Code", monospace',
    fontSize: 13,
    theme: { background: "#ffffff", foreground: "#28332b", cursor: "#2f6f52", selectionBackground: "#d8e7db" },
    convertEol: false,
  });
  const fit = new FitAddon();
  terminal.loadAddon(fit);
  terminal.open(byId("terminal-surface"));
  runtime.terminal = terminal;
  runtime.fitAddon = fit;
  setPanel("terminal");
  setState("keyStripHidden", false);
  setTerminalModifier("ctrl", false);
  setTerminalModifier("alt", false);
  setState("terminalCloseHidden", false);
  setState("terminalState", "Connecting…");
  const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
  const socket = new WebSocket(`${protocol}//${window.location.host}/api/v1/terminals/${session.sessionId}/stream`);
  runtime.socket = socket;
  let exited = false;
  socket.addEventListener("open", () => {
    if (runtime.socket !== socket || runtime.terminalSessionId !== session.sessionId) {
      socket.close();
      return;
    }
    terminal.focus();
    setState("terminalState", "Restoring…");
  });
  socket.addEventListener("message", (event) => {
    try {
      const message = JSON.parse(String(event.data)) as StreamMessage;
      if (message.type === "history") {
        const restore = (): void => {
          if (runtime.socket !== socket || runtime.terminalSessionId !== session.sessionId) return;
          fit.fit();
          if (socket.readyState === WebSocket.OPEN) {
            socket.send(JSON.stringify({ type: "resize", rows: terminal.rows, cols: terminal.cols } satisfies StreamMessage));
          }
          terminal.scrollToBottom();
          terminal.focus();
          if (!exited) setState("terminalState", "Connected");
        };
        if (message.data) terminal.write(message.data, restore);
        else restore();
      }
      if (message.type === "output" && message.data) terminal.write(message.data);
      if (message.type === "exit") {
        exited = true;
        setState("terminalState", `Exited (${message.exitCode ?? "unknown"})`);
      }
    } catch { terminal.write("\r\n[terminal] invalid server message\r\n"); }
  });
  socket.addEventListener("close", () => {
    if (runtime.socket === socket) setState("terminalState", "Disconnected");
  });
  socket.addEventListener("error", () => {
    if (runtime.socket === socket) setState("terminalState", "Connection error");
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
  runtime.terminalObserver = observer;
  await refreshTerminalSessions();
}

export function focusTerminalPanel(): void {
  window.requestAnimationFrame(() => {
    try { runtime.fitAddon?.fit(); } catch { /* Panel has no measurable size yet. */ }
  });
}

export async function backToWorkspaces(navigate = true): Promise<void> {
  if ([...runtime.openDocuments.values()].some((opened) => opened.dirty)
      && !window.confirm("Some files have unsaved changes. Leave this workspace?")) return;
  flushLocalDrafts();
  if (!await stopDebugSession(true)) {
    const currentWorkspaceId = state.workspace?.workspaceId;
    if (!navigate && currentWorkspaceId) {
      history.replaceState({ workspaceId: currentWorkspaceId }, "", `#workspace/${currentWorkspaceId}`);
    }
    return;
  }
  void closeTerminal(false);
  clearWorkspaceSearch();
  resetGitPanel();
  runtime.openDocuments.forEach((opened) => opened.view.destroy());
  runtime.openDocuments.clear();
  byId("editor-surface").replaceChildren();
  setState("tabs", []);
  setState("activePath", null);
  setState("selectedPath", null);
  setState("workspace", null);
  setState("debugTargets", []);
  clearDebugLaunchConfigurations();
  setState("previewPort", null);
  setState("previewUrl", null);
  runtime.languageServices?.close();
  runtime.languageServices = null;
  setState("languagePacks", []);
  setState("diagnostics", []);
  setState("languageState", "Optional");
  if (navigate) history.pushState({}, "", "/");
  setPanel("files");
  setPage("home");
  renderWorkspaces().catch((failure) => toast(errorText(failure), "error"));
}

export function restoreWorkspaceFromLocation(): void {
  const match = /^#workspace\/([^/]+)$/.exec(window.location.hash);
  if (!match) {
    if (state.workspace) void backToWorkspaces(false);
    return;
  }
  const workspaceId = match[1];
  if (state.workspace?.workspaceId === workspaceId) return;
  void api<Workspace>(`/api/v1/workspaces/${encodeURIComponent(workspaceId)}`)
    .then(({ body }) => {
      if (window.location.hash === `#workspace/${workspaceId}`) return openWorkspace(body, false);
    })
    .catch((failure) => toast(errorText(failure), "error"));
}

export async function main(): Promise<void> {
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
  window.addEventListener("pagehide", flushLocalDrafts);
  document.addEventListener("keydown", (event) => {
    if (!(event.ctrlKey || event.metaKey)) return;
    const key = event.key.toLowerCase();
    if (key === "s") {
      event.preventDefault();
      void saveActiveDocument().catch((failure) => toast(errorText(failure), "error"));
    }
    if (key === "p") {
      event.preventDefault();
      openQuickOpen();
    }
  });

  effect(() => {
    const visible = state.quickOpenVisible;
    byId("quick-open-palette").hidden = !visible;
  });
  effect(() => {
    const panel = state.activePanel;
    byId("workbench-page").dataset.activePanel = panel;
    document.querySelectorAll<HTMLButtonElement>(".workbench-panel-nav [data-panel]").forEach((item) => {
      item.setAttribute("aria-pressed", String(item.dataset.panel === panel));
    });
    if (panel === "terminal") focusTerminalPanel();
    if (panel === "preview") renderPreviewPanelState();
  });
  effect(() => {
    const page = state.page;
    byId("home-page").hidden = page !== "home";
    byId("workbench-page").hidden = page !== "workbench";
    byId("app").dataset.page = page;
  });
}
