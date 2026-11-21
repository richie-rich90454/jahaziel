///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { api, errorText } from "./api";
import { refreshFiles } from "./files";
import { t } from "./i18n";
import { escapeHtml } from "./markdown";
import { state, toast } from "./store";

const MAX_BYTES = 2 * 1024 * 1024;
const TEXT_TYPES = /^(text\/|application\/(json|xml|javascript)|.*\+(json|xml)$)/i;
const IMAGE_TYPES = /^image\/(png|jpe?g|gif|webp|svg\+xml|bmp)$/i;

let uploadUnavailableShown = false;

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
        `<!doctype html><html><body><img src="${dataUrl}" alt="${escapeHtml(file.name)}" /></body></html>`,
    );
}

function validate(file: File): string | null {
    if (file.size > MAX_BYTES) return `${file.name}: ${t("upload.limit")}`;
    const text =
        TEXT_TYPES.test(file.type) ||
        /\.(txt|md|json|csv|html?|css|js|ts|py|java|go|rs|xml)$/i.test(file.name);
    const image = IMAGE_TYPES.test(file.type) || /\.(png|jpe?g|gif|webp|svg|bmp)$/i.test(file.name);
    return text || image ? null : `${file.name}: ${t("upload.limit")}`;
}

/** Uploads each file as base64 JSON; a 404 keeps the local fallback. */
async function uploadBatch(files: File[]): Promise<boolean> {
    const id = workspaceId();
    if (!id) return false;
    let uploaded = 0;
    for (const file of files) {
        const dataUrl = await readAsDataUrl(file);
        const content = dataUrl.slice(dataUrl.indexOf(",") + 1);
        const response = await fetch(`/api/v1/workspaces/${id}/upload`, {
            method: "POST",
            credentials: "same-origin",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ path: uniqueName(file.name), content, encoding: "base64" }),
        });
        if (response.status === 404 || response.status === 405) {
            if (!uploadUnavailableShown) {
                uploadUnavailableShown = true;
                toast("Unavailable on this host — saving files directly.");
            }
            return uploaded > 0;
        }
        if (!response.ok) throw new Error(`${response.status} ${t("upload.limit")}`);
        uploaded += 1;
    }
    return uploaded > 0;
}

async function handleFiles(files: File[]): Promise<void> {
    if (!workspaceId() || files.length === 0) return;
    const errors = files.map(validate).filter((error): error is string => error !== null);
    if (errors.length) {
        for (const error of errors) toast(error, "error");
        return;
    }
    try {
        if (await uploadBatch(files)) {
            await refreshFiles();
            toast(`${t("upload.drop")}: ${files.length}`);
            return;
        }
    } catch (failure) {
        toast(errorText(failure), "error");
        return;
    }
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

/** Wires an explicit file-picker to the same validated upload path. */
export function installUploadInput(input: HTMLInputElement): void {
    input.addEventListener("change", () => {
        const files = Array.from(input.files ?? []);
        if (files.length) void handleFiles(files);
        input.value = "";
    });
}
