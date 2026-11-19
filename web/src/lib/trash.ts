///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { api, errorText, isUnavailable } from "./api";
import { refreshFiles } from "./files";
import { setState, state, toast, type TrashEntry } from "./store";

function endpoint(): string {
    const id = state.workspace?.workspaceId;
    if (!id) throw new Error("Open a workspace first.");
    return `/api/v1/workspaces/${id}/trash`;
}

function markUnavailable(): void {
    setState("trashUnavailable", true);
    setState("trashState", "Unavailable on this host");
}

export async function loadTrash(): Promise<void> {
    const id = state.workspace?.workspaceId;
    if (!id) return;
    try {
        const { body } = await api<TrashEntry[] | { items?: TrashEntry[] } | null>(endpoint());
        const items = Array.isArray(body) ? body : (body?.items ?? []);
        setState("trashItems", items);
        setState("trashUnavailable", false);
        setState("trashState", `${items.length} deleted`);
    } catch (failure) {
        if (!isUnavailable(failure)) throw failure;
        markUnavailable();
    }
}

async function mutate(path: string, init: RequestInit, message: string): Promise<void> {
    const id = state.workspace?.workspaceId;
    if (!id) return;
    try {
        await api(`${endpoint()}${path}`, init);
        await Promise.all([loadTrash(), refreshFiles()]);
        toast(message);
    } catch (failure) {
        if (isUnavailable(failure)) {
            markUnavailable();
            toast("Unavailable on this host", "error");
            return;
        }
        toast(errorText(failure), "error");
    }
}

export function restoreTrash(entry: TrashEntry): Promise<void> {
    return mutate(
        "",
        { method: "POST", body: JSON.stringify({ action: "restore", itemId: entry.id }) },
        `Restored ${entry.path}.`,
    );
}

export function deleteTrash(entry: TrashEntry): Promise<void> {
    const query = new URLSearchParams({ itemId: entry.id });
    return mutate(`?${query}`, { method: "DELETE" }, `Deleted ${entry.path} permanently.`);
}

export function emptyTrash(): Promise<void> {
    return mutate("", { method: "DELETE" }, "Trash emptied.");
}
