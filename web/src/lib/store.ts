///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { createStore } from "solid-js";
import type { DebugLaunchConfiguration } from "../debug-launch-config";
import type { LanguageDiagnostic, LanguagePack } from "../language-services";

export type { LanguageDiagnostic, LanguagePack } from "../language-services";

export type Panel =
    | "files"
    | "editor"
    | "run"
    | "languages"
    | "debug"
    | "git"
    | "terminal"
    | "preview";

export interface User {
    displayName: string;
    email: string;
}
export interface PortEntry {
    port: number;
    protocol: string;
    url: string;
    listening: boolean | null;
}
export interface Workspace {
    workspaceId: string;
    name: string;
    state: string;
    stateReason?: string | null;
    ports: PortEntry[];
}
export interface FileEntry {
    name: string;
    path: string;
    directory: boolean;
    size: number;
    modifiedAt: number;
}
export interface DocumentBody {
    path: string;
    content: string;
    version: string;
}
export interface SearchResult {
    path: string;
    line: number;
    preview: string;
}
export interface TerminalSession {
    sessionId: string;
    workspaceId: string;
    startedAt: string;
    lastActivityAt: string;
    rows: number;
    cols: number;
}
export interface AgentToolStatus {
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
export interface WorkspaceTask {
    id: string;
    label: string;
    port: number | null;
}
export interface DebugTarget {
    id: string;
    taskRunId: string;
    languageId: string;
    label: string;
}
export interface SetupStatus {
    status: string;
    output: string;
    error: string;
    updatedAt: string;
    correlationId?: string | null;
}
export interface TaskRun {
    id: string;
    workspaceId: string;
    taskId: string;
    label: string;
    status: string;
    output: string;
    exitCode: number | null;
    durationMs?: number;
}
export interface GitChange {
    path: string;
    indexStatus: string;
    worktreeStatus: string;
}
export interface GitStatus {
    repository: boolean;
    branch: string;
    changes: GitChange[];
}
export interface GitDiff {
    path: string;
    content: string;
}
export interface DebugSession {
    id: string;
    workspaceId: string;
    languageId: string;
    adapterId: string;
    state: string;
    adapterVersion: string;
    startedAt: string;
    error: string;
}
export interface DapFrame {
    id: number;
    name: string;
    line?: number;
    column?: number;
    source?: { name?: string; path?: string };
}
export interface DapVariable {
    name: string;
    value: string;
    type?: string;
    variablesReference?: number;
}
export interface TabInfo {
    path: string;
    dirty: boolean;
    recoveredDraft: boolean;
}
export interface ToastItem {
    id: number;
    message: string;
    kind: "error" | "success";
}
export interface CandidatePort {
    port: number;
    sources: string[];
}
export interface QuickResult {
    label: string;
    detail: string;
    kind: "panel" | "document" | "match" | "command" | "empty";
    value: string;
}

export interface UiState {
    page: "home" | "workbench";
    activePanel: Panel;
    user: User | null;
    loadingState: string;
    templates: string[];
    workspaces: Workspace[];
    workspace: Workspace | null;
    workbenchTitle: string;
    workbenchState: string;
    workbenchStateClass: string;
    rootName: string;
    runtimeLabel: string;

    files: FileEntry[];
    searchQuery: string;
    searchResults: SearchResult[];
    searchVisible: boolean;
    selectedPath: string | null;
    downloadDisabled: boolean;

    tabs: TabInfo[];
    activePath: string | null;
    activeFileLabel: string;
    statusLanguage: string;
    saveState: string;
    saveStateClass: string;
    saveDisabled: boolean;
    conflictVisible: boolean;
    editorEmptyVisible: boolean;

    setupState: string;
    setupSummary: string;
    setupOutput: string;
    setupOutputHidden: boolean;
    setupRetryHidden: boolean;
    setupCorrelation: string;
    setupCorrelationHidden: boolean;
    tasks: WorkspaceTask[];
    taskOutput: string;
    taskOutputHidden: boolean;
    taskStopHidden: boolean;
    taskStopDisabled: boolean;
    taskCorrelation: string;
    taskCorrelationHidden: boolean;
    runHistory: TaskRun[];

    gitState: string;
    gitChanges: GitChange[];
    gitMessage: string;
    gitDiff: string;
    gitDiffHidden: boolean;
    gitFormHidden: boolean;
    gitCommitDisabled: boolean;

