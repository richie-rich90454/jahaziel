///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { api, errorText } from "./api";
import { setState, state, toast, type FileEntry, type TabInfo } from "./store";
import { runtime } from "./runtime";
import {
    disposeImageDocument,
    imageDocuments,
    openDocument,
    renameImageDocument,
    updateSaveState,
} from "./editor";
import { refreshGitSafely } from "./git";
import { isSelected, toggleSelection } from "./bulk-files";

export async function refreshFiles(path = ".", parent?: HTMLElement): Promise<void> {
    const workspace = state.workspace;
    if (!workspace) return;
    const query = new URLSearchParams({ path });
    const { body: entries } = await api<FileEntry[]>(
        `/api/v1/workspaces/${workspace.workspaceId}/files?${query}`,
    );
    if (!parent) {
        setState("files", entries);
        return;
    }
    parent.replaceChildren(...entries.map((entry) => renderFile(entry)));
}

export function renderFile(entry: FileEntry): HTMLLIElement {
    const item = document.createElement("li");
    item.className = entry.directory ? "tree-directory" : "tree-file";
    const row = document.createElement("div");
    row.className = "tree-row";
    const toggle = document.createElement("button");
    toggle.type = "button";
    toggle.className = "tree-item";
    toggle.textContent = entry.directory ? `${entry.name} ▸` : entry.name;
    toggle.title = entry.path;
    if (entry.directory) {
        toggle.setAttribute("aria-expanded", "false");
        toggle.addEventListener("click", () => {
            const existing = item.querySelector(":scope > ul");
            if (existing) {
                existing.remove();
                toggle.textContent = `${entry.name} ▸`;
                toggle.setAttribute("aria-expanded", "false");
                return;
            }
            const children = document.createElement("ul");
            toggle.textContent = `${entry.name} ▾`;
            toggle.setAttribute("aria-expanded", "true");
            item.append(children);
            refreshFiles(entry.path, children).catch((failure) =>
                toast(errorText(failure), "error"),
            );
        });
    } else {
        const check = document.createElement("input");
        check.type = "checkbox";
        check.className = "tree-check";
        check.checked = isSelected(entry.path);
        check.setAttribute("aria-label", `Select ${entry.path}`);
        check.addEventListener("click", (event) => event.stopPropagation());
        check.addEventListener("change", () => toggleSelection(entry.path, check.checked));
        row.append(check);
        toggle.addEventListener("click", () => selectAndOpenFile(entry.path));
    }
    row.append(toggle);
    item.append(row);
    return item;
}

export function selectAndOpenFile(path: string): void {
    setState("selectedPath", path);
    setState("downloadDisabled", false);
    openDocument(path).catch((failure) => {
        const message = errorText(failure);
        if (
            message.includes("binary files cannot be opened") ||
            message.includes("too large to open")
        ) {
            toast(
                "This file cannot be edited as text. Use Download selected to download it.",
                "error",
            );
            return;
        }
        toast(message, "error");
    });
}

export function downloadSelected(): void {
    const workspace = state.workspace;
    const path = state.selectedPath;
    if (!workspace || !path) return;
    const query = new URLSearchParams({ path });
    const anchor = document.createElement("a");
    anchor.href = `/api/v1/workspaces/${workspace.workspaceId}/files/download?${query}`;
    anchor.download = "";
    anchor.rel = "noreferrer";
    document.body.append(anchor);
    anchor.click();
    anchor.remove();
}

export async function createFile(): Promise<void> {
    const workspace = state.workspace;
    if (!workspace) return;
    const path = window.prompt("New file path relative to the workspace root");
    if (!path?.trim()) return;
    const query = new URLSearchParams({ path: path.trim() });
    await api(`/api/v1/workspaces/${workspace.workspaceId}/files/document?${query}`, {
        method: "PUT",
        headers: { "If-None-Match": "*" },
        body: JSON.stringify({ content: "" }),
    });
    await refreshFiles();
    refreshGitSafely();
    await openDocument(path.trim());
}

export async function createFolder(): Promise<void> {
    const workspace = state.workspace;
    if (!workspace) return;
    const path = window.prompt("New folder path relative to the workspace root");
    if (!path?.trim()) return;
    await api(`/api/v1/workspaces/${workspace.workspaceId}/files/directory`, {
        method: "POST",
        body: JSON.stringify({ path: path.trim() }),
    });
    await refreshFiles();
    refreshGitSafely();
}

export async function renameSelected(): Promise<void> {
    const workspace = state.workspace;
    if (!workspace || !state.selectedPath) {
        toast("Select a file or folder first.", "error");
        return;
    }
    const newPath = window.prompt("New path relative to the workspace root", state.selectedPath);
    if (!newPath?.trim() || newPath === state.selectedPath) return;
    await api(`/api/v1/workspaces/${workspace.workspaceId}/files/rename`, {
        method: "POST",
        body: JSON.stringify({ path: state.selectedPath, newPath: newPath.trim() }),
    });
    const previousPath = state.selectedPath;
    const renamedPath = newPath.trim().replaceAll("\\", "/");
    for (const [oldPath, opened] of [...runtime.openDocuments.entries()]) {
        if (oldPath !== previousPath && !oldPath.startsWith(`${previousPath}/`)) continue;
        const updatedPath = `${renamedPath}${oldPath.substring(previousPath.length)}`;
        runtime.openDocuments.delete(oldPath);
        opened.path = updatedPath;
        runtime.openDocuments.set(updatedPath, opened);
        if (state.activePath === oldPath) setState("activePath", updatedPath);
    }
    if (imageDocuments.has(previousPath)) {
        renameImageDocument(previousPath, renamedPath);
        if (state.activePath === previousPath) setState("activePath", renamedPath);
    }
    setState("selectedPath", renamedPath);
    setState("tabs", (tabs) =>
        tabs.map((tab: TabInfo) => ({
            ...tab,
            path:
                tab.path === previousPath || tab.path.startsWith(`${previousPath}/`)
                    ? `${renamedPath}${tab.path.substring(previousPath.length)}`
                    : tab.path,
        })),
    );
    await refreshFiles();
    refreshGitSafely();
    if (state.activePath) setState("activeFileLabel", state.activePath);
}

export async function deleteSelected(): Promise<void> {
    const workspace = state.workspace;
    const path = state.selectedPath;
    if (!workspace || !path) {
        toast("Select a file or empty folder first.", "error");
        return;
    }
    if (!window.confirm(`Delete ${path}? Folders must be empty.`)) return;
    await api(
        `/api/v1/workspaces/${workspace.workspaceId}/files?${new URLSearchParams({ path })}`,
        { method: "DELETE" },
    );
    if (imageDocuments.has(path)) {
        disposeImageDocument(path);
        setState("tabs", (tabs) => tabs.filter((tab: TabInfo) => tab.path !== path));
        if (state.activePath === path) setState("activePath", null);
    }
    const opened = runtime.openDocuments.get(path);
    if (opened) {
        runtime.languageServices?.closeDocument(path);
        opened.view.destroy();
        opened.wrapper.remove();
        runtime.openDocuments.delete(path);
        setState("tabs", (tabs) => tabs.filter((tab: TabInfo) => tab.path !== path));
        if (state.activePath === path) setState("activePath", null);
    }
    setState("selectedPath", null);
    setState("downloadDisabled", true);
    await refreshFiles();
    refreshGitSafely();
    if (!state.activePath) setState("editorEmptyVisible", true);
    updateSaveState();
}
