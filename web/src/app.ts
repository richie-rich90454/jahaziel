// Copyright 2026 The Jahaziel Authors. SPDX-License-Identifier: Apache-2.0.
// Browser dashboard companion: lists the user's workspaces next to the IDE link.
// Served statically; the control plane API does the rest.
"use strict";

async function workspaces() {
  const response = await fetch("/api/v1/me/workspaces");
  if (!response.ok) {
    throw new Error("workspaces: " + response.status);
  }
  return response.json();
}

async function main() {
  const list = document.getElementById("workspaces");
  if (list === null) {
    return;
  }
  const items = await workspaces();
  for (const workspace of items) {
    const entry = document.createElement("li");
    const link = document.createElement("a");
    link.href = workspace.urls.ide;
    link.textContent = workspace.name + " (" + workspace.state + ")";
    entry.append(link);
    list.append(entry);
  }
}

main().catch((failure) => console.error(failure));
