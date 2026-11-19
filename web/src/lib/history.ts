///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { api, errorText, isUnavailable } from "./api";
import { parseUnifiedDiff } from "./git-extras";
import { setState, state, toast, type HistoryEntry, type SnapshotRow } from "./store";

function endpoint(path: string): string {
    const id = state.workspace?.workspaceId;
    if (!id) throw new Error("Open a workspace first.");
    return `/api/v1/workspaces/${id}/${path}`;
}

interface HistoryResponse {
    entries?: HistoryEntry[];
    canUndo?: boolean;
    canRedo?: boolean;
}

function applyHistory(body: HistoryEntry[] | HistoryResponse | null, canRedo?: boolean): void {
    const entries = Array.isArray(body) ? body : (body?.entries ?? []);
    const response = Array.isArray(body) ? null : body;
    setState("historyEntries", entries);
    setState("historyCanUndo", response?.canUndo ?? entries.length > 0);
    if (canRedo !== undefined) setState("historyCanRedo", canRedo);
    else if (response?.canRedo !== undefined) setState("historyCanRedo", response.canRedo);
}

async function loadSnapshots(): Promise<void> {
    try {
        const { body } = await api<Array<{ snapshotId: string }> | null>(endpoint("snapshots"));
        setState(
            "snapshotIds",
            (body ?? []).map((snapshot) => String(snapshot.snapshotId)),
        );
    } catch (failure) {
        if (!isUnavailable(failure)) throw failure;
        setState("snapshotIds", []);
    }
}

/** Loads the workspace history list; a 404 disables every history control. */
export async function loadHistory(): Promise<void> {
    const id = state.workspace?.workspaceId;
    if (!id) return;
    try {
        const { body } = await api<HistoryEntry[] | HistoryResponse | null>(endpoint("history"));
        applyHistory(body);
        setState("historyUnavailable", false);
        setState("historyState", `${state.historyEntries.length} changes`);
        await loadSnapshots();
    } catch (failure) {
        if (!isUnavailable(failure)) throw failure;
        setState("historyUnavailable", true);
        setState("historyCanUndo", false);
        setState("historyCanRedo", false);
        setState("historyState", "Unavailable on this host");
    }
}

async function changeHistory(verb: "undo" | "redo"): Promise<void> {
    const id = state.workspace?.workspaceId;
    if (!id) return;
    try {
        const { body } = await api<HistoryEntry[] | null>(endpoint(`history/${verb}`), {
            method: "POST",
            body: JSON.stringify({}),
        });
        const remaining = Array.isArray(body) ? body : [];
        applyHistory(remaining, verb === "undo" ? remaining.length > 0 : false);
        setState("historyState", `${state.historyEntries.length} changes`);
        toast(`${verb === "undo" ? "Undo" : "Redo"} complete.`);
    } catch (failure) {
        if (isUnavailable(failure)) {
            setState("historyUnavailable", true);
            toast("Unavailable on this host", "error");
            return;
        }
        toast(errorText(failure), "error");
    }
}

export function undoHistory(): Promise<void> {
    return changeHistory("undo");
}

export function redoHistory(): Promise<void> {
    return changeHistory("redo");
}

function rowsFromDiff(body: unknown): SnapshotRow[] {
    const rows: SnapshotRow[] = [];
    if (typeof body === "string") {
        for (const file of parseUnifiedDiff(body)) {
            rows.push({
                path: file.file,
                status: "modified",
                added: file.hunks.reduce(
                    (count, hunk) =>
                        count + hunk.lines.filter((line) => line.kind === "add").length,
                    0,
                ),
                removed: file.hunks.reduce(
                    (count, hunk) =>
                        count + hunk.lines.filter((line) => line.kind === "remove").length,
                    0,
                ),
            });
        }
        return rows;
    }
    const value = (body ?? {}) as Record<string, unknown>;
    for (const status of ["added", "removed", "modified"] as const) {
        const paths = Array.isArray(value[status]) ? (value[status] as unknown[]) : [];
        for (const raw of paths) rows.push({ path: String(raw), status });
    }
    return rows;
}

/** Loads the file-level diff for one snapshot. */
export async function loadDiff(snapshotId: string): Promise<void> {
    const id = state.workspace?.workspaceId;
    if (!id || !snapshotId) return;
    setState("snapshotId", snapshotId);
    try {
        const query = new URLSearchParams({ snapshotId });
        const { body } = await api<unknown>(endpoint(`diff?${query}`));
        setState("snapshotRows", rowsFromDiff(body));
        setState("snapshotUnavailable", false);
        setState("snapshotState", `${state.snapshotRows.length} files changed`);
    } catch (failure) {
        if (!isUnavailable(failure)) throw failure;
        setState("snapshotUnavailable", true);
        setState("snapshotRows", []);
        setState("snapshotState", "Unavailable on this host");
    }
}

/** Restores a whole snapshot, or a single file when a path is supplied. */
export async function restoreSnapshot(snapshotId: string, path?: string): Promise<void> {
    const id = state.workspace?.workspaceId;
    if (!id || !snapshotId) return;
    try {
        if (path)
            await api(endpoint("data/history/restore"), {
                method: "POST",
                body: JSON.stringify({ snapshotId, path, expectedVersion: "" }),
            });
        else
            await api(endpoint(`snapshots/${encodeURIComponent(snapshotId)}/restore`), {
                method: "POST",
            });
        await Promise.all([loadHistory(), loadDiff(snapshotId)]);
        toast(path ? `Restored ${path}.` : "Restored the whole tree.");
    } catch (failure) {
        if (isUnavailable(failure)) {
            setState("snapshotUnavailable", true);
            toast("Unavailable on this host", "error");
            return;
        }
        toast(errorText(failure), "error");
    }
}

/** Ctrl/Cmd+Z / Ctrl/Cmd+Shift+Z outside editable surfaces drive workspace history. */
export function installHistoryShortcuts(): void {
    document.addEventListener("keydown", (event) => {
        if (!(event.ctrlKey || event.metaKey) || event.altKey) return;
        const key = event.key.toLowerCase();
        if (key !== "z" && key !== "y") return;
        const target = event.target;
        if (
            target instanceof HTMLElement &&
            (target.isContentEditable ||
                ["INPUT", "TEXTAREA", "SELECT"].includes(target.tagName) ||
                target.closest(".cm-editor"))
        )
            return;
        event.preventDefault();
        if (key === "y" || event.shiftKey) void redoHistory();
        else void undoHistory();
    });
}