    languageState: string;
    languagePacks: LanguagePack[];
    diagnostics: LanguageDiagnostic[];
    problems: LanguageDiagnostic[];

    debugState: string;
    debugTargets: DebugTarget[];
    debugConfigs: DebugLaunchConfiguration[];
    debugConfigFieldHidden: boolean;
    debugConfigSelectHidden: boolean;
    debugConfigStatus: string;
    debugConfigName: string;
    debugLanguage: string;
    debugProgram: string;
    debugMainClass: string;
    debugWorkingDirectory: string;
    debugArguments: string;
    debugStopOnEntry: boolean;
    debugProgramFieldHidden: boolean;
    debugMainClassFieldHidden: boolean;
    debugAttachTarget: string;
    debugBreakpointPath: string;
    debugBreakpointLines: string;
    debugBreakpoints: Array<{ path: string; lines: number[] }>;
    debugFrames: DapFrame[];
    debugVariables: DapVariable[];
    debugWatches: string[];
    debugWatchValues: Record<string, string>;
    debugConsole: string;
    debugControls: Record<string, boolean>;
    debugWatchSubmitDisabled: boolean;
    debugConsoleSubmitDisabled: boolean;
    debugLoadLaunchConfigDisabled: boolean;
    debugStartDisabled: boolean;
    debugStopDisabled: boolean;
    debugReconnectHidden: boolean;
    debugLanguageDisabled: boolean;
    debugAttachDisabled: boolean;
    debugAttachTargetDisabled: boolean;
    debugProgramDisabled: boolean;
    debugMainClassDisabled: boolean;
    debugWorkingDirectoryDisabled: boolean;
    debugArgumentsDisabled: boolean;
    debugStopOnEntryDisabled: boolean;
    debugApplyBreakpointsDisabled: boolean;
    debugConfigSelectDisabled: boolean;

    terminalState: string;
    terminalSessions: TerminalSession[];
    terminalSelectHidden: boolean;
    terminalSelectDisabled: boolean;
    terminalReconnectHidden: boolean;
    terminalReconnectDisabled: boolean;
    terminalCloseHidden: boolean;
    keyStripHidden: boolean;
    terminalModifiers: { ctrl: boolean; alt: boolean };
    opencodeStatus: string;
    opencodeLaunchLabel: string;
    opencodeLaunchDisabled: boolean;
    opencodeUpdateHidden: boolean;
    opencodeUpdateDisabled: boolean;
    opencodeRemoveHidden: boolean;
    opencodeRemoveDisabled: boolean;

    previewPort: number | null;
    previewUrl: string | null;
    previewState: string;
    previewFrameHidden: boolean;
    previewEmptyHidden: boolean;
    previewReloadDisabled: boolean;
    previewNewTabDisabled: boolean;
    previewSelectDisabled: boolean;
    previewCandidates: CandidatePort[] | null;
    refreshPortsHidden: boolean;

