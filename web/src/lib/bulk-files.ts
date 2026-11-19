///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { api, errorText } from "./api";
import { t } from "./i18n";
import { refreshFiles } from "./files";
import { state, toast, type DocumentBody } from "./store";

const selection = new Set<string>();
const listeners = new Set<() => void>();

type HistoryEntry =
    | { kind: "copy"; created: string[] }
    | { kind: "move"; moved: Array<{ from: string; to: string }> }
    | { kind: "delete"; removed: Array<{ path: string; content: string; version: string }> };

const history: HistoryEntry[] = [];

function notify(): void {
    for (const listener of listeners) listener();
}

export function subscribeSelection(listener: () => void): () => void {
    listeners.add(listener);
    return () => listeners.delete(listener);
}

export function isSelected(path: string): boolean {
    return selection.has(path);
}

export function selectedPaths(): string[] {
    return [...selection];
}

export function toggleSelection(path: string, selected?: boolean): void {
    const next = selected ?? !selection.has(path);
    if (next) selection.add(path);
    else selection.delete(path);
    notify();
}

export function clearSelection(): void {
    if (selection.size === 0) return;
    selection.clear();
    notify();
}

export function hasUndo(): boolean {
    return history.length > 0;
}

function workspaceId(): string {
    const id = state.workspace?.workspaceId;
    if (!id) throw new Error("Open a workspace first.");
    return id;
}

function destination(path: string, directory: string): string {
    const name = path.split("/").at(-1) || path;
    return !directory || directory === "." ? name : `${directory.replace(/\/$/, "")}/${name}`;
}

async function readDocument(path: string): Promise<{ content: string; version: string }> {
    const query = new URLSearchParams({ path });
    const { body } = await api<DocumentBody>(
        `/api/v1/workspaces/${workspaceId()}/files/document?${query}`,
    );
    return { content: body.content, version: body.version };
}

async function writeDocument(path: string, content: string): Promise<void> {
    const query = new URLSearchParams({ path });
    await api(`/api/v1/workspaces/${workspaceId()}/files/document?${query}`, {
        method: "PUT",
        headers: { "If-None-Match": "*" },
        body: JSON.stringify({ content }),
    });
}

async function renameDocument(from: string, to: string): Promise<void> {
    await api(`/api/v1/workspaces/${workspaceId()}/files/rename`, {
        method: "POST",
        body: JSON.stringify({ path: from, newPath: to }),
    });
}

async function removeDocument(path: string): Promise<void> {
    const query = new URLSearchParams({ path });
    await api(`/api/v1/workspaces/${workspaceId()}/files?${query}`, { method: "DELETE" });
}

async function finish(): Promise<void> {
    await refreshFiles();
    clearSelection();
}

export async function bulkCopy(directory: string): Promise<void> {
    const paths = selectedPaths();
    if (!paths.length) return;
    const created: string[] = [];
    for (const path of paths) {
        const target = destination(path, directory);
        try {
            const { content } = await readDocument(path);
            await writeDocument(target, content);
            created.push(target);
        } catch (failure) {
            toast(`${path}: ${errorText(failure)}`, "error");
        }
    }
    if (created.length) history.push({ kind: "copy", created });
    await finish();
}

export async function bulkMove(directory: string): Promise<void> {
    const paths = selectedPaths();
    if (!paths.length) return;
    const moved: Array<{ from: string; to: string }> = [];
    for (const path of paths) {
        const target = destination(path, directory);
        if (target === path) continue;
        try {
            await renameDocument(path, target);
            moved.push({ from: path, to: target });
        } catch (failure) {
            toast(`${path}: ${errorText(failure)}`, "error");
        }
    }
    if (moved.length) history.push({ kind: "move", moved });
    await finish();
}

export async function bulkDelete(): Promise<void> {
    const paths = selectedPaths();
    if (!paths.length) return;
    const removed: Array<{ path: string; content: string; version: string }> = [];
    for (const path of paths) {
        try {
            const document = await readDocument(path);
            await removeDocument(path);
            removed.push({ path, ...document });
        } catch (failure) {
            toast(`${path}: ${errorText(failure)}`, "error");
        }
    }
    if (removed.length) history.push({ kind: "delete", removed });
    await finish();
}

