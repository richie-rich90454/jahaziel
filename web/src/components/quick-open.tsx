///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { chooseQuickResult, closeQuickOpen, renderQuickOpen } from "../lib/controller";
import { byId, effect, on } from "../lib/dom";
import { state } from "../lib/store";

export function quickOpenMarkup(): string {
    return `
    <div id="quick-open-palette" class="quick-open-palette" hidden>
      <div class="quick-open-dialog" role="dialog" aria-modal="true" aria-label="Quick open">
        <input id="quick-open-input" type="search" placeholder="Open file or panel…" aria-label="Quick open" autocomplete="off" />
        <ul id="quick-open-results" class="quick-open-results" aria-live="polite"></ul>
      </div>
    </div>
  `;
}

export function bindQuickOpen(): void {
    const input = byId<HTMLInputElement>("quick-open-input");
    on(input, "input", () => {
        void renderQuickOpen(input.value);
    });
    on(input, "keydown", (event) => {
        if (event.key === "Escape") closeQuickOpen();
    });
    const palette = byId("quick-open-palette");
    on(palette, "click", (event) => {
        if (event.target === palette) closeQuickOpen();
    });
    const results = byId("quick-open-results");
    effect(() => {
        const entries = state.quickOpenResults;
        results.replaceChildren(
            ...entries.map((entry) => {
                const item = document.createElement("li");
                const button = document.createElement("button");
                button.type = "button";
                button.textContent = entry.detail
                    ? `${entry.label} — ${entry.detail}`
                    : entry.label;
                button.disabled = entry.kind === "empty";
                on(button, "click", () => chooseQuickResult(entry));
                item.append(button);
                return item;
            }),
        );
    });
}