    quickOpenVisible: boolean;
    quickOpenQuery: string;
    quickOpenResults: QuickResult[];
    toasts: ToastItem[];
    draftStorageWarningShown: boolean;
}

const [state, rawSetState] = createStore<UiState>({
    page: "home",
    activePanel: "files",
    user: null,
    loadingState: "Connecting…",
    templates: [],
    workspaces: [],
    workspace: null,
    workbenchTitle: "Workspace",
    workbenchState: "requested",
    workbenchStateClass: "status status-requested",
    rootName: "Project",
    runtimeLabel: "Start workspace",

    files: [],
    searchQuery: "",
    searchResults: [],
    searchVisible: false,
    selectedPath: null,
    downloadDisabled: true,

    tabs: [],
    activePath: null,
    activeFileLabel: "No file selected",
    statusLanguage: "Plain text",
    saveState: "",
    saveStateClass: "save-state",
    saveDisabled: true,
    conflictVisible: false,
    editorEmptyVisible: true,

    setupState: "Pending",
    setupSummary: "Template setup is queued and will start shortly.",
    setupOutput: "",
    setupOutputHidden: true,
    setupRetryHidden: true,
    setupCorrelation: "",
    setupCorrelationHidden: true,
    tasks: [],
    taskOutput: "",
    taskOutputHidden: true,
    taskStopHidden: true,
    taskStopDisabled: false,
    taskCorrelation: "",
    taskCorrelationHidden: true,
    runHistory: [],

    gitState: "Git",
    gitChanges: [],
    gitMessage: "",
    gitDiff: "",
    gitDiffHidden: true,
    gitFormHidden: true,
    gitCommitDisabled: true,

    languageState: "Optional",
    languagePacks: [],
    diagnostics: [],
    problems: [],

    debugState: "NOT RUNNING",
    debugTargets: [],
    debugConfigs: [],
    debugConfigFieldHidden: true,
    debugConfigSelectHidden: true,
    debugConfigStatus: "",
    debugConfigName: "",
    debugLanguage: "node",
    debugProgram: "",
    debugMainClass: "",
    debugWorkingDirectory: ".",
    debugArguments: "[]",
    debugStopOnEntry: false,
    debugProgramFieldHidden: false,
    debugMainClassFieldHidden: true,
    debugAttachTarget: "",
    debugBreakpointPath: "",
    debugBreakpointLines: "",
    debugBreakpoints: [],
    debugFrames: [],
    debugVariables: [],
    debugWatches: [],
    debugWatchValues: {},
    debugConsole: "Debugger output will appear here.",
    debugControls: {
        continue: true,
        pause: true,
        next: true,
        stepIn: true,
        stepOut: true,
    },
    debugWatchSubmitDisabled: true,
    debugConsoleSubmitDisabled: true,
    debugLoadLaunchConfigDisabled: false,
    debugStartDisabled: false,
    debugStopDisabled: true,
    debugReconnectHidden: true,
    debugLanguageDisabled: false,
    debugAttachDisabled: true,
    debugAttachTargetDisabled: true,
    debugProgramDisabled: false,
    debugMainClassDisabled: false,
    debugWorkingDirectoryDisabled: false,
    debugArgumentsDisabled: false,
    debugStopOnEntryDisabled: false,
    debugApplyBreakpointsDisabled: true,
    debugConfigSelectDisabled: true,

    terminalState: "No session",
    terminalSessions: [],
    terminalSelectHidden: true,
    terminalSelectDisabled: true,
    terminalReconnectHidden: true,
    terminalReconnectDisabled: true,
    terminalCloseHidden: true,
    keyStripHidden: true,
    terminalModifiers: { ctrl: false, alt: false },
    opencodeStatus: "OpenCode, checking",
    opencodeLaunchLabel: "Open OpenCode",
    opencodeLaunchDisabled: true,
    opencodeUpdateHidden: true,
    opencodeUpdateDisabled: false,
    opencodeRemoveHidden: true,
    opencodeRemoveDisabled: false,

    previewPort: null,
    previewUrl: null,
    previewState: "Select a listening port to open its preview.",
    previewFrameHidden: true,
    previewEmptyHidden: false,
    previewReloadDisabled: true,
    previewNewTabDisabled: true,
    previewSelectDisabled: true,
    previewCandidates: null,
    refreshPortsHidden: true,

    quickOpenVisible: false,
    quickOpenQuery: "",
    quickOpenResults: [],
    toasts: [],
    draftStorageWarningShown: false,
});

export { state };

/** Solid 2.x stores expose a functional setter; keep the familiar path form for this app. */
export function setState(key: string, value: (current: any) => any, extra?: any): void;
export function setState(key: string, value: any, extra?: any): void;
export function setState(key: string, value: any, extra?: any): void {
    rawSetState((draft) => {
        const target = (draft as unknown as Record<string, any>)[key];
        if (arguments.length < 2) return;
        if (extra === undefined) {
            (draft as unknown as Record<string, any>)[key] =
                typeof value === "function" ? value(target) : value;
            return;
        }
        if (typeof value === "number" && Array.isArray(target)) {
            target[value] = typeof extra === "function" ? extra(target[value]) : extra;
        } else if (target !== null && typeof target === "object") {
            (target as Record<string, any>)[String(value)] =
                typeof extra === "function"
                    ? extra((target as Record<string, any>)[String(value)])
                    : extra;
        }
    });
}

let nextToastId = 1;

export function toast(message: string, kind: "error" | "success" = "success"): void {
    const id = nextToastId++;
    setState("toasts", (items) => [...items, { id, message, kind }]);
    window.setTimeout(() => {
        setState("toasts", (items) => items.filter((item: ToastItem) => item.id !== id));
    }, 4800);
}

export function setPage(page: "home" | "workbench"): void {
    setState("page", page);
}

export function setPanel(panel: Panel): void {
    setState("activePanel", panel);
}
