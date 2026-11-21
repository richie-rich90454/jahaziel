///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { errorText } from "./lib/api";
import {
    backToWorkspaces,
    promptClone,
    redoHistory,
    setPanel,
    toggleWorkspace,
    undoHistory,
} from "./lib/controller";
import { byId, effect, on } from "./lib/dom";
import { getLocale, LOCALE_LABELS, LOCALES, setLocale, subscribeLocale, t } from "./lib/i18n";
import { icon } from "./lib/icons";
import { getTheme, setTheme, subscribeTheme, THEMES, THEME_LABELS } from "./lib/theme";
import { state, toast } from "./lib/store";
import { editorMarkup, bindEditor } from "./components/editor";
import { bindHome, homeMarkup } from "./components/home";
import { bindPreview, previewMarkup } from "./components/preview";
import { bindQuickOpen, quickOpenMarkup } from "./components/quick-open";
import { bindServicePanels, servicePanelsMarkup } from "./components/service-panels";
import { bindSidebar, sidebarMarkup } from "./components/sidebar";
import { bindTerminal, terminalMarkup } from "./components/terminal";
import { bindTour, tourMarkup } from "./components/tour";
import {
    bindWorkspaceTools,
    watchBannerMarkup,
    workspaceToolsMarkup,
} from "./components/workspace-tools";

