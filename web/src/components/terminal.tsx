///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { errorText } from "../lib/api";
import {
  closeActiveTerminal,
  copyTerminalSelection,
  launchOpenCode,
  openTerminal,
  reconnectTerminal,
  removeOpenCode,
  terminalInput,
  terminalShortcut,
  toggleTerminalModifier,
  updateOpenCode,
} from "../lib/controller";
import { bindDisabled, bindHidden, bindText, byId, effect, on } from "../lib/dom";
import { state, toast } from "../lib/store";

const inputKeys: Record<string, string> = {
  escape: "\u001b",
  tab: "\t",
  left: "\u001b[D",
  up: "\u001b[A",
  down: "\u001b[B",
  right: "\u001b[C",
};

export function terminalMarkup(): string {
  return `
    <section class="terminal-dock" aria-label="Terminal">
      <div class="terminal-heading">
        <div><span class="terminal-square"></span><span>Terminal</span><span id="terminal-state" class="terminal-state">No session</span></div>
        <div class="terminal-actions">
          <span id="opencode-status" class="terminal-state" role="status">OpenCode, checking</span>
          <button id="terminal-opencode" class="terminal-action" type="button">Open OpenCode</button>
          <button id="opencode-update" class="terminal-action" type="button" hidden>Update</button>
          <button id="opencode-remove" class="terminal-action" type="button" hidden>Remove</button>
          <select id="terminal-session-select" class="terminal-session-select" aria-label="Detached terminal sessions" hidden disabled></select>
          <button id="terminal-reconnect" class="terminal-action" type="button" hidden disabled>Reconnect</button>
          <button id="terminal-new" class="terminal-action" type="button">＋ New terminal</button>
          <button id="close-terminal" class="terminal-action terminal-close" type="button" hidden>×</button>
        </div>
      </div>
      <div id="terminal-key-strip" class="terminal-key-strip" aria-label="Terminal keyboard shortcuts" hidden>
        <button type="button" data-terminal-modifier="ctrl" aria-pressed="false">Ctrl</button>
        <button type="button" data-terminal-modifier="alt" aria-pressed="false">Alt</button>
        <button type="button" data-terminal-input="escape">Esc</button>
        <button type="button" data-terminal-input="tab">Tab</button>
        <button type="button" data-terminal-input="left" aria-label="Left arrow">←</button>
        <button type="button" data-terminal-input="up" aria-label="Up arrow">↑</button>
        <button type="button" data-terminal-input="down" aria-label="Down arrow">↓</button>
        <button type="button" data-terminal-input="right" aria-label="Right arrow">→</button>
        <button type="button" data-terminal-shortcut="c">Ctrl+C</button>
        <button type="button" data-terminal-shortcut="d">Ctrl+D</button>
        <button type="button" data-terminal-shortcut="z">Ctrl+Z</button>
        <button id="terminal-copy-selection" type="button">Copy selection</button>
      </div>
      <div id="terminal-surface" class="terminal-surface"><div class="terminal-placeholder"><span>⌁</span><p>Open a terminal to run commands in this workspace.</p></div></div>
    </section>
  `;
}

export function bindTerminal(): void {
  bindText(byId("terminal-state"), () => state.terminalState);
  bindText(byId("opencode-status"), () => state.opencodeStatus);
  const launch = byId<HTMLButtonElement>("terminal-opencode");
  bindText(launch, () => state.opencodeLaunchLabel);
  bindDisabled(launch, () => state.opencodeLaunchDisabled);
  const update = byId<HTMLButtonElement>("opencode-update");
  bindHidden(update, () => state.opencodeUpdateHidden);
  bindDisabled(update, () => state.opencodeUpdateDisabled);
  const remove = byId<HTMLButtonElement>("opencode-remove");
  bindHidden(remove, () => state.opencodeRemoveHidden);
  bindDisabled(remove, () => state.opencodeRemoveDisabled);

  const select = byId<HTMLSelectElement>("terminal-session-select");
  effect(() => {
    const sessions = state.terminalSessions;
    const previous = select.value;
    select.replaceChildren(...sessions.map((session) => {
      const option = document.createElement("option");
      option.value = session.sessionId;
      const started = new Date(session.startedAt);
      const label = Number.isNaN(started.getTime()) ? "Terminal" : `Terminal, ${started.toLocaleTimeString()}`;
      option.textContent = `${label}, ${session.sessionId.slice(0, 8)}`;
      return option;
    }));
    if (sessions.some((session) => session.sessionId === previous)) select.value = previous;
  });
  bindHidden(select, () => state.terminalSelectHidden);
  bindDisabled(select, () => state.terminalSelectDisabled);
  const reconnect = byId<HTMLButtonElement>("terminal-reconnect");
  bindHidden(reconnect, () => state.terminalReconnectHidden);
  bindDisabled(reconnect, () => state.terminalReconnectDisabled);
  const close = byId<HTMLButtonElement>("close-terminal");
  bindHidden(close, () => state.terminalCloseHidden);
  bindHidden(byId("terminal-key-strip"), () => state.keyStripHidden);

  on(byId("terminal-new"), "click", () => {
    void openTerminal().catch((failure) => toast(errorText(failure), "error"));
  });
  on(launch, "click", () => {
    void launchOpenCode().catch((failure) => toast(errorText(failure), "error"));
  });
  on(update, "click", () => {
    void updateOpenCode().catch((failure) => toast(errorText(failure), "error"));
  });
  on(remove, "click", () => {
    void removeOpenCode().catch((failure) => toast(errorText(failure), "error"));
  });
  on(reconnect, "click", () => {
    void reconnectTerminal().catch((failure) => toast(errorText(failure), "error"));
  });
  on(close, "click", closeActiveTerminal);
  on(byId("terminal-copy-selection"), "click", () => {
    void copyTerminalSelection().catch((failure) => toast(errorText(failure), "error"));
  });

  for (const button of Array.from(document.querySelectorAll<HTMLButtonElement>("[data-terminal-modifier]"))) {
    const modifier = button.dataset.terminalModifier as "ctrl" | "alt";
    effect(() => button.setAttribute("aria-pressed", String(state.terminalModifiers[modifier])));
    on(button, "click", () => toggleTerminalModifier(modifier));
  }
  for (const button of Array.from(document.querySelectorAll<HTMLButtonElement>("[data-terminal-input]"))) {
    const key = button.dataset.terminalInput ?? "";
    on(button, "click", () => terminalInput(inputKeys[key] ?? ""));
  }
  for (const button of Array.from(document.querySelectorAll<HTMLButtonElement>("[data-terminal-shortcut]"))) {
    const key = button.dataset.terminalShortcut ?? "c";
    on(button, "click", () => terminalShortcut(key));
  }
}
