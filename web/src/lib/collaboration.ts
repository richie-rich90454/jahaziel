///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { api, errorText } from "./api";
import { byId } from "./dom";
import {
    setPanel,
    setState,
    state,
    toast,
    type CandidatePort,
    type Panel,
    type PortEntry,
    type QuickResult,
    type SearchResult,
    type Workspace,
} from "./store";
import { runtime } from "./runtime";
import { selectAndOpenFile } from "./files";
import { saveActiveDocument } from "./editor";
import { backToWorkspaces } from "./identity";
import { focusTerminalPanel } from "./terminal";

interface PaletteCommand {
    label: string;
    detail: string;
    run: () => void | Promise<void>;
}

const paletteCommands: PaletteCommand[] = [
    { label: "Save file", detail: "Ctrl S", run: () => saveActiveDocument() },
    { label: "Reload preview", detail: "", run: reloadPreview },
    { label: "Find preview ports", detail: "", run: () => findPreviewPorts() },
    { label: "Clear search", detail: "", run: clearWorkspaceSearch },
    { label: "Focus terminal", detail: "", run: focusTerminalPanel },
    { label: "Back to workspaces", detail: "", run: () => backToWorkspaces() },
];

function commandResults(query: string): QuickResult[] {
    const needle = query.trim().toLowerCase();
    return paletteCommands
        .filter((command) => command.label.toLowerCase().includes(needle))
        .map((command) => ({
            label: `Command: ${command.label}`,
            detail: command.detail,
            kind: "command" as const,
            value: command.label,
        }));
}

export function renderPreviewLinks(workspace: Workspace): void {
    setState("refreshPortsHidden", workspace.ports.length === 0);
    renderPreviewPanelState();
}

