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
      <a id="login-link" class="login-link" href="/api/login" hidden>Sign in <span aria-hidden="true">↗</span></a>
    </header>

    ${homeMarkup()}

    <main id="workbench-page" class="page page-workbench" hidden>
      <div class="toasts" id="workbench-toasts" aria-live="polite"></div>
      <div class="workbench-bar">
        <button id="back-home" class="icon-button back-button" type="button" aria-label="${t("bar.back")}">←</button>
        <div class="breadcrumb"><span>${t("bar.workspaces")}</span><b>/</b><strong id="workbench-title">Workspace</strong></div>
        <span id="workbench-state" class="status status-requested">requested</span>
        <button id="bar-history-undo" class="icon-button history-control" type="button" title="Undo workspace change" aria-label="Undo workspace change" disabled>↶</button>
        <button id="bar-history-redo" class="icon-button history-control" type="button" title="Redo workspace change" aria-label="Redo workspace change" disabled>↷</button>
        <div class="bar-spacer"></div>
        <div id="preview-links" class="preview-links"></div>
        <label class="tools-control" for="tools-select">
          <span class="sr-only">Workspace tools</span>
          <select id="tools-select" aria-label="Workspace tools">
            <option value="files">Files</option>
            <option value="history">History</option>
            <option value="trash">Trash</option>
            <option value="diff">Diff</option>
            <option value="clone">Clone</option>
            <option value="config">Config</option>
            <option value="flags">Flags</option>
          </select>
        </label>
        <button id="bar-clone" class="button btn bar-clone" type="button" title="Clone this workspace">Clone</button>
        <label class="theme-control" for="theme-select">
          <span class="sr-only">${t("bar.theme")}</span>
          <select id="theme-select" aria-label="${t("bar.theme")}">
            ${THEMES.map(
                (theme) => `<option value="${theme}">${THEME_LABELS[theme]}</option>`,
            ).join("")}
          </select>
        </label>
        <button id="refresh-preview-ports" class="button btn preview-refresh" type="button" title="${t("bar.checkPorts")}">${t("bar.checkPorts")}</button>
        <button id="runtime-toggle" class="button btn" type="button">${t("bar.startWorkspace")}</button>
      </div>
      ${watchBannerMarkup()}
      <nav class="workbench-panel-nav" aria-label="Workspace panels">
        <button type="button" data-panel="files" aria-pressed="true">${t("nav.files")}</button>
        <button type="button" data-panel="editor" aria-pressed="false">${t("nav.editor")}</button>
        <button type="button" data-panel="run" aria-pressed="false">${t("nav.run")}</button>
        <button type="button" data-panel="languages" aria-pressed="false">${t("nav.languages")}</button>
        <button type="button" data-panel="debug" aria-pressed="false">${t("nav.debug")}</button>
        <button type="button" data-panel="git" aria-pressed="false">${t("nav.git")}</button>
        <button type="button" data-panel="terminal" aria-pressed="false">${t("nav.terminal")}</button>
        <button type="button" data-panel="preview" aria-pressed="false">${t("nav.preview")}</button>
        <button type="button" data-panel="collaboration" aria-pressed="false">Collaborate</button>
        <button type="button" data-panel="agent" aria-pressed="false">Agent</button>
        <button type="button" data-panel="data" aria-pressed="false">Data</button>
        <button type="button" data-panel="club" aria-pressed="false">Club</button>
        <button type="button" data-panel="ops" aria-pressed="false">Ops</button>
        <button type="button" data-panel="history" aria-pressed="false">History</button>
        <button type="button" data-panel="trash" aria-pressed="false">Trash</button>
        <button type="button" data-panel="diff" aria-pressed="false">Diff</button>
        <button type="button" data-panel="clone" aria-pressed="false">Clone</button>
        <button type="button" data-panel="config" aria-pressed="false">Config</button>
        <button type="button" data-panel="flags" aria-pressed="false">Flags</button>
      </nav>
      <div class="workbench-layout">
        ${sidebarMarkup()}
        <section class="editor-column" aria-label="Code editor">
          ${editorMarkup()}
          ${terminalMarkup()}
          ${previewMarkup()}
          ${servicePanelsMarkup()}
          ${workspaceToolsMarkup()}
          <footer class="statusbar"><span>Jahaziel</span><span id="status-language">Plain text</span><span>UTF-8</span><span>Linux</span></footer>
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
        const labels: Record<string, string> = {
            files: "nav.files",
            editor: "nav.editor",
            run: "nav.run",
            languages: "nav.languages",
            debug: "nav.debug",
            git: "nav.git",
            terminal: "nav.terminal",
            preview: "nav.preview",
        };
        for (const button of Array.from(
            document.querySelectorAll<HTMLButtonElement>(".workbench-panel-nav [data-panel]"),
        )) {
            const key = labels[button.dataset.panel ?? ""];
            if (key) button.textContent = t(key);
        }
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
            ? "Unavailable on this host"
            : "Undo workspace change";
        byId<HTMLButtonElement>("bar-history-redo").title = state.historyUnavailable
            ? "Unavailable on this host"
            : "Redo workspace change";
    });
    const toolsSelect = byId<HTMLSelectElement>("tools-select");
    on(toolsSelect, "change", () => setPanel(toolsSelect.value as typeof state.activePanel));
    effect(() => {
        toolsSelect.value = state.activePanel;
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
