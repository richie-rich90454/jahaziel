///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { LanguageServices } from "../language-services";
import { api, errorText } from "./api";
import { byId, effect } from "./dom";
import { setPage, setPanel, setState, state, toast, type User, type Workspace } from "./store";
import { clearDiagnostics, flushLocalDrafts, runtime, workspaceRunning } from "./runtime";
import {
    clearWorkspaceSearch,
    openQuickOpen,
    refreshPortStatuses,
    renderPreviewLinks,
    renderPreviewPanelState,
} from "./collaboration";
import { refreshGitSafely, resetGitPanel } from "./git";
import { clearDebugLaunchConfigurations, refreshDebugSessions, stopDebugSession } from "./debug";
import {
    clearImageDocuments,
    refreshLanguagePacks,
    renderLanguageDiagnostics,
    renderTabs,
    saveActiveDocument,
} from "./editor";
import { refreshTasks } from "./tasks";
import { refreshAgentToolStatus } from "./agent";
import {
    autoReconnectTerminal,
    closeTerminal,
    focusTerminalPanel,
    refreshTerminalSessions,
} from "./terminal";
import { refreshFiles } from "./files";
import { installHistoryShortcuts, loadHistory } from "./history";
import { startWatching, stopWatching } from "./watch";

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
    const { body: created } = await api<Workspace>(
        `/api/v1/templates/${encodeURIComponent(template)}/apply`,
        {
            method: "POST",
            body: JSON.stringify({ workspaceName: name }),
        },
    );
    input.value = "";
    toast(`${created.name} is ready to edit.`);
    await renderWorkspaces();
    await openWorkspace(created);
}

export async function startWorkspaceFromCard(workspace: Workspace): Promise<void> {
    const { body } = await api<Workspace>(`/api/v1/workspaces/${workspace.workspaceId}/start`, {
        method: "POST",
    });
    await renderWorkspaces();
    if (state.workspace?.workspaceId === body.workspaceId) applyWorkspace(body);
}

export async function stopWorkspaceFromCard(workspace: Workspace): Promise<void> {
    await api(`/api/v1/workspaces/${workspace.workspaceId}/stop`, { method: "POST" });
    await renderWorkspaces();
    if (state.workspace?.workspaceId === workspace.workspaceId)
        applyWorkspace({ ...workspace, state: "suspended" });
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
            history.pushState(
                { workspaceId: workspace.workspaceId },
                "",
                `#workspace/${workspace.workspaceId}`,
            );
        }
        setPage("workbench");
        void loadHistory().catch(() => undefined);
        startWatching();
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
    if (
        [...runtime.openDocuments.values()].some((opened) => opened.dirty) &&
        state.workspace &&
        state.workspace.workspaceId !== workspace.workspaceId &&
        !window.confirm("Some files have unsaved changes. Leave this workspace?")
    )
        return;
    if (state.workspace?.workspaceId !== workspace.workspaceId) {
        flushLocalDrafts();
        if (!(await stopDebugSession(true))) {
            const currentWorkspaceId = state.workspace?.workspaceId;
            if (!navigate && currentWorkspaceId) {
                history.replaceState(
                    { workspaceId: currentWorkspaceId },
                    "",
                    `#workspace/${currentWorkspaceId}`,
                );
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
    clearImageDocuments();
    clearDiagnostics();
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
    if (navigate)
        history.pushState(
            { workspaceId: workspace.workspaceId },
            "",
            `#workspace/${workspace.workspaceId}`,
        );
    setPage("workbench");
    setPanel("files");
    void loadHistory().catch(() => undefined);
    startWatching();
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
    const { body } = await api<Workspace>(`/api/v1/workspaces/${workspace.workspaceId}/${action}`, {
        method: "POST",
    });
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

export async function backToWorkspaces(navigate = true): Promise<void> {
    stopWatching();
    if (
        [...runtime.openDocuments.values()].some((opened) => opened.dirty) &&
        !window.confirm("Some files have unsaved changes. Leave this workspace?")
    )
        return;
    flushLocalDrafts();
    if (!(await stopDebugSession(true))) {
        const currentWorkspaceId = state.workspace?.workspaceId;
        if (!navigate && currentWorkspaceId) {
            history.replaceState(
                { workspaceId: currentWorkspaceId },
                "",
                `#workspace/${currentWorkspaceId}`,
            );
        }
        return;
    }
    void closeTerminal(false);
    clearWorkspaceSearch();
    resetGitPanel();
    runtime.openDocuments.forEach((opened) => opened.view.destroy());
    runtime.openDocuments.clear();
    clearImageDocuments();
    clearDiagnostics();
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
            if (window.location.hash === `#workspace/${workspaceId}`)
                return openWorkspace(body, false);
        })
        .catch((failure) => toast(errorText(failure), "error"));
}

export async function main(): Promise<void> {
    installHistoryShortcuts();
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
        document
            .querySelectorAll<HTMLButtonElement>(".workbench-panel-nav [data-panel]")
            .forEach((item) => {
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