export function App(): HTMLElement {
    const root = document.createElement("div");
    root.className = "app-root";
    root.innerHTML = `
    <header class="topbar">
      <div class="brand-lockup">
        <span class="brand-mark" aria-hidden="true">J</span>
        <span class="brand-name">jahaziel<span class="brand-dot">.</span></span>
        <span class="brand-divider"></span>
        <span class="brand-product">Remote source IDE</span>
      </div>
      <label class="locale-control" for="locale-select">
        <span class="sr-only">${t("bar.language")}</span>
        <select id="locale-select" aria-label="${t("bar.language")}">
          ${LOCALES.map(
              (locale) => `<option value="${locale}">${LOCALE_LABELS[locale]}</option>`,
          ).join("")}
        </select>
      </label>
      <div id="user-badge" class="user-badge" hidden>
        <span class="user-presence"></span><span id="user-name"></span><span id="user-email" class="user-email"></span>
      </div>
      <a id="login-link" class="login-link" href="/api/login" hidden><span data-i18n="bar.signIn">${t("bar.signIn")}</span> ${icon("external")}</a>
    </header>

    ${homeMarkup()}

    <main id="workbench-page" class="page page-workbench" hidden>
      <div class="toasts" id="workbench-toasts" aria-live="polite"></div>
      <div class="workbench-bar">
        <button id="back-home" class="icon-button back-button" type="button" data-i18n-aria="bar.back" aria-label="${t("bar.back")}">${icon("back")}</button>
        <div class="breadcrumb"><span>${t("bar.workspaces")}</span><b>/</b><strong id="workbench-title"></strong></div>
        <span id="workbench-state" class="status status-requested">requested</span>
        <button id="bar-history-undo" class="icon-button history-control" type="button" data-i18n-title="bar.undo" data-i18n-aria="bar.undo" title="${t("bar.undo")}" aria-label="${t("bar.undo")}" disabled>${icon("undo")}</button>
        <button id="bar-history-redo" class="icon-button history-control" type="button" data-i18n-title="bar.redo" data-i18n-aria="bar.redo" title="${t("bar.redo")}" aria-label="${t("bar.redo")}" disabled>${icon("redo")}</button>
        <div class="bar-spacer"></div>
        <div id="preview-links" class="preview-links"></div>
        <button id="bar-clone" class="button bar-clone" type="button" data-i18n-title="bar.cloneTitle" title="${t("bar.cloneTitle")}"><span data-i18n="bar.clone">${t("bar.clone")}</span></button>
        <label class="theme-control" for="theme-select">
          <span class="sr-only">${t("bar.theme")}</span>
          <select id="theme-select" aria-label="${t("bar.theme")}">
            ${THEMES.map(
                (theme) => `<option value="${theme}">${THEME_LABELS[theme]}</option>`,
            ).join("")}
          </select>
        </label>
        <button id="refresh-preview-ports" class="button preview-refresh" type="button" data-i18n-title="bar.checkPorts" title="${t("bar.checkPorts")}">${t("bar.checkPorts")}</button>
        <button id="runtime-toggle" class="button" type="button">${t("bar.startWorkspace")}</button>
      </div>
      ${watchBannerMarkup()}
      <nav class="workbench-panel-nav" aria-label="${t("nav.panels")}" data-i18n-aria="nav.panels">
        <button type="button" data-panel="files" aria-pressed="true"><span data-i18n="nav.files">${t("nav.files")}</span></button>
        <button type="button" data-panel="editor" aria-pressed="false"><span data-i18n="nav.editor">${t("nav.editor")}</span></button>
        <button type="button" data-panel="run" aria-pressed="false"><span data-i18n="nav.run">${t("nav.run")}</span></button>
        <button type="button" data-panel="languages" aria-pressed="false"><span data-i18n="nav.languages">${t("nav.languages")}</span></button>
        <button type="button" data-panel="debug" aria-pressed="false"><span data-i18n="nav.debug">${t("nav.debug")}</span></button>
        <button type="button" data-panel="git" aria-pressed="false"><span data-i18n="nav.git">${t("nav.git")}</span></button>
        <button type="button" data-panel="terminal" aria-pressed="false"><span data-i18n="nav.terminal">${t("nav.terminal")}</span></button>
        <button type="button" data-panel="preview" aria-pressed="false"><span data-i18n="nav.preview">${t("nav.preview")}</span></button>
        <button type="button" class="panel-more-toggle" id="panel-more" aria-expanded="false" aria-controls="panel-more-group"><span data-i18n="nav.more">${t("nav.more")}</span></button>
        <div id="panel-more-group" class="panel-more-group" hidden>
          <button type="button" data-panel="collaboration" aria-pressed="false"><span data-i18n="panel.collaboration">${t("panel.collaboration")}</span></button>
          <button type="button" data-panel="agent" aria-pressed="false"><span data-i18n="panel.agent">${t("panel.agent")}</span></button>
          <button type="button" data-panel="data" aria-pressed="false"><span data-i18n="panel.data">${t("panel.data")}</span></button>
          <button type="button" data-panel="club" aria-pressed="false"><span data-i18n="panel.club">${t("panel.club")}</span></button>
          <button type="button" data-panel="ops" aria-pressed="false"><span data-i18n="panel.ops">${t("panel.ops")}</span></button>
          <button type="button" data-panel="history" aria-pressed="false"><span data-i18n="panel.history">${t("panel.history")}</span></button>
          <button type="button" data-panel="trash" aria-pressed="false"><span data-i18n="panel.trash">${t("panel.trash")}</span></button>
          <button type="button" data-panel="diff" aria-pressed="false"><span data-i18n="panel.diff">${t("panel.diff")}</span></button>
          <button type="button" data-panel="clone" aria-pressed="false"><span data-i18n="panel.clone">${t("panel.clone")}</span></button>
          <button type="button" data-panel="config" aria-pressed="false"><span data-i18n="panel.config">${t("panel.config")}</span></button>
          <button type="button" data-panel="flags" aria-pressed="false"><span data-i18n="panel.flags">${t("panel.flags")}</span></button>
        </div>
      </nav>
      <div class="workbench-layout">
        ${sidebarMarkup()}
        <section class="editor-column" aria-label="Code editor">
          ${editorMarkup()}
          ${terminalMarkup()}
          ${previewMarkup()}
          ${servicePanelsMarkup()}
          ${workspaceToolsMarkup()}
          <footer class="statusbar"><span>Jahaziel</span><span id="status-language"></span></footer>
        </section>
      </div>
    </main>

    ${tourMarkup()}
    ${quickOpenMarkup()}
  `;
    return root;
}