export async function undoBulkOperation(): Promise<void> {
    const last = history.pop();
    if (!last) return;
    try {
        if (last.kind === "copy") {
            for (const path of last.created) await removeDocument(path);
        } else if (last.kind === "move") {
            for (const entry of last.moved) await renameDocument(entry.to, entry.from);
        } else {
            for (const entry of last.removed) await writeDocument(entry.path, entry.content);
        }
        toast(`${t("bulk.undo")}: ${t("common.refresh")}`);
    } catch (failure) {
        history.push(last);
        toast(errorText(failure), "error");
    }
    await finish();
}

const CRC_TABLE = (() => {
    const table = new Uint32Array(256);
    for (let index = 0; index < 256; index += 1) {
        let value = index;
        for (let bit = 0; bit < 8; bit += 1)
            value = value & 1 ? 0xedb88320 ^ (value >>> 1) : value >>> 1;
        table[index] = value >>> 0;
    }
    return table;
})();

function crc32(bytes: Uint8Array): number {
    let value = 0xffffffff;
    for (const byte of bytes) value = CRC_TABLE[(value ^ byte) & 0xff] ^ (value >>> 8);
    return (value ^ 0xffffffff) >>> 0;
}

interface ZipPart {
    offset: number;
}

/** Builds a stored (uncompressed) ZIP archive entirely in the browser. */
export async function downloadZip(): Promise<void> {
    const paths = selectedPaths();
    if (!paths.length) return;
    const encoder = new TextEncoder();
    const localParts: Uint8Array[] = [];
    const centralParts: Uint8Array[] = [];
    const parts: ZipPart[] = [];
    let offset = 0;

    for (const path of paths) {
        let content = "";
        try {
            content = (await readDocument(path)).content;
        } catch (failure) {
            toast(`${path}: ${errorText(failure)}`, "error");
            continue;
        }
        const nameBytes = encoder.encode(path);
        const data = encoder.encode(content);
        const crc = crc32(data);
        const local = new Uint8Array(30 + nameBytes.length + data.length);
        const localView = new DataView(local.buffer);
        localView.setUint32(0, 0x04034b50, true);
        localView.setUint16(4, 20, true);
        localView.setUint16(6, 0, true);
        localView.setUint16(8, 0, true);
        localView.setUint32(14, crc, true);
        localView.setUint32(18, data.length, true);
        localView.setUint32(22, data.length, true);
        localView.setUint16(26, nameBytes.length, true);
        local.set(nameBytes, 30);
        local.set(data, 30 + nameBytes.length);

        const central = new Uint8Array(46 + nameBytes.length);
        const centralView = new DataView(central.buffer);
        centralView.setUint32(0, 0x02014b50, true);
        centralView.setUint16(4, 20, true);
        centralView.setUint16(6, 20, true);
        centralView.setUint16(8, 0, true);
        centralView.setUint16(10, 0, true);
        centralView.setUint32(16, crc, true);
        centralView.setUint32(20, data.length, true);
        centralView.setUint32(24, data.length, true);
        centralView.setUint16(28, nameBytes.length, true);
        centralView.setUint32(42, offset, true);
        central.set(nameBytes, 46);

        parts.push({ offset });
        localParts.push(local);
        centralParts.push(central);
        offset += local.length;
    }
    if (!parts.length) return;

    const centralSize = centralParts.reduce((sum, part) => sum + part.length, 0);
    const end = new Uint8Array(22);
    const endView = new DataView(end.buffer);
    endView.setUint32(0, 0x06054b50, true);
    endView.setUint16(8, parts.length, true);
    endView.setUint16(10, parts.length, true);
    endView.setUint32(12, centralSize, true);
    endView.setUint32(16, offset, true);

    const partsToBlob = (bytes: Uint8Array): ArrayBuffer =>
        bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer;
    const blob = new Blob([...localParts, ...centralParts, end].map(partsToBlob), {
        type: "application/zip",
    });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = "workspace-selection.zip";
    anchor.rel = "noreferrer";
    document.body.append(anchor);
    anchor.click();
    anchor.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 5000);
}
