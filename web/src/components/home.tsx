///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { errorText } from "../lib/api";
import { bindDisabled, byId, effect, html, on } from "../lib/dom";
import { toast } from "../lib/store";
import {
    createWorkspace,
    openWorkspace,
    startWorkspaceFromCard,
    stopWorkspaceFromCard,
} from "../lib/controller";
import { state, type Workspace } from "../lib/store";

function workspaceCard(workspace: Workspace): HTMLLIElement {
    const card = html`
        <li class="workspace-card">
            <div class="workspace-identity">
                <span class="workspace-initial" aria-hidden="true"></span>
                <div class="workspace-details">
                    <h3></h3>
                    <p class="workspace-id"></p>
                </div>
            </div>
            <span class="status"></span>
            <div class="workspace-actions"></div>
        </li>
    ` as HTMLLIElement;
    const initial = card.querySelector<HTMLElement>(".workspace-initial")!;
    initial.textContent = workspace.name.slice(0, 1).toUpperCase();
    card.querySelector("h3")!.textContent = workspace.name;
    card.querySelector(".workspace-id")!.textContent = `ID: ${workspace.workspaceId.slice(0, 8)}`;
    const status = card.querySelector<HTMLElement>(".status")!;
    status.className = `status status-${workspace.state}`;
    status.textContent = workspace.state;

    const actions = card.querySelector<HTMLElement>(".workspace-actions")!;
    const open = html`
        <button type="button" class="button btn btn-primary">Open workspace</button>
    `;
    on(open, "click", () => {
        void openWorkspace(workspace);
    });
    actions.append(open);
    if (["requested", "suspended", "failed"].includes(workspace.state)) {
        const start = html`
            <button type="button" class="button button-quiet">Start</button>
        `;
        on(start, "click", () => {
            void startWorkspaceFromCard(workspace);
        });
        actions.append(start);
    } else if (["ready", "active", "idle"].includes(workspace.state)) {
        const stop = html`
            <button type="button" class="button button-quiet">Stop</button>
        `;
        on(stop, "click", () => {
            void stopWorkspaceFromCard(workspace);
        });
        actions.append(stop);
    }
    if (workspace.stateReason) {
        const reason = html`
            <p class="workspace-error"></p>
        `;
        reason.textContent = workspace.stateReason;
        card.append(reason);
    }
    if (workspace.ports.length) {
        const previews = html`
            <div class="workspace-previews"></div>
        `;
        for (const port of workspace.ports) {
            const link = html`
                <a target="_blank" rel="noreferrer"></a>
            ` as HTMLAnchorElement;
            link.href = port.url;
            link.textContent = `Preview :${port.port} ↗`;
            previews.append(link);
        }
        card.append(previews);
    }
    return card;
}

export function homeMarkup(): string {
    return `
    <main id="home-page" class="page page-home">
      <div class="toasts" id="toasts" aria-live="polite"></div>
      <section class="home-intro">
        <div>
          <p class="eyebrow">Your Linux workspaces</p>
          <h1>Make room<br /><em>to build.</em></h1>
          <p class="intro-copy">A focused development environment, running on your server and ready from any browser.</p>
        </div>
        <div class="intro-stamp" aria-hidden="true"><span>Linux</span><b>↗</b><span>Remote</span><i></i><span>Ready</span></div>
      </section>
      <section class="workspace-section" aria-labelledby="workspace-heading">
        <div class="section-heading">
          <div>
            <p class="eyebrow">Project spaces</p>
            <h2 id="workspace-heading">Workspaces <span id="workspace-count" class="count-mark">00</span></h2>
          </div>
          <div id="loading-state" class="loading-state">Connecting…</div>
        </div>
        <ul id="workspace-list" class="workspace-list"></ul>
        <div id="empty-workspaces" class="empty-state" hidden>
          <div class="empty-orbit" aria-hidden="true"><span></span><span></span><span></span></div>
          <h3>Your next project starts here.</h3>
          <p>Choose a starter and Jahaziel will lay down the first files.</p>
        </div>
      </section>
      <section class="create-section" aria-labelledby="create-heading">
        <div class="create-copy">
          <p class="eyebrow">A clean start</p>
          <h2 id="create-heading">Create a workspace</h2>
          <p id="template-note">Files are created in a private home directory for this workspace.</p>
        </div>
        <form id="workspace-form" class="create-form">
          <label class="field-label" for="workspace-name">Workspace name</label>
          <input id="workspace-name" class="input input-bordered" name="name" required maxlength="64" placeholder="e.g. orchid-dashboard" autocomplete="off" />
          <label class="field-label" for="template-select">Starter</label>
          <select id="template-select" class="select select-bordered" name="template"></select>
          <button id="create-workspace" class="button btn btn-primary" type="submit">Create workspace <span aria-hidden="true">↗</span></button>
        </form>
      </section>
      <footer class="home-footer"><span>Jahaziel, remote source IDE</span><span>Files stay on your server</span></footer>
    </main>
  `;
}

export function bindHome(): void {
    const list = byId("workspace-list");
    effect(() => {
        const workspaces = state.workspaces;
        list.replaceChildren(...workspaces.map(workspaceCard));
    });

    const select = byId<HTMLSelectElement>("template-select");
    effect(() => {
        const templates = state.templates;
        select.replaceChildren(
            ...templates.map((name) => {
                const option = document.createElement("option");
                option.value = name;
                option.textContent = name.replaceAll("-", " ");
                return option;
            }),
        );
        select.disabled = templates.length === 0;
        byId<HTMLButtonElement>("create-workspace").disabled = templates.length === 0;
    });

    on(byId("workspace-form"), "submit", (event) => {
        void createWorkspace(event as SubmitEvent).catch((failure) => {
            toast(errorText(failure), "error");
        });
    });

    const node = byId("loading-state");
    effect(() => {
        node.textContent = state.loadingState;
    });
    bindDisabled(byId<HTMLInputElement>("workspace-name"), () => state.templates.length === 0);
}
