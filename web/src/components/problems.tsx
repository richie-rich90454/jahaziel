///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { focusDiagnostic } from "../lib/controller";
import { effect, on } from "../lib/dom";
import { state } from "../lib/store";

export function problemsMarkup(): string {
    return `
      <section class="problems-subpanel" aria-label="Problems">
        <h3>Problems</h3>
        <ul id="problems-list" class="problems-list" aria-live="polite"></ul>
      </section>
    `;
}

export function bindProblems(): void {
    const list = document.getElementById("problems-list");
    if (!list) return;
    effect(() => {
        const problems = state.problems;
        if (problems.length === 0) {
            const empty = document.createElement("li");
            empty.className = "debug-empty";
            empty.textContent = "No problems in open documents.";
            list.replaceChildren(empty);
            return;
        }
        list.replaceChildren(
            ...problems.map((diagnostic) => {
                const item = document.createElement("li");
                const button = document.createElement("button");
                button.type = "button";
                button.className = `problem-entry problem-${diagnostic.severity.toLowerCase()}`;
                button.textContent = `${diagnostic.severity}: ${diagnostic.message}`;
                button.title = `${diagnostic.path}:${diagnostic.line}:${diagnostic.column} — ${diagnostic.message}`;
                const location = document.createElement("span");
                location.className = "problem-location";
                location.textContent = `${diagnostic.path}:${diagnostic.line}`;
                button.append(location);
                on(button, "click", () => {
                    void focusDiagnostic(diagnostic);
                });
                item.append(button);
                return item;
            }),
        );
    });
}
