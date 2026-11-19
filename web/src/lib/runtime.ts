///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import type { EditorView } from "codemirror";
import type { LanguageDiagnostic, LanguageServices } from "../language-services";
import {
    setState,
    state,
    toast,
    type DapFrame,
    type DapVariable,
    type DebugSession,
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
interface DapPending {
    resolve: (message: DapMessage) => void;
    reject: (failure: Error) => void;
    timer: number;
}
interface DapEventWaiter {
    resolve: () => void;
    reject: (failure: Error) => void;
    timer: number;
}
interface StreamMessage {
    type: "input" | "history" | "output" | "resize" | "exit";
    data?: string;
    rows?: number;
    cols?: number;
    exitCode?: number;
}
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
interface LocalDraft {
    content: string;
    updatedAt: number;
}
export type BreakpointEntry = { path: string; lines: number[] };
export type { DapMessage, DapPending, DapEventWaiter, StreamMessage, OpenDocument, LocalDraft };

export const runtime = {
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

export const languages: Record<string, string> = {
    node: "Node, TypeScript",
    python: "Python",
    java: "Java",
    cpp: "C, C++",
    go: "Go",
    rust: "Rust",
};

export const setupSummaries: Record<string, string> = {
    UNTRACKED: "No template task metadata.",
    PENDING: "Template setup is queued and will start shortly.",
    RUNNING: "Template setup is running…",
    FAILED: "Template setup failed.",
    READY: "Template setup is ready.",
};

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
        localStorage.setItem(
            key,
            JSON.stringify({ content, updatedAt: Date.now() } satisfies LocalDraft),
        );
    } catch {
        if (!state.draftStorageWarningShown) {
            setState("draftStorageWarningShown", true);
            toast(
                "Browser recovery storage is full or unavailable. Save this file to the server.",
                "error",
            );
        }
    }
}

function scheduleLocalDraft(workspaceId: string, path: string, content: string): void {
    const key = localDraftKey(workspaceId, path);
    if (!key) return;
    const existing = runtime.draftTimers.get(key);
    if (existing !== undefined) window.clearTimeout(existing);
    runtime.pendingDrafts.set(key, content);
    runtime.draftTimers.set(
        key,
        window.setTimeout(() => persistLocalDraft(key), 250),
    );
}

function clearLocalDraft(workspaceId: string, path: string): void {
    const key = localDraftKey(workspaceId, path);
    if (!key) return;
    const timer = runtime.draftTimers.get(key);
    if (timer !== undefined) window.clearTimeout(timer);
    runtime.draftTimers.delete(key);
    runtime.pendingDrafts.delete(key);
    try {
        localStorage.removeItem(key);
    } catch {
        /* Browser storage can be disabled. */
    }
}

function flushLocalDrafts(): void {
    for (const [key, timer] of runtime.draftTimers) {
        window.clearTimeout(timer);
        persistLocalDraft(key);
    }
}

const diagnosticsByPath = new Map<string, LanguageDiagnostic[]>();

function publishProblems(): void {
    setState("problems", [...diagnosticsByPath.values()].flat());
}

function recordDiagnostics(diagnostics: LanguageDiagnostic[]): void {
    const path = diagnostics[0]?.path ?? state.activePath;
    if (path) {
        if (diagnostics.length) diagnosticsByPath.set(path, diagnostics);
        else diagnosticsByPath.delete(path);
    }
    publishProblems();
}

function clearDiagnostics(path?: string): void {
    if (path) diagnosticsByPath.delete(path);
    else diagnosticsByPath.clear();
    publishProblems();
}

export {
    clearDiagnostics,
    recordDiagnostics,
    workspaceRunning,
    localDraftKey,
    readLocalDraft,
    persistLocalDraft,
    scheduleLocalDraft,
    clearLocalDraft,
    flushLocalDrafts,
};
