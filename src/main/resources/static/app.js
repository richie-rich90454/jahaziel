/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
"use strict";

const POLL_MS = 3000;
const TICK_MS = 1000;
const STAGES = ["requested", "preparing", "booting", "ready"];
const TRANSITIONAL = new Set(["preparing", "booting"]);
// Ordered boot sub-steps reported live by the backend; the bar position is the
// index into this list, never an estimate. Declaration order is progress order.
const BOOT_STEPS = [
  "requested",
  "preparing",
  "extracting",
  "spawning",
  "waiting",
  "registering",
  "ready",
];
const BOOT_LABELS = {
  requested: "Requested",
  preparing: "Preparing workspace",
  extracting: "Extracting IDE runtime",
  spawning: "Spawning IDE server",
  waiting: "Waiting for IDE port",
  registering: "Registering gateway route",
  ready: "Ready",
};

async function api(path, options) {
  let response;
  try {
    response = await fetch(path, options);
  } catch (failure) {
    throw new Error("Network unreachable: " + failure.message);
  }
  if (response.status === 204) {
    return null;
  }
  let body = null;
  try {
    body = await response.json();
  } catch (ignored) {
    /* non-JSON error body */
  }
  if (!response.ok) {
    const detail =
      body && body.message && body.message !== "Unexpected error"
        ? body.message
        : "Request failed";
    const code = body && body.code ? body.code + ": " : "";
    throw new Error(response.status + " " + code + detail);
  }
  return body;
}

function toast(message, kind) {
  const region = document.getElementById("toasts");
  const note = document.createElement("div");
  note.className = "toast" + (kind === "error" ? " toast-error" : "");
  note.setAttribute("role", kind === "error" ? "alert" : "status");
  const text = document.createElement("span");
  text.textContent = message;
  const close = document.createElement("button");
  close.type = "button";
  close.className = "toast-close";
  close.setAttribute("aria-label", "Dismiss");
  close.textContent = "×";
  close.addEventListener("click", () => note.remove());
  note.append(text, close);
  region.append(note);
  window.setTimeout(() => note.remove(), 8000);
}

function elapsedSince(iso) {
  const seconds = Math.max(0, Math.floor((Date.now() - Date.parse(iso)) / 1000));
  if (seconds < 60) {
    return seconds + "s";
  }
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) {
    return minutes + "m " + (seconds % 60) + "s";
  }
  return Math.floor(minutes / 60) + "h " + (minutes % 60) + "m";
}

function stateBadge(state) {
  const badge = document.createElement("span");
  badge.className = "badge badge-" + state;
  badge.textContent = state;
  return badge;
}

function stepper(state) {
  const steps = document.createElement("ol");
  steps.className = "stepper";
  steps.setAttribute("aria-label", "Boot progress");
  const reached = STAGES.indexOf(state);
  const terminal = reached < 0 && state !== "active" && state !== "idle";
  STAGES.forEach((stage, index) => {
    const step = document.createElement("li");
    const current = !terminal && index === reached;
    const complete =
      !terminal && (reached < 0 || index < reached || state === "active" || state === "idle");
    step.className = "step" + (current ? " step-current" : "") + (complete ? " step-done" : "");
    step.setAttribute("aria-current", current ? "step" : "false");
    const dot = document.createElement("span");
    dot.className = "step-dot";
    dot.setAttribute("aria-hidden", "true");
    const label = document.createElement("span");
    label.className = "step-label";
    label.textContent = stage;
    step.append(dot, label);
    steps.append(step);
  });
  return steps;
}

function loadingBar(label) {
  const wrap = document.createElement("div");
  wrap.className = "loading";
  wrap.setAttribute("role", "progressbar");
  wrap.setAttribute("aria-label", label);
  const bar = document.createElement("div");
  bar.className = "loading-bar";
  bar.setAttribute("aria-hidden", "true");
  wrap.append(bar);
  return wrap;
}

