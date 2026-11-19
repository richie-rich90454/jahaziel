///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { on } from "./dom";
import { t, subscribeLocale } from "./i18n";
import { renderMarkdown } from "./markdown";
import { runtime } from "./runtime";
import { state } from "./store";

let enabled = true;

export function isMarkdownPreviewEnabled(): boolean {
    return enabled;
}

export function setMarkdownPreviewEnabled(value: boolean): void {
    enabled = value;
    refreshMarkdownPreview();
}

function isMarkdown(path: string | null): boolean {
    return Boolean(path && /\.m?d(?:own)?$/i.test(path));
}

/** Repaints the local-only Markdown preview from the active CodeMirror document. */
export function refreshMarkdownPreview(): void {
    const pane = document.getElementById("markdown-preview");
    const body = document.getElementById("markdown-preview-body");
    const toggle = document.getElementById("markdown-toggle");
    if (!pane || !body || !toggle) return;
    const markdown = isMarkdown(state.activePath);
    toggle.hidden = !markdown;
    const visible = markdown && enabled;
    pane.hidden = !visible;
    if (!visible) return;
    const opened = state.activePath ? runtime.openDocuments.get(state.activePath) : undefined;
    body.innerHTML = opened
        ? renderMarkdown(opened.view.state.doc.toString())
        : `<p>${t("markdown.empty")}</p>`;
}

export function bindMarkdownPreview(): void {
    const toggle = document.getElementById("markdown-toggle");
    if (toggle) {
        on(toggle, "click", () => setMarkdownPreviewEnabled(!enabled));
        toggle.textContent = t("markdown.split");
    }
    subscribeLocale(() => {
        if (toggle) toggle.textContent = t("markdown.split");
        refreshMarkdownPreview();
    });
    refreshMarkdownPreview();
}
