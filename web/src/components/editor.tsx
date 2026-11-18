///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { errorText } from "../lib/api";
import {
    activateDocument,
    closeTab,
    keepLocalVersion,
    saveActiveDocument,
    useServerVersion,
} from "../lib/controller";
import { bindDisabled, bindHidden, bindText, byId, effect, on } from "../lib/dom";
import { state, toast } from "../lib/store";

export function editorMarkup(): string {
    return `
    <div class="editor-chrome">
      <div id="editor-tabs" class="editor-tabs"></div>
      <div class="editor-controls">
        <span id="save-state" class="save-state"></span>
        <button id="save-file" class="button btn button-save" type="button" disabled>Save <kbd>Ctrl S</kbd></button>
      </div>
    </div>
    <div class="editor-location"><span class="location-root">workspace</span><span>/</span><span id="active-file">No file selected</span></div>
    <div id="save-conflict-actions" class="save-conflict-actions" hidden>
      <span>The server file changed after you opened it.</span>
      <button id="use-server-version" type="button">Use server version</button>
      <button id="keep-local-version" type="button">Keep my edits</button>
    </div>
    <div class="editor-surface-wrap">
      <div id="editor-empty" class="editor-empty"><div class="empty-mark">⌘</div><p>Choose a file to begin.</p><span>Changes save directly to your Linux workspace.</span></div>
      <div id="editor-surface" class="editor-surface"></div>
    </div>
  `;
}

export function bindEditor(): void {
    const tabs = byId("editor-tabs");
    effect(() => {
        const openDocuments = state.tabs;
        tabs.replaceChildren(
            ...openDocuments.map((tab) => {
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
                    dirty.setAttribute("aria-label", "Unsaved changes");
                    button.append(dirty);
                }
                on(button, "click", () => activateDocument(tab.path));
                const close = document.createElement("span");
                close.className = "tab-close";
                close.textContent = "×";
                close.setAttribute("role", "button");
                close.setAttribute("aria-label", `Close ${tab.path}`);
                on(close, "click", (event) => {
                    event.stopPropagation();
                    closeTab(tab.path);
                });
                button.append(close);
                return button;
            }),
        );
    });

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
