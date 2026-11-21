///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { errorText } from "../lib/api";
import { bindDisabled, byId, effect, html, on } from "../lib/dom";
import { subscribeLocale, t } from "../lib/i18n";
import { icon } from "../lib/icons";
import { toast } from "../lib/store";
import {
    createWorkspace,
    openWorkspace,
    promptClone,
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
        <button type="button" class="button button-primary">
            <span data-i18n="home.openWorkspace">${t("home.openWorkspace")}</span>
        </button>
    `;
    on(open, "click", () => {
        void openWorkspace(workspace);
    });
    actions.append(open);
    const clone = html`
        <button type="button" class="button button-quiet workspace-clone">
            <span data-i18n="home.clone">${t("home.clone")}</span>
        </button>
    `;
    on(clone, "click", () => {
        void promptClone(workspace).catch((failure) => toast(errorText(failure), "error"));
    });
    actions.append(clone);
    if (["requested", "suspended", "failed"].includes(workspace.state)) {
        const start = html`
            <button type="button" class="button button-quiet">
                <span data-i18n="home.start">${t("home.start")}</span>
            </button>
        `;
        on(start, "click", () => {
            void startWorkspaceFromCard(workspace);
        });
        actions.append(start);
    } else if (["ready", "active", "idle"].includes(workspace.state)) {
        const stop = html`
            <button type="button" class="button button-quiet">
                <span data-i18n="common.stop">${t("common.stop")}</span>
            </button>
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
            link.innerHTML = `${t("home.previewLink", { port: port.port })} ${icon("external")}`;
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
        <h1><span data-i18n="home.title">${t("home.title")}</span><br /><em data-i18n="home.titleEm">${t("home.titleEm")}</em></h1>
        <p class="intro-copy" data-i18n="home.intro">${t("home.intro")}</p>
      </section>
      <section class="workspace-section" aria-labelledby="workspace-heading">
        <div class="section-heading">
          <h2 id="workspace-heading" data-i18n="bar.workspaces">${t("bar.workspaces")}</h2>
          <div id="loading-state" class="loading-state"></div>
        </div>
        <ul id="workspace-list" class="workspace-list"></ul>
        <div id="empty-workspaces" class="empty-state" hidden>
          <h3 data-i18n="home.emptyTitle">${t("home.emptyTitle")}</h3>
          <p data-i18n="home.emptyCopy">${t("home.emptyCopy")}</p>
        </div>
      </section>
      <section class="create-section" aria-labelledby="create-heading">
        <div class="create-copy">
          <h2 id="create-heading" data-i18n="home.createTitle">${t("home.createTitle")}</h2>
          <p id="template-note" data-i18n="home.note">${t("home.note")}</p>
        </div>
        <div class="sample-projects" aria-label="${t("home.samples")}" data-i18n-aria="home.samples">
          <span class="sample-label" data-i18n="home.samples">${t("home.samples")}</span>
          <span class="sample-hint" data-i18n="home.samplesCopy">${t("home.samplesCopy")}</span>
          <div class="sample-actions">
            <button class="button button-quiet" type="button" data-sample="python"><span data-i18n="home.sample.python">${t("home.sample.python")}</span></button>
            <button class="button button-quiet" type="button" data-sample="node"><span data-i18n="home.sample.node">${t("home.sample.node")}</span></button>
            <button class="button button-quiet" type="button" data-sample="empty"><span data-i18n="home.sample.empty">${t("home.sample.empty")}</span></button>
          </div>
        </div>
        <form id="workspace-form" class="create-form">
          <label class="field-label" for="workspace-name" data-i18n="home.workspaceName">${t("home.workspaceName")}</label>
          <input id="workspace-name" class="input input-bordered" name="name" required maxlength="64" placeholder="e.g. orchid-dashboard" autocomplete="off" />
          <label class="field-label" for="template-select" data-i18n="home.starter">${t("home.starter")}</label>
          <select id="template-select" class="select select-bordered" name="template"></select>
          <button id="create-workspace" class="button button-primary" type="submit"><span data-i18n="home.createAction">${t("home.createAction")}</span></button>
        </form>
      </section>
      <footer class="home-footer"><span data-i18n="home.footer">${t("home.footer")}</span><span data-i18n="home.footerCopy">${t("home.footerCopy")}</span></footer>
    </main>
  `;
}

export function bindHome(): void {
    const list = byId("workspace-list");
    const renderCards = (): void => list.replaceChildren(...state.workspaces.map(workspaceCard));
    effect(renderCards);
    subscribeLocale(renderCards);

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

    for (const button of Array.from(
        document.querySelectorAll<HTMLButtonElement>("[data-sample]"),
    )) {
        on(button, "click", () => {
            const sample = button.dataset.sample ?? "empty";
            const name = byId<HTMLInputElement>("workspace-name");
            const starter = byId<HTMLSelectElement>("template-select");
            if (Array.from(starter.options).some((option) => option.value === sample))
                starter.value = sample;
            if (!name.value.trim()) name.value = `sample-${sample}-${Date.now().toString(36)}`;
            name.focus();
        });
    }

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
