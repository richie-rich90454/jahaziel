///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { LanguageServices, type LanguageDiagnostic, type LanguagePack } from "../language-services";
import { api, errorText } from "./api";
import { byId, effect } from "./dom";
import { setPanel, setState, state, toast, type DocumentBody, type TabInfo } from "./store";
import {
    clearDiagnostics,
    clearLocalDraft,
    readLocalDraft,
    recordDiagnostics,
    runtime,
    scheduleLocalDraft,
    workspaceRunning,
    type OpenDocument,
} from "./runtime";
import { refreshBreakpointGutter, toggleDebugBreakpoint } from "./debug";
import { refreshGitSafely } from "./git";

function formatBytes(bytes: number): string {
    if (!Number.isFinite(bytes) || bytes <= 0) return "unknown size";
    return bytes >= 1024 * 1024
        ? `${(bytes / (1024 * 1024)).toFixed(0)} MB`
        : `${Math.ceil(bytes / 1024)} KB`;
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
            setState(
                "languageState",
                `Installing ${pack.name} (${formatBytes(pack.downloadBytes)})…`,
            );
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
    recordDiagnostics(diagnostics);
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

async function languageFor(path: string): Promise<any[]> {
    const lower = path.toLowerCase();
    if (lower.endsWith(".html") || lower.endsWith(".htm"))
        return [(await import("@codemirror/lang-html")).html()];
    if (lower.endsWith(".json") || lower.endsWith(".jsonc"))
        return [(await import("@codemirror/lang-json")).json()];
    if (lower.endsWith(".py")) return [(await import("@codemirror/lang-python")).python()];
    if (/\.(js|jsx|ts|tsx|mjs|cjs)$/.test(lower)) {
        return [
            (await import("@codemirror/lang-javascript")).javascript({
                typescript: /\.(ts|tsx)$/.test(lower),
            }),
        ];
    }
    return [];
}

const IMAGE_EXTENSION = /\.(png|jpe?g|gif|svg|webp|bmp|ico|avif)$/i;

interface ImageDocument {
    path: string;
    url: string;
    wrapper: HTMLDivElement;
}

export const imageDocuments = new Map<string, ImageDocument>();

function disposeImageDocument(path: string): void {
    const image = imageDocuments.get(path);
    if (!image) return;
    URL.revokeObjectURL(image.url);
    image.wrapper.remove();
    imageDocuments.delete(path);
}

function clearImageDocuments(): void {
    for (const path of [...imageDocuments.keys()]) disposeImageDocument(path);
}

function renameImageDocument(previousPath: string, nextPath: string): void {
    const image = imageDocuments.get(previousPath);
    if (!image) return;
    imageDocuments.delete(previousPath);
    image.path = nextPath;
    imageDocuments.set(nextPath, image);
}

async function openImageDocument(path: string): Promise<void> {
    const workspace = state.workspace;
    if (!workspace) return;
    const query = new URLSearchParams({ path });
    const response = await fetch(
        `/api/v1/workspaces/${workspace.workspaceId}/files/download?${query}`,
        { credentials: "same-origin" },
    );
    if (!response.ok) throw new Error(`${response.status} Image could not be loaded.`);
    const url = URL.createObjectURL(await response.blob());
    const wrapper = document.createElement("div");
    wrapper.className = "editor-instance image-preview";
    const image = document.createElement("img");
    image.className = "image-preview-image";
    image.alt = path;
    image.src = url;
    wrapper.append(image);
    effect(() => {
        wrapper.hidden = state.activePath !== path;
    });
    byId("editor-surface").append(wrapper);
    imageDocuments.set(path, { path, url, wrapper });
    setState("tabs", (tabs) => [...tabs, { path, dirty: false, recoveredDraft: false }]);
    setState("selectedPath", path);
    setState("downloadDisabled", false);
    activateDocument(path);
    if (window.matchMedia("(max-width: 900px)").matches) setPanel("editor");
}

export async function openDocument(path: string): Promise<void> {
    const existing = runtime.openDocuments.get(path) ?? imageDocuments.get(path);
    if (existing) {
        activateDocument(path);
        if (window.matchMedia("(max-width: 900px)").matches) setPanel("editor");
        return;
    }
    const workspace = state.workspace;
    if (!workspace) return;
    if (IMAGE_EXTENSION.test(path)) {
        await openImageDocument(path);
        return;
    }
    const query = new URLSearchParams({ path });
    const { body, response } = await api<DocumentBody>(
        `/api/v1/workspaces/${workspace.workspaceId}/files/document?${query}`,
    );
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
    wrapper.addEventListener(
        "keydown",
        (event: KeyboardEvent) => {
            if (event.key === "Enter" || event.key === " ") toggleFromGutter(event);
        },
        true,
    );
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
                if (documentState.dirty)
                    scheduleLocalDraft(workspace.workspaceId, path, update.state.doc.toString());
                else clearLocalDraft(workspace.workspaceId, path);
                runtime.languageServices?.changeDocument(path, update.state.doc.toString());
                syncTab(path);
                updateSaveState();
            }),
        ],
    });
    wrapper.hidden = true;
    effect(() => {
        wrapper.hidden = state.activePath !== path;
    });
    effect(() => {
        // Reading the store establishes a dependency so CodeMirror's rebuilt gutter elements are
        // re-decorated after every breakpoint change.
        void state.debugBreakpoints;
        refreshBreakpointGutter(wrapper, path);
    });
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
    setState("tabs", (tabs) =>
        tabs.map((tab: TabInfo) =>
            tab.path === path
                ? { ...tab, dirty: opened.dirty, recoveredDraft: opened.recoveredDraft }
                : tab,
        ),
    );
}

