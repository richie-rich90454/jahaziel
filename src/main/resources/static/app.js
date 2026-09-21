/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
async function api(path, options) {
  const response = await fetch(path, options);
  if (!response.ok) {
    throw new Error(response.status + " " + path);
  }
  if (response.status === 204) {
    return null;
  }
  return response.json();
}

async function main() {
  const user = await api("/api/v1/me").catch(() => null);
  if (user === null) {
    window.location.replace("/api/login");
    return;
  }
  document.getElementById("user").textContent = user.displayName + " <" + user.email + ">";
  const list = document.getElementById("workspaces");
  const render = async () => {
    list.replaceChildren();
    const workspaces = await api("/api/v1/me/workspaces");
    for (const workspace of workspaces) {
      const item = document.createElement("li");
      const title = document.createElement("strong");
      title.textContent = workspace.name + " (" + workspace.state + ")";
      item.append(title);

      if (workspace.ports.length === 0) {
        const hint = document.createElement("p");
        hint.textContent = "No ports registered. Register one to open it in the browser.";
        item.append(hint);
      }
      for (const entry of workspace.ports) {
        const link = document.createElement("a");
        link.href = entry.url;
        const label = ":" + entry.port;
        link.textContent = label;
        link.target = "_blank";
        item.append(" ", link);
      }

      const start = document.createElement("button");
      start.type = "button";
      start.textContent = "Start";
      start.addEventListener("click", async () => {
        await api("/api/v1/workspaces/" + workspace.workspaceId + "/start", { method: "POST" });
        await render();
      });

      const del = document.createElement("button");
      del.type = "button";
      del.textContent = "Delete";
      del.addEventListener("click", async () => {
        await api("/api/v1/workspaces/" + workspace.workspaceId + "?confirm=true", {
          method: "DELETE",
        });
        await render();
      });
      item.append(" ", start, " ", del);
      list.append(item);
    }
  };
  document.getElementById("create").addEventListener("click", async () => {
    const name = window.prompt("Workspace name:");
    if (name === null || name === "") {
      return;
    }
    await api("/api/v1/workspaces", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name: name }),
    });
    await render();
  });
  await render();
}

main().catch((failure) => {
  const banner = document.createElement("p");
  banner.textContent = "Failed to load: " + failure.message;
  document.body.prepend(banner);
});
