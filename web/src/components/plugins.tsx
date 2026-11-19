///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { t } from "../lib/i18n";

interface Capability {
    id: string;
    name: string;
    available: boolean;
    builtIn: boolean;
    detail: string;
}

function detectCapabilities(): Capability[] {
    const canUseSocket = typeof WebSocket !== "undefined";
    const canUseClipboard = typeof ClipboardEvent !== "undefined";
    const canUseFiles = typeof FileReader !== "undefined" && typeof Blob !== "undefined";
    const canUseStorage = (() => {
        try {
            localStorage.setItem("jahaziel:probe", "1");
            localStorage.removeItem("jahaziel:probe");
            return true;
        } catch {
            return false;
        }
    })();
    return [
        {
            id: "files",
            name: "Workspace files",
            available: true,
            builtIn: true,
            detail: "GET / PUT with ETag checks",
        },
        {
            id: "search",
            name: "Workspace search",
            available: true,
            builtIn: true,
            detail: "Server-side content search",
        },
        {
            id: "git-status",
            name: "Git status & diff",
            available: true,
            builtIn: true,
            detail: "Status, diff, stage, unstage, commit",
        },
        {
            id: "git-history",
            name: "Git history, branches & blame",
            available: false,
            builtIn: true,
            detail: "No history/branches/blame endpoint on this host",
        },
        {
            id: "git-hunks",
            name: "Per-hunk staging",
            available: false,
            builtIn: true,
            detail: "Host exposes whole-file staging only",
        },
        {
            id: "debug",
            name: "Debugger (DAP)",
            available: canUseSocket,
            builtIn: true,
            detail: "Launch, attach, breakpoints, logpoints",
        },
        {
            id: "bulk",
            name: "Bulk file operations",
            available: canUseFiles,
            builtIn: true,
            detail: "Copy, move, delete, ZIP export, undo",
        },
        {
            id: "upload",
            name: "Paste & drop upload",
            available: canUseClipboard && canUseFiles,
            builtIn: true,
            detail: "Size and type limited",
        },
        {
            id: "markdown",
            name: "Markdown preview",
            available: true,
            builtIn: true,
            detail: "Rendered locally, never sent to the server",
        },
        {
            id: "theme",
            name: "Themes & locales",
            available: canUseStorage,
            builtIn: true,
            detail: "Light, dark, high contrast; 5 locales",
        },
        {
            id: "plugins",
            name: "Plugin loader",
            available: false,
            builtIn: false,
            detail: "No plugin catalogue endpoint on this host",
        },
    ];
}

export function pluginsMarkup(): string {
    return `
    <section class="capabilities" aria-labelledby="capabilities-heading">
      <h2 id="capabilities-heading">${t("plugins.heading")}</h2>
      <ul id="capability-list" class="capability-list"></ul>
    </section>
  `;
}

export function renderPlugins(host: HTMLElement): void {
    const list = host.querySelector<HTMLUListElement>("#capability-list");
    if (!list) return;
    list.replaceChildren(
        ...detectCapabilities().map((capability) => {
            const item = document.createElement("li");
            item.className = `capability${capability.available ? "" : " capability-unavailable"}`;
            const name = document.createElement("span");
            name.className = "capability-name";
            name.textContent = capability.name;
            const state = document.createElement("span");
            state.className = "capability-state";
            state.textContent = capability.available
                ? `${t("plugins.available")} · ${capability.builtIn ? t("plugins.builtin") : t("plugins.loaded")}`
                : t("plugins.unavailable");
            const detail = document.createElement("small");
            detail.className = "capability-detail";
            detail.textContent = capability.detail;
            item.append(name, state, detail);
            return item;
        }),
    );
}