export function activateDocument(path: string): void {
    const image = imageDocuments.get(path);
    if (image) {
        for (const doc of runtime.openDocuments.values()) doc.wrapper.hidden = true;
        for (const [otherPath, doc] of imageDocuments) doc.wrapper.hidden = otherPath !== path;
        setState("activePath", path);
        setState("activeFileLabel", path);
        setState("editorEmptyVisible", false);
        setState("statusLanguage", "Image");
        setState("debugBreakpointPath", path);
        setState("debugBreakpointLines", "");
        renderTabs();
        updateSaveState();
        return;
    }
    const next = runtime.openDocuments.get(path);
    if (!next) return;
    for (const doc of imageDocuments.values()) doc.wrapper.hidden = true;
    for (const [otherPath, doc] of runtime.openDocuments) doc.wrapper.hidden = otherPath !== path;
    next.view.dom.dataset.editorPath = path;
    setState("activePath", path);
    setState("activeFileLabel", path);
    setState("editorEmptyVisible", false);
    setState("statusLanguage", languageName(path));
    setState("debugBreakpointPath", path);
    setState(
        "debugBreakpointLines",
        (state.debugBreakpoints.find((entry) => entry.path === path)?.lines ?? []).join(", "),
    );
    if (state.workspace && !workspaceRunning()) {
        runtime.languageServices?.closeDocument(path);
        setState("languageState", "Start the workspace to use language services");
    } else {
        runtime.languageServices?.openDocument(
            languageIdForPath(path),
            path,
            next.view.state.doc.toString(),
        );
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
        py: "Python",
        md: "Markdown",
        ts: "TypeScript",
        tsx: "TypeScript",
        js: "JavaScript",
        jsx: "JavaScript",
        json: "JSON",
        html: "HTML",
        htm: "HTML",
        css: "CSS",
        java: "Java",
        rs: "Rust",
        go: "Go",
        c: "C",
        h: "C",
        cpp: "C++",
        cc: "C++",
    };
    return names[extension] ?? extension;
}

