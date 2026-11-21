///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { errorText } from "../lib/api";
import {
    activateDocument,
    bindMarkdownPreview,
    closeTab,
    keepLocalVersion,
    saveActiveDocument,
    useServerVersion,
} from "../lib/controller";
import { bindDisabled, bindHidden, bindText, byId, effect, on } from "../lib/dom";
import { subscribeLocale, t } from "../lib/i18n";
import { icon } from "../lib/icons";
import { state, toast } from "../lib/store";

export function editorMarkup(): string {
    return `
    <div class="editor-chrome">
      <div id="editor-tabs" class="editor-tabs"></div>
      <div class="editor-controls">
        <span id="save-state" class="save-state"></span>
        <button id="markdown-toggle" class="button button-quiet" type="button" hidden>${t("markdown.split")}</button>
        <button id="save-file" class="button button-save" type="button" disabled><span data-i18n="common.save">${t("common.save")}</span> <kbd>Ctrl S</kbd></button>
      </div>
    </div>
    <div class="editor-location"><span class="location-root">workspace</span><span>/</span><span id="active-file"></span></div>
    <div id="save-conflict-actions" class="save-conflict-actions" hidden>
      <span data-i18n="editor.conflict">${t("editor.conflict")}</span>
      <button id="use-server-version" type="button"><span data-i18n="editor.useServer">${t("editor.useServer")}</span></button>
      <button id="keep-local-version" type="button"><span data-i18n="editor.keepLocal">${t("editor.keepLocal")}</span></button>
    </div>
    <div class="editor-surface-wrap">
      <div id="editor-empty" class="editor-empty"><div class="empty-mark">${icon("files")}</div><p data-i18n="editor.emptyTitle">${t("editor.emptyTitle")}</p><span data-i18n="editor.emptyCopy">${t("editor.emptyCopy")}</span></div>
      <div id="editor-surface" class="editor-surface"></div>
      <aside id="markdown-preview" class="markdown-preview" aria-label="${t("markdown.preview")}" hidden>
        <div class="markdown-preview-heading">${t("markdown.preview")}</div>
        <article id="markdown-preview-body" class="markdown-body"></article>
      </aside>
    </div>
  `;
}

export function bindEditor(): void {
    bindMarkdownPreview();
    const tabs = byId("editor-tabs");
    const renderTabs = (): void => {
        tabs.replaceChildren(
            ...state.tabs.map((tab) => {
                const button = document.createElement("button");
                button.type = "button";
                button.className = `editor-tab${tab.path === state.activePath ? " active" : ""}`;
                button.title = tab.path;
                const label = document.createElement("span");
                label.className = "tab-label";
                label.textContent = tab.path.split("/").at(-1) || tab.path;
                button.append(label);
                if (tab.dirty) {
                    const dirty = document.createElement("span");
                    dirty.className = "tab-dirty";
                    dirty.setAttribute("aria-label", t("editor.unsaved"));
                    button.append(dirty);
                }
                on(button, "click", () => activateDocument(tab.path));
                const close = document.createElement("span");
                close.className = "tab-close";
                close.innerHTML = icon("close");
                close.setAttribute("role", "button");
                close.setAttribute("aria-label", t("editor.closeTab", { path: tab.path }));
                on(close, "click", (event) => {
                    event.stopPropagation();
                    closeTab(tab.path);
                });
                button.append(close);
                return button;
            }),
        );
    };
    effect(renderTabs);
    subscribeLocale(renderTabs);

    const saveState = byId("save-state");
    effect(() => {
        saveState.textContent = state.saveState;
    });
    effect(() => {
        saveState.className = state.saveStateClass;
    });
    const saveButton = byId<HTMLButtonElement>("save-file");
    bindDisabled(saveButton, () => state.saveDisabled);
    on(saveButton, "click", () => {
        void saveActiveDocument().catch((failure) => toast(errorText(failure), "error"));
    });

    bindText(byId("active-file"), () => state.activeFileLabel);
    bindText(byId("status-language"), () => state.statusLanguage);
    bindHidden(byId("editor-empty"), () => !state.editorEmptyVisible);
    bindHidden(byId("save-conflict-actions"), () => !state.conflictVisible);
    on(byId("use-server-version"), "click", useServerVersion);
    on(byId("keep-local-version"), "click", keepLocalVersion);
}