function progressView(workspace) {
  const phase = workspace.bootPhase || null;
  const phaseAt = workspace.bootPhaseAt || null;
  const known = phase !== null && BOOT_STEPS.includes(phase);
  const wrap = document.createElement("div");
  wrap.className = "progress";
  if (!known) {
    wrap.append(loadingBar("Workspace " + workspace.state));
    const meta = document.createElement("p");
    meta.className = "meta";
    meta.append(
      document.createTextNode(
        "No live boot is reporting — the server restarted mid-boot, or the last boot died before"
          + " reporting. Press Retry start below. Last state change "
      ),
      elapsedNode(workspace.lastActivityAt),
      document.createTextNode(" ago.")
    );
    wrap.append(meta);
    return wrap;
  }
  const index = BOOT_STEPS.indexOf(phase);
  const percent = Math.round(((index + 1) / BOOT_STEPS.length) * 100);
  const bar = document.createElement("div");
  bar.className = "progress-track";
  bar.setAttribute("role", "progressbar");
  bar.setAttribute("aria-valuemin", "0");
  bar.setAttribute("aria-valuemax", "100");
  bar.setAttribute("aria-valuenow", String(percent));
  bar.setAttribute("aria-label", BOOT_LABELS[phase] || phase);
  const fill = document.createElement("div");
  fill.className = "progress-fill";
  fill.style.width = percent + "%";
  bar.append(fill);
  wrap.append(bar);
  const meta = document.createElement("p");
  meta.className = "meta";
  meta.append(
    document.createTextNode(
      (BOOT_LABELS[phase] || phase) + " — step " + (index + 1) + " of " + BOOT_STEPS.length + ", "
    ),
    elapsedNode(phaseAt || workspace.lastActivityAt),
    document.createTextNode(" in this step.")
  );
  if (phase === "extracting") {
    const note = document.createElement("span");
    note.className = "meta-note";
    note.textContent = " First boot unpacks the IDE runtime, allow a minute.";
    meta.append(note);
  }
  wrap.append(meta);
  return wrap;
}

function elapsedNode(iso) {
  const elapsed = document.createElement("span");
  elapsed.dataset.elapsed = iso;
  elapsed.textContent = elapsedSince(iso);
  return elapsed;
}

function actionButton(label, kind, handler, disabled) {
  const button = document.createElement("button");
  button.type = "button";
  button.className = "btn" + (kind ? " btn-" + kind : "");
  button.textContent = label;
  button.disabled = !!disabled;
  button.addEventListener("click", async () => {
    button.disabled = true;
    try {
      await handler();
      await render();
    } catch (failure) {
      toast(failure.message, "error");
      button.disabled = false;
    }
  });
  return button;
}

async function postStart(id) {
  await api("/api/v1/workspaces/" + id + "/start", { method: "POST" });
  toast("Start requested — watching boot progress.");
}

async function postStop(id) {
  await api("/api/v1/workspaces/" + id + "/stop", { method: "POST" });
}

async function postRestart(id) {
  await api("/api/v1/workspaces/" + id + "/restart", { method: "POST" });
  toast("Restart requested — watching boot progress.");
}

async function deleteWorkspace(id, name) {
  if (!window.confirm('Delete workspace "' + name + '"? Home files are kept.')) {
    return;
  }
  await api("/api/v1/workspaces/" + id + "?confirm=true", { method: "DELETE" });
}

function renderPorts(item, workspace) {
  const ports = workspace.ports || [];
  if (ports.length === 0) {
    const hint = document.createElement("p");
    hint.className = "hint";
    hint.textContent =
      workspace.state === "ready" || workspace.state === "active"
        ? "IDE is starting — its link appears here when the port registers."
        : "No ports registered. Start the workspace to open it in the browser.";
    item.append(hint);
    return;
  }
  const first = ports.find((entry) => entry.url);
  if (first) {
    const open = document.createElement("a");
    open.className = "btn btn-primary";
    open.href = first.url;
    open.target = "_blank";
    open.rel = "noopener";
    open.textContent = "Open IDE";
    item.append(open);
  }
  const list = document.createElement("ul");
  list.className = "ports";
  for (const entry of ports) {
    const row = document.createElement("li");
    if (entry.url) {
      const link = document.createElement("a");
      link.href = entry.url;
      link.target = "_blank";
      link.rel = "noopener";
      link.textContent = ":" + entry.port;
      row.append(link);
    } else {
      row.textContent = ":" + entry.port + " (no URL)";
    }
    list.append(row);
  }
  item.append(list);
}