function renderTabs(): void {
    setState("tabs", (tabs) =>
        tabs.map((tab: TabInfo) => {
            const opened = runtime.openDocuments.get(tab.path);
            return opened
                ? { ...tab, dirty: opened.dirty, recoveredDraft: opened.recoveredDraft }
                : tab;
        }),
    );
}

export function closeTab(path: string): void {
    if (imageDocuments.has(path)) {
        disposeImageDocument(path);
        clearDiagnostics(path);
        setState("tabs", (tabs) => tabs.filter((tab: TabInfo) => tab.path !== path));
        if (state.activePath === path) {
            setState("activePath", null);
            const replacement =
                runtime.openDocuments.keys().next().value ?? imageDocuments.keys().next().value;
            if (replacement) activateDocument(replacement);
            else {
                setState("activeFileLabel", "No file selected");
                setState("editorEmptyVisible", true);
                updateSaveState();
            }
        } else updateSaveState();
        return;
    }
    const opened = runtime.openDocuments.get(path);
    if (!opened) return;
    if (opened.dirty && !window.confirm(`Discard unsaved changes to ${path}?`)) return;
    if (opened.dirty && state.workspace) clearLocalDraft(state.workspace.workspaceId, path);
    opened.view.destroy();
    opened.wrapper.remove();
    runtime.openDocuments.delete(path);
    clearDiagnostics(path);
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
    setState(
        "saveState",
        current?.dirty
            ? current.recoveredDraft
                ? "Recovered local draft, unsaved"
                : "Unsaved changes, saved locally"
            : current
              ? "Saved"
              : "",
    );
    setState("saveStateClass", current?.dirty ? "save-state dirty" : "save-state");
    setState(
        "conflictVisible",
        Boolean(
            current &&
            current.conflictRemoteVersion !== null &&
            current.conflictRemoteContent !== null,
        ),
    );
}

export function useServerVersion(): void {
    const workspace = state.workspace;
    const opened = state.activePath ? runtime.openDocuments.get(state.activePath) : undefined;
    if (
        !workspace ||
        !opened ||
        opened.conflictRemoteVersion === null ||
        opened.conflictRemoteContent === null
    )
        return;
    if (
        opened.dirty &&
        !window.confirm(`Discard your local edits to ${opened.path} and use the server version?`)
    )
        return;
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

export function keepLocalVersion(): void {
    const workspace = state.workspace;
    const opened = state.activePath ? runtime.openDocuments.get(state.activePath) : undefined;
    if (
        !workspace ||
        !opened ||
        opened.conflictRemoteVersion === null ||
        opened.conflictRemoteContent === null
    )
        return;
    opened.version = opened.conflictRemoteVersion;
    opened.savedContent = opened.conflictRemoteContent;
    opened.conflictRemoteVersion = null;
    opened.conflictRemoteContent = null;
    opened.recoveredDraft = false;
    opened.dirty = opened.view.state.doc.toString() !== opened.savedContent;
    if (opened.dirty)
        scheduleLocalDraft(workspace.workspaceId, opened.path, opened.view.state.doc.toString());
    renderTabs();
    updateSaveState();
    toast(
        "Your edits are ready to save against the latest version. Review them, then choose Save.",
    );
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
            if (
                state.workspace?.workspaceId !== workspace.workspaceId ||
                runtime.openDocuments.get(path) !== opened
            )
                return;
            opened.conflictRemoteVersion =
                latest.response.headers.get("ETag") || latest.body.version;
            opened.conflictRemoteContent = latest.body.content;
            updateSaveState();
            setState("saveState", "Changed on disk — reload before saving");
            setState("saveStateClass", "save-state conflict");
            toast(
                "Your local edits are preserved. Choose the server copy or keep your edits before saving again.",
                "error",
            );
            return;
        }
        throw failure;
    }
}

export {
    clearImageDocuments,
    disposeImageDocument,
    renameImageDocument,
    renderLanguageDiagnostics,
    renderTabs,
    updateSaveState,
};
