///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

// Admin dashboard companion: health, workspaces, users, live logs.
"use strict";

import { pluginsMarkup, renderPlugins } from "./components/plugins";

function renderCapabilities(): void {
    const host = document.getElementById("admin-capabilities");
    if (host) {
        host.innerHTML = pluginsMarkup();
        renderPlugins(host);
        return;
    }
    const section = document.createElement("div");
    section.className = "admin-host";
    section.innerHTML = pluginsMarkup();
    document.body.append(section);
    renderPlugins(section);
}

async function adminApi<T>(path: string): Promise<T> {
    const response = await fetch(path);
    if (!response.ok) {
        throw new Error(response.status + " " + path);
    }
    return (await response.json()) as T;
}

async function adminMain(): Promise<void> {
    renderCapabilities();
    const health = await adminApi<{ status: string }>("/admin/v1/health").catch(() => null);
    const banner = document.getElementById("admin-health");
    if (banner !== null) {
        banner.textContent = health === null ? "unreachable" : health.status;
    }
    const logs = document.getElementById("admin-logs");
    if (logs !== null) {
        const stream = new EventSource("/admin/v1/logs/stream");
        stream.onmessage = (event: MessageEvent): void => {
            const line = document.createElement("div");
            line.textContent = event.data as string;
            logs.append(line);
        };
    }
}

adminMain().catch((failure: unknown) => {
    const banner = document.createElement("p");
    banner.textContent = "Admin failed to load: " + String(failure);
    document.body.prepend(banner);
});
