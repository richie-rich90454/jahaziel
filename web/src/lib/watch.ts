///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { api, errorText, isUnavailable } from "./api";
import { subscribeSelection, selectedPaths } from "./bulk-files";
import { byId, ownedEffect } from "./dom";
import { applyExternalChange } from "./editor";
import { getTheme, setTheme } from "./theme";
import { setState, state, toast, type DocumentBody } from "./store";
import { refreshFiles } from "./files";

const CHANNEL = "jahaziel-workspace";
const POLL_MS = 8000;

interface WatchChange {
    path: string;
    kind?: string;
    modifiedAt: number;
}

let since = 0;
let pollTimer: number | null = null;
let channel: BroadcastChannel | null = null;
let localId = "";
let applyingRemote = false;
let stopSyncEffects: (() => void) | null = null;
let unsubscribeSelection: (() => void) | null = null;
const peers = new Map<string, { name: string; at: number }>();
const pending = new Map<string, { content: string; version: string }>();

function endpoint(since: string): string {
    const id = state.workspace?.workspaceId;
    if (!id) throw new Error("Open a workspace first.");
    return `/api/v1/workspaces/${id}/watch?${new URLSearchParams({ since })}`;
}

function showBanner(paths: string[]): void {
    setState("watchPaths", paths);
    setState("watchBannerVisible", true);
    const node = byId("watch-message");
    if (node) node.textContent = `External change detected in: ${paths.join(", ")}`;
}

async function loadChanged(path: string): Promise<void> {
    const id = state.workspace?.workspaceId;
    if (!id) return;
    const query = new URLSearchParams({ path });
    const { body, response } = await api<DocumentBody>(
        `/api/v1/workspaces/${id}/files/document?${query}`,
    );
    if (state.workspace?.workspaceId !== id) return;
    const version = response.headers.get("ETag") || body.version;
    pending.set(path, { content: body.content, version });
    applyExternalChange(path, body.content, version, false);
}

async function poll(): Promise<void> {
    const workspaceId = state.workspace?.workspaceId;
    if (!workspaceId) return;
    try {
        const { body } = await api<WatchChange[] | null>(endpoint(String(since)));
        if (state.workspace?.workspaceId !== workspaceId) return;
        const changes = Array.isArray(body) ? body : [];
        for (const change of changes)
            if (Number.isFinite(change.modifiedAt))
                since = Math.max(since, Number(change.modifiedAt));
        setState("watchUnavailable", false);
        const dirty = changes.filter((change) =>
            state.tabs.some((tab) => tab.path === change.path && tab.dirty),
        );
        if (dirty.length) showBanner(dirty.map((change) => change.path));
        for (const change of changes) {
            if (change.kind === "DELETED") {
                void refreshFiles().catch(() => undefined);
                continue;
            }
            try {
                await loadChanged(change.path);
            } catch (failure) {
                if (!isUnavailable(failure)) toast(errorText(failure), "error");
            }
        }
    } catch (failure) {
        if (!isUnavailable(failure)) return;
        setState("watchUnavailable", true);
        setState("watchState", "Unavailable on this host");
        stopWatching();
    }
}

export function startWatching(): void {
    if (pollTimer !== null) return;
    localId = `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 7)}`;
    since = 0;
    setState("watchUnavailable", false);
    setState("watchState", "Watching");
    pollTimer = window.setInterval(() => void poll().catch(() => undefined), POLL_MS);
    installCrossTabSync();
}

export function stopWatching(): void {
    if (pollTimer !== null) window.clearInterval(pollTimer);
    pollTimer = null;
    stopSyncEffects?.();
    stopSyncEffects = null;
    unsubscribeSelection?.();
    unsubscribeSelection = null;
    channel?.close();
    channel = null;
}

/** Applies external changes after an explicit user decision; drafts are never overwritten silently. */
export function reloadExternalChanges(discardDrafts: boolean): void {
    for (const [path, change] of pending) {
        if (!discardDrafts && state.tabs.some((tab) => tab.path === path && tab.dirty)) continue;
        applyExternalChange(path, change.content, change.version, true);
    }
    pending.clear();
    setState("watchBannerVisible", false);
    setState("watchPaths", []);
    void refreshFiles().catch(() => undefined);
}

interface SyncMessage {
    type: "sync";
    sender: string;
    workspaceId: string;
    name: string;
    activePath?: string | null;
    selectedPath?: string | null;
    selection?: string[];
    theme?: string;
    at: number;
}

function broadcast(partial: Partial<SyncMessage>): void {
    if (!channel || applyingRemote || !state.workspace) return;
    const message: SyncMessage = {
        type: "sync",
        sender: localId,
        workspaceId: state.workspace.workspaceId,
        name: state.user?.email ?? "Guest",
        at: Date.now(),
        ...partial,
    };
    channel.postMessage(message);
}

function applyRemote(message: SyncMessage): void {
    if (message.sender === localId || message.workspaceId !== state.workspace?.workspaceId) return;
    applyingRemote = true;
    try {
        peers.set(message.sender, { name: message.name, at: message.at });
        for (const [id, peer] of peers) if (message.at - peer.at > 60_000) peers.delete(id);
        setState(
            "presence",
            [...peers.values()].map((peer) => peer.name),
        );
        if (message.theme && message.theme !== getTheme())
            setTheme(message.theme as ReturnType<typeof getTheme>);
        if (message.activePath && message.activePath !== state.activePath) {
            const change = pending.get(message.activePath);
            if (change)
                applyExternalChange(message.activePath, change.content, change.version, false);
        }
        broadcast({
            activePath: state.activePath,
            selectedPath: state.selectedPath,
            selection: selectedPaths(),
        });
    } finally {
        applyingRemote = false;
    }
}

function installCrossTabSync(): void {
    if (channel || typeof BroadcastChannel === "undefined") return;
    channel = new BroadcastChannel(CHANNEL);
    channel.onmessage = (event: MessageEvent<SyncMessage>) => {
        if (event.data?.type === "sync") applyRemote(event.data);
    };
    stopSyncEffects = ownedEffect(() => {
        void state.activePath;
        void state.selectedPath;
        broadcast({ activePath: state.activePath, selectedPath: state.selectedPath });
    });
    unsubscribeSelection = subscribeSelection(() => broadcast({ selection: selectedPaths() }));
    broadcast({
        activePath: state.activePath,
        selectedPath: state.selectedPath,
        selection: selectedPaths(),
        theme: getTheme(),
    });
}
