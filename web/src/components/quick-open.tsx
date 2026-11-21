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
    const palette = byId("quick-open-palette");
    const dialog = palette.querySelector<HTMLElement>(".quick-open-dialog")!;
    on(palette, "keydown", (event) => {
        if (event.key === "Escape") {
            event.preventDefault();
            closeQuickOpen();
            return;
        }
        if (event.key !== "Tab") return;
        const focusable = Array.from(
            dialog.querySelectorAll<HTMLElement>("input:not([disabled]), button:not([disabled])"),
        ).filter((node) => node.offsetParent !== null);
        if (focusable.length === 0) {
            event.preventDefault();
            return;
        }
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        const active = document.activeElement;
        if (event.shiftKey && (active === first || !dialog.contains(active))) {
            event.preventDefault();
            last.focus();
        } else if (!event.shiftKey && (active === last || !dialog.contains(active))) {
            event.preventDefault();
            first.focus();
        }
    });
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