function renderActions(item, workspace) {
  const actions = document.createElement("div");
  actions.className = "actions";
  const id = workspace.workspaceId;
  const state = workspace.state;
  if (state === "requested") {
    actions.append(actionButton("Start", "primary", () => postStart(id)));
  } else if (TRANSITIONAL.has(state)) {
    const starting = document.createElement("button");
    starting.type = "button";
    starting.className = "btn btn-primary";
    starting.disabled = true;
    const spinner = document.createElement("span");
    spinner.className = "spinner";
    spinner.setAttribute("aria-hidden", "true");
    starting.append(spinner, document.createTextNode("Starting…"));
    actions.append(starting);
    if (!workspace.bootPhase) {
      actions.append(actionButton("Retry start", "primary", () => postStart(id)));
    }
    actions.append(actionButton("Cancel", null, () => postStop(id)));
  } else if (state === "ready" || state === "active" || state === "idle") {
    actions.append(actionButton("Stop", null, () => postStop(id)));
    actions.append(actionButton("Restart", null, () => postRestart(id)));
  } else if (state === "suspended" || state === "failed") {
    actions.append(actionButton("Restart", "primary", () => postRestart(id)));
  }
  actions.append(
    actionButton("Delete", "danger", () => deleteWorkspace(id, workspace.name))
  );
  if (actions.hasChildNodes()) {
    item.append(actions);
  }
}

function renderCard(workspace) {
  const item = document.createElement("li");
  item.className = "card";
  const head = document.createElement("div");
  head.className = "card-head";
  const title = document.createElement("strong");
  title.className = "card-title";
  title.textContent = workspace.name;
  head.append(title, stateBadge(workspace.state));
  item.append(head);

  item.append(stepper(workspace.state));

  if (TRANSITIONAL.has(workspace.state)) {
    item.append(progressView(workspace));
  }

  if (workspace.state === "failed" && workspace.stateReason) {
    const reason = document.createElement("p");
    reason.className = "failure";
    reason.textContent = "Failed: " + workspace.stateReason;
    item.append(reason);
  }

  renderPorts(item, workspace);
  renderActions(item, workspace);
  return item;
}

async function render() {
  const list = document.getElementById("workspaces");
  const workspaces = await api("/api/v1/me/workspaces");
  list.replaceChildren();
  if (workspaces.length === 0) {
    const empty = document.createElement("li");
    empty.className = "empty";
    empty.textContent = "No workspaces yet — create one to get started.";
    list.append(empty);
  }
  let dot = document.getElementById("live-dot");
  for (const workspace of workspaces) {
    list.append(renderCard(workspace));
  }
  dot.classList.toggle("live-on", true);
  document.getElementById("updated").dataset.stamp = new Date().toISOString();
}

function tickElapsed() {
  const now = Date.now();
  document.querySelectorAll("[data-elapsed]").forEach((node) => {
    const millis = now - Date.parse(node.dataset.elapsed);
    const seconds = Math.max(0, Math.floor(millis / 1000));
    node.textContent = seconds < 60 ? seconds + "s" : Math.floor(seconds / 60) + "m " + (seconds % 60) + "s";
  });
  const updated = document.getElementById("updated");
  if (updated.dataset.stamp) {
    const seconds = Math.max(0, Math.floor((now - Date.parse(updated.dataset.stamp)) / 1000));
    updated.textContent = "Updated " + seconds + "s ago";
  }
}

async function main() {
  const user = await api("/api/v1/me").catch(() => null);
  if (user === null) {
    window.location.replace("/api/login");
    return;
  }
  document.getElementById("user").textContent = user.displayName + " <" + user.email + ">";
  const create = async () => {
    const input = document.getElementById("new-name");
    const name = input.value.trim();
    if (name === "") {
      toast("Give the workspace a name first.", "error");
      return;
    }
    await api("/api/v1/workspaces", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name: name }),
    });
    input.value = "";
    await render();
  };
  document.getElementById("create-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      await create();
    } catch (failure) {
      toast(failure.message, "error");
    }
  });
  const loop = async () => {
    try {
      await render();
    } catch (failure) {
      toast(failure.message, "error");
    }
  };
  await loop();
  window.setInterval(() => {
    if (!document.hidden) {
      loop();
    }
  }, POLL_MS);
  window.setInterval(tickElapsed, TICK_MS);
  document.addEventListener("visibilitychange", () => {
    if (!document.hidden) {
      loop();
    }
  });
}

main().catch((failure) => {
  toast("Failed to load: " + failure.message, "error");
});