function bindAppearanceControls(): void {
    const themeSelect = byId<HTMLSelectElement>("theme-select");
    const localeSelect = byId<HTMLSelectElement>("locale-select");
    themeSelect.value = getTheme();
    localeSelect.value = getLocale();
    on(themeSelect, "change", () => setTheme(themeSelect.value as ReturnType<typeof getTheme>));
    on(localeSelect, "change", () => setLocale(localeSelect.value as ReturnType<typeof getLocale>));
    subscribeTheme(() => {
        themeSelect.value = getTheme();
    });
    subscribeLocale(() => {
        localeSelect.value = getLocale();
        byId("back-home").setAttribute("aria-label", t("bar.back"));
        const localeLabel = t("bar.language");
        localeSelect.setAttribute("aria-label", localeLabel);
        const themeLabel = t("bar.theme");
        byId("theme-select").setAttribute("aria-label", themeLabel);
        byId("refresh-preview-ports").textContent = t("bar.checkPorts");
    });
}

export function bindApp(): void {
    bindHome();
    bindSidebar();
    bindEditor();
    bindTerminal();
    bindPreview();
    bindQuickOpen();
    bindServicePanels();
    bindWorkspaceTools();
    bindTour();
    bindAppearanceControls();

    for (const button of Array.from(
        document.querySelectorAll<HTMLButtonElement>(".workbench-panel-nav [data-panel]"),
    )) {
        on(button, "click", () => setPanel(button.dataset.panel as typeof state.activePanel));
    }
    const moreToggle = byId<HTMLButtonElement>("panel-more");
    const moreGroup = byId("panel-more-group");
    on(moreToggle, "click", () => {
        const expanded = moreToggle.getAttribute("aria-expanded") === "true";
        moreToggle.setAttribute("aria-expanded", String(!expanded));
        moreGroup.hidden = expanded;
    });
    const advancedPanels = new Set([
        "collaboration",
        "agent",
        "data",
        "club",
        "ops",
        "history",
        "trash",
        "diff",
        "clone",
        "config",
        "flags",
    ]);
    effect(() => {
        if (advancedPanels.has(state.activePanel)) {
            moreGroup.hidden = false;
            moreToggle.setAttribute("aria-expanded", "true");
        }
    });
    on(byId("back-home"), "click", () => backToWorkspaces());
    on(byId("runtime-toggle"), "click", () => {
        void toggleWorkspace().catch((failure) => toast(errorText(failure), "error"));
    });
    on(byId("bar-history-undo"), "click", () => {
        void undoHistory().catch((failure) => toast(errorText(failure), "error"));
    });
    on(byId("bar-history-redo"), "click", () => {
        void redoHistory().catch((failure) => toast(errorText(failure), "error"));
    });
    on(byId("bar-clone"), "click", () => {
        void promptClone().catch((failure) => toast(errorText(failure), "error"));
    });
    effect(() => {
        byId<HTMLButtonElement>("bar-history-undo").disabled =
            !state.historyCanUndo || state.historyUnavailable;
        byId<HTMLButtonElement>("bar-history-redo").disabled =
            !state.historyCanRedo || state.historyUnavailable;
        byId<HTMLButtonElement>("bar-history-undo").title = state.historyUnavailable
            ? t("bar.unavailable")
            : t("bar.undo");
        byId<HTMLButtonElement>("bar-history-redo").title = state.historyUnavailable
            ? t("bar.unavailable")
            : t("bar.redo");
    });
    effect(() => {
        byId("workbench-title").textContent = state.workbenchTitle;
    });
    effect(() => {
        byId("workbench-state").textContent = state.workbenchState;
    });
    effect(() => {
        byId("workbench-state").className = state.workbenchStateClass;
    });
    effect(() => {
        byId("runtime-toggle").textContent = state.runtimeLabel;
    });

    effect(() => {
        const items = state.toasts;
        const home = byId("toasts");
        const workbench = byId("workbench-toasts");
        const target = state.page === "workbench" ? workbench : home;
        const source = state.page === "workbench" ? home : workbench;
        source.replaceChildren();
        target.replaceChildren(
            ...items.map((item) => {
                const node = document.createElement("div");
                node.className = `toast alert toast-${item.kind}`;
                node.textContent = item.message;
                return node;
            }),
        );
    });
}
