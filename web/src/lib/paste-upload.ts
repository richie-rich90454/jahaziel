///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { api, errorText } from "./api";
import { refreshFiles } from "./files";
import { t } from "./i18n";
import { state, toast } from "./store";

const MAX_BYTES = 2 * 1024 * 1024;
const TEXT_TYPES = /^(text\/|application\/(json|xml|javascript)|.*\+(json|xml)$)/i;
const IMAGE_TYPES = /^image\/(png|jpe?g|gif|webp|svg\+xml|bmp)$/i;

function workspaceId(): string | null {
    return state.workspace?.workspaceId ?? null;
}

async function saveText(path: string, content: string): Promise<void> {
    const id = workspaceId();
    if (!id) throw new Error("Open a workspace first.");
    const query = new URLSearchParams({ path });
    await api(`/api/v1/workspaces/${id}/files/document?${query}`, {
        method: "PUT",
        headers: { "If-None-Match": "*" },
        body: JSON.stringify({ content }),
    });
}

function uniqueName(name: string): string {
    const clean = name.replace(/[^a-zA-Z0-9._/-]/g, "_").replace(/^\/+/, "") || "pasted.txt";
    const stamp = Date.now().toString(36);
    const dot = clean.lastIndexOf(".");
    return dot > 0 ? `${clean.slice(0, dot)}-${stamp}${clean.slice(dot)}` : `${clean}-${stamp}.txt`;
}

function readAsDataUrl(blob: Blob): Promise<string> {
    return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(String(reader.result));
        reader.onerror = () => reject(reader.error ?? new Error("Could not read the file."));
        reader.readAsDataURL(blob);
    });
}

async function saveBlob(file: File): Promise<void> {
    if (file.size > MAX_BYTES) {
        toast(t("upload.limit"), "error");
        return;
    }
    const isText =
        TEXT_TYPES.test(file.type) ||
        /\.(txt|md|json|csv|html?|css|js|ts|py|java|go|rs|xml)$/i.test(file.name);
    const isImage =
        IMAGE_TYPES.test(file.type) || /\.(png|jpe?g|gif|webp|svg|bmp)$/i.test(file.name);
    if (!isText && !isImage) {
        toast(t("upload.limit"), "error");
        return;
    }
    if (isText && file.size <= MAX_BYTES) {
        await saveText(uniqueName(file.name), await file.text());
        return;
    }
    const dataUrl = await readAsDataUrl(file);
    const name = uniqueName(file.name.replace(/\.[^.]+$/, "")).replace(/\.txt$/, "") + ".html";
    await saveText(
        name,
        `<!doctype html><html><body><img src="${dataUrl}" alt="${file.name}" /></body></html>`,
    );
}

async function handleFiles(files: File[]): Promise<void> {
    if (!workspaceId() || files.length === 0) return;
    for (const file of files) {
        try {
            await saveBlob(file);
        } catch (failure) {
            toast(`${file.name}: ${errorText(failure)}`, "error");
        }
    }
    await refreshFiles();
    toast(`${t("upload.drop")}: ${files.length}`);
}

async function handlePaste(event: ClipboardEvent): Promise<void> {
    const items = event.clipboardData?.items;
    if (!items?.length || !workspaceId()) return;
    const files: File[] = [];
    for (const item of items) {
        if (item.kind !== "file") continue;
        const file = item.getAsFile();
        if (file) files.push(file);
    }
    if (files.length) {
        event.preventDefault();
        await handleFiles(files);
        return;
    }
    const text = event.clipboardData?.getData("text/plain");
    if (text && text.trim()) {
        event.preventDefault();
        await saveText(uniqueName("pasted.txt"), text);
        await refreshFiles();
        toast(t("upload.paste"));
    }
}

/** Wires paste and drag-and-drop file intake into a host element. */
export function installPasteUpload(host: HTMLElement): void {
    const overlay = document.createElement("div");
    overlay.className = "upload-overlay";
    overlay.hidden = true;
    overlay.textContent = t("upload.drop");
    (host.parentElement ?? host).append(overlay);

    host.addEventListener("dragover", (event) => {
        event.preventDefault();
        overlay.hidden = false;
        overlay.textContent = t("upload.drop");
    });
    host.addEventListener("dragleave", (event) => {
        if (event.relatedTarget instanceof Node && host.contains(event.relatedTarget)) return;
        overlay.hidden = true;
    });
    host.addEventListener("drop", (event) => {
        event.preventDefault();
        overlay.hidden = true;
        void handleFiles(Array.from(event.dataTransfer?.files ?? []));
    });
    host.addEventListener("paste", (event) => {
        void handlePaste(event as ClipboardEvent);
    });
}
