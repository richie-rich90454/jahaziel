///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

// Copyright 2026 The Jahaziel Authors. SPDX-License-Identifier: Apache-2.0.
// Browser dashboard companion: lists the user's workspaces and their port links.
// Served statically; the control plane API does the rest.
"use strict";

interface PortEntry {
  port: number;
  protocol: string;
  url: string;
  registeredAt: string;
}

interface WorkspaceEntry {
  workspaceId: string;
  name: string;
  state: string;
  ports: PortEntry[];
}

async function api<T>(path: string, options?: RequestInit): Promise<T | null> {
  const response = await fetch(path, options);
  if (!response.ok) {
    throw new Error(response.status + " " + path);
  }
  if (response.status === 204) {
    return null;
  }
  return (await response.json()) as T;
}

async function main(): Promise<void> {
  const user = await api<{ displayName: string; email: string }>("/api/v1/me").catch(() => null);
  if (user === null) {
    window.location.replace("/api/login");
    return;
  }
  const banner = document.getElementById("user");
  if (banner !== null) {
    banner.textContent = user.displayName + " <" + user.email + ">";
  }
  const list = document.getElementById("workspaces");
  if (list === null) {
    return;
  }
  const render = async (): Promise<void> => {
    list.replaceChildren();
    const workspaces = await api<WorkspaceEntry[]>("/api/v1/me/workspaces");
    for (const workspace of workspaces ?? []) {
      const item = document.createElement("li");
      const title = document.createElement("strong");
      title.textContent = workspace.name + " (" + workspace.state + ")";
      item.append(title);
      for (const entry of workspace.ports) {
        const link = document.createElement("a");
        link.href = entry.url;
        link.textContent = ":" + entry.port;
        link.target = "_blank";
        item.append(" ", link);
      }
      list.append(item);
    }
  };
  await render();
}

main().catch((failure: unknown) => {
  const banner = document.createElement("p");
  banner.textContent = "Failed to load: " + String(failure);
  document.body.prepend(banner);
});
