///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { errorText } from "./lib/api";
import { backToWorkspaces, setPanel, toggleWorkspace } from "./lib/controller";
import { byId, effect, on } from "./lib/dom";
import { state, toast } from "./lib/store";
import { editorMarkup, bindEditor } from "./components/editor";
import { bindHome, homeMarkup } from "./components/home";
import { bindPreview, previewMarkup } from "./components/preview";
import { bindQuickOpen, quickOpenMarkup } from "./components/quick-open";
import { bindSidebar, sidebarMarkup } from "./components/sidebar";
import { bindTerminal, terminalMarkup } from "./components/terminal";

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
      <div id="user-badge" class="user-badge" hidden>
        <span class="user-presence"></span><span id="user-name"></span><span id="user-email" class="user-email"></span>
      </div>
      <a id="login-link" class="login-link" href="/api/login" hidden>Sign in <span aria-hidden="true">↗</span></a>
    </header>

    ${homeMarkup()}

    <main id="workbench-page" class="page page-workbench" hidden>
      <div class="toasts" id="workbench-toasts" aria-live="polite"></div>
      <div class="workbench-bar">
        <button id="back-home" class="icon-button back-button" type="button" aria-label="Back to workspaces">←</button>
        <div class="breadcrumb"><span>Workspaces</span><b>/</b><strong id="workbench-title">Workspace</strong></div>
        <span id="workbench-state" class="status status-requested">requested</span>
        <div class="bar-spacer"></div>
        <div id="preview-links" class="preview-links"></div>
        <button id="refresh-preview-ports" class="button btn preview-refresh" type="button" title="Check which preview ports are open">Check ports</button>
        <button id="runtime-toggle" class="button btn" type="button">Start workspace</button>
      </div>
      <nav class="workbench-panel-nav" aria-label="Workspace panels">
        <button type="button" data-panel="files" aria-pressed="true">Files</button>
        <button type="button" data-panel="editor" aria-pressed="false">Editor</button>
        <button type="button" data-panel="run" aria-pressed="false">Run</button>
        <button type="button" data-panel="languages" aria-pressed="false">Languages</button>
        <button type="button" data-panel="debug" aria-pressed="false">Debug</button>
        <button type="button" data-panel="git" aria-pressed="false">Git</button>
        <button type="button" data-panel="terminal" aria-pressed="false">Terminal</button>
        <button type="button" data-panel="preview" aria-pressed="false">Preview</button>
      </nav>
      <div class="workbench-layout">
        ${sidebarMarkup()}
        <section class="editor-column" aria-label="Code editor">
          ${editorMarkup()}
          ${terminalMarkup()}
          ${previewMarkup()}
          <footer class="statusbar"><span>Jahaziel</span><span id="status-language">Plain text</span><span>UTF-8</span><span>Linux</span></footer>
        </section>
      </div>
    </main>

    ${quickOpenMarkup()}
  `;
    return root;
}

export function bindApp(): void {
    bindHome();
    bindSidebar();
    bindEditor();
    bindTerminal();
    bindPreview();
    bindQuickOpen();

    for (const button of Array.from(
        document.querySelectorAll<HTMLButtonElement>(".workbench-panel-nav [data-panel]"),
    )) {
        on(button, "click", () => setPanel(button.dataset.panel as typeof state.activePanel));
    }
    on(byId("back-home"), "click", () => backToWorkspaces());
    on(byId("runtime-toggle"), "click", () => {
        void toggleWorkspace().catch((failure) => toast(errorText(failure), "error"));
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