function renderPreviewPanelState(): void {
    const workspace = state.workspace;
    const current = workspace?.ports.find(
        (port) => port.port === state.previewPort && port.listening === true,
    );
    const selectOptions = [
        { value: "", label: "Select a listening port", disabled: true },
        ...(workspace?.ports ?? []).map((port) => ({
            value: String(port.port),
            label: `:${port.port}, ${port.listening === true ? "listening" : port.listening === false ? "not listening" : "not checked"}`,
            disabled: port.listening !== true,
        })),
    ];
    const portSelect = byId<HTMLSelectElement>("preview-port-select");
    if (portSelect) {
        portSelect.replaceChildren(
            ...selectOptions.map((option) => {
                const element = document.createElement("option");
                element.value = option.value;
                element.textContent = option.label;
                element.disabled = option.disabled;
                return element;
            }),
        );
        portSelect.value = current ? String(current.port) : "";
        portSelect.disabled = !workspace?.ports.some((port) => port.listening === true);
    }
    setState("previewSelectDisabled", !workspace?.ports.some((port) => port.listening === true));
    if (!current) {
        const selected = workspace?.ports.find((port) => port.port === state.previewPort);
        setState(
            "previewState",
            selected
                ? `Port :${selected.port} is not listening.`
                : "Select a listening port to open its preview.",
        );
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
    if (frame && (frame.src !== current.url || frame.src === window.location.href))
        frame.src = current.url;
    setState("previewReloadDisabled", false);
    setState("previewNewTabDisabled", false);
}

export function openPreview(port: PortEntry): void {
    if (port.listening !== true) {
        toast(
            `Port ${port.port} is not listening yet. Start the project task and check ports again.`,
            "error",
        );
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

const PREVIEW_MIN_HEIGHT = 120;

function previewBounds(): { min: number; max: number } {
    const min = PREVIEW_MIN_HEIGHT;
    return { min, max: Math.max(min, window.innerHeight - 160) };
}

function setPreviewHeight(height: number): void {
    const dock = byId<HTMLElement>("preview-dock");
    const { min, max } = previewBounds();
    const next = Math.round(Math.min(max, Math.max(min, height)));
    dock.style.height = `${next}px`;
    const resizer = document.getElementById("preview-resizer");
    if (resizer) {
        resizer.setAttribute("aria-valuemin", String(min));
        resizer.setAttribute("aria-valuemax", String(max));
        resizer.setAttribute("aria-valuenow", String(next));
    }
}

export function resizePreviewStart(event: PointerEvent): void {
    if (runtime.resizingPreview) return;
    event.preventDefault();
    const dock = byId<HTMLElement>("preview-dock");
    runtime.resizingPreview = true;
    runtime.previewStartY = event.clientY;
    runtime.previewStartHeight = dock.getBoundingClientRect().height;
    window.addEventListener("pointermove", resizePreviewMove);
    window.addEventListener("pointerup", resizePreviewEnd);
}

export function resizePreviewBy(delta: number): void {
    const dock = byId<HTMLElement>("preview-dock");
    const stored = Number.parseFloat(dock.style.height);
    const measured = dock.getBoundingClientRect().height;
    const current = Number.isFinite(stored) && stored > 0 ? stored : measured > 0 ? measured : 240;
    setPreviewHeight(current + delta);
}

function resizePreviewMove(event: PointerEvent): void {
    if (!runtime.resizingPreview) return;
    setPreviewHeight(runtime.previewStartHeight + runtime.previewStartY - event.clientY);
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
        candidates = (workspace.ports ?? []).map((port) => ({
            port: port.port,
            sources: ["tracked"],
        }));
    }
    setState("previewCandidates", candidates.length === 0 ? [] : candidates);
}

export async function registerPreviewPort(port: number): Promise<void> {
    const workspace = state.workspace;
    if (!workspace || !window.confirm(`Register and open preview on port ${port}?`)) return;
    try {
        const { body } = await api<PortEntry>(`/api/v1/workspaces/${workspace.workspaceId}/ports`, {
            method: "POST",
            body: JSON.stringify({ port, protocol: "http" }),
        });
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
    const { body: ports } = await api<PortEntry[]>(
        `/api/v1/workspaces/${workspace.workspaceId}/ports`,
    );
    if (state.workspace?.workspaceId !== workspace.workspaceId) return;
    setState("workspace", "ports", ports);
    renderPreviewLinks(state.workspace);
}

export async function searchWorkspace(query: string): Promise<void> {
    const workspace = state.workspace;
    if (!workspace) return;
    const params = new URLSearchParams({ q: query, limit: "50" });
    const { body } = await api<SearchResult[]>(
        `/api/v1/workspaces/${workspace.workspaceId}/files/search?${params}`,
    );
    if (
        state.workspace?.workspaceId !== workspace.workspaceId ||
        state.searchQuery.trim() !== query
    )
        return;
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
        () =>
            searchWorkspace(query).catch((failure) => {
                if (
                    state.workspace?.workspaceId === workspaceId &&
                    state.searchQuery.trim() === query
                ) {
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

let quickOpenReturnFocus: HTMLElement | null = null;

export function openQuickOpen(): void {
    quickOpenReturnFocus =
        document.activeElement instanceof HTMLElement ? document.activeElement : null;
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
    if (quickOpenReturnFocus?.isConnected) quickOpenReturnFocus.focus();
    quickOpenReturnFocus = null;
}

export async function renderQuickOpen(query: string): Promise<void> {
    setState("quickOpenQuery", query);
    const panels: Panel[] = [
        "files",
        "editor",
        "run",
        "languages",
        "debug",
        "git",
        "terminal",
        "preview",
        "collaboration",
        "agent",
        "data",
        "club",
        "ops",
    ];
    const trimmed = query.trim();
    if (trimmed.length < 2) {
        const results: QuickResult[] = panels.map((panel) => ({
            label: `Panel: ${panel}`,
            detail: "",
            kind: "panel",
            value: panel,
        }));
        for (const opened of runtime.openDocuments.keys()) {
            results.push({ label: `Open: ${opened}`, detail: "", kind: "document", value: opened });
        }
        results.push(...commandResults(trimmed));
        setState("quickOpenResults", results);
        return;
    }
    const workspace = state.workspace;
    if (!workspace) return;
    try {
        const params = new URLSearchParams({ q: trimmed, limit: "30" });
        const { body } = await api<SearchResult[]>(
            `/api/v1/workspaces/${workspace.workspaceId}/files/search?${params}`,
        );
        const results: QuickResult[] = commandResults(trimmed);
        results.push(
            ...body.map((match) => ({
                label: `${match.path}${match.line ? `:${match.line}` : ""}`,
                detail: match.preview,
                kind: "match" as const,
                value: match.path,
            })),
        );
        if (results.length === 0)
            results.push({ label: "No matching files.", detail: "", kind: "empty", value: "" });
        setState("quickOpenResults", results);
    } catch (failure) {
        toast(errorText(failure), "error");
    }
}

export function chooseQuickResult(result: QuickResult): void {
    if (result.kind === "empty") return;
    if (result.kind === "panel") setPanel(result.value as Panel);
    else if (result.kind === "command") {
        const command = paletteCommands.find((item) => item.label === result.value);
        closeQuickOpen();
        if (command)
            void Promise.resolve(command.run()).catch((failure) =>
                toast(errorText(failure), "error"),
            );
        return;
    } else selectAndOpenFile(result.value);
    closeQuickOpen();
}

export { renderPreviewPanelState };
