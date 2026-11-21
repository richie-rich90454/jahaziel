///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { errorText } from "../lib/api";
import { bindDisabled, bindHidden, bindText, bindValue, byId, effect, on } from "../lib/dom";
import {
    setState,
    state,
    toast,
    type FeatureFlag,
    type SnapshotRow,
    type TrashEntry,
} from "../lib/store";
import {
    cloneWorkspace,
    deleteTrash,
    emptyTrash,
    loadConfig,
    loadDiff,
    loadFlags,
    loadHistory,
    loadTrash,
    redoHistory,
    reloadExternalChanges,
    restoreSnapshot,
    restoreTrash,
    saveConfig,
    setConfigValue,
    setFlag,
    undoHistory,
    validateConfig,
} from "../lib/controller";
import { renderPluginDependencies } from "./plugins";

const unavailable = `<p class="service-unavailable" hidden>Unavailable on this host</p>`;

function panel(name: string, title: string, body: string): string {
    return `
    <section class="service-panel" data-panel="${name}" aria-label="${title}">
      <div class="service-head">
        <h2>${title}</h2>
        <span id="tools-${name}-state" class="service-status" role="status"></span>
        <button id="tools-${name}-refresh" class="button svc-button" type="button">Refresh</button>
      </div>
      ${unavailable}
      <p id="tools-${name}-result" class="service-result" role="status"></p>
      ${body}
    </section>`;
}

export function workspaceToolsMarkup(): string {
    return [
        panel(
            "history",
            "Workspace history",
            `<div class="svc-row">
               <button id="history-undo" class="button svc-button" type="button" disabled>Undo</button>
               <button id="history-redo" class="button svc-button" type="button" disabled>Redo</button>
             </div>
             <ul id="history-list" class="svc-list"></ul>`,
        ),
        panel(
            "trash",
            "Trash",
            `<div class="svc-row"><button id="trash-empty" class="button svc-button" type="button">Empty trash</button></div>
             <ul id="trash-list" class="svc-list"></ul>`,
        ),
        panel(
            "diff",
            "Workspace diff",
            `<div class="svc-row">
               <label class="svc-field"><span>Snapshot</span><select id="diff-snapshot"></select></label>
               <button id="diff-restore-tree" class="button svc-button" type="button">Restore whole tree</button>
             </div>
             <ul id="diff-rows" class="svc-list"></ul>`,
        ),
        panel(
            "clone",
            "Clone workspace",
            `<div class="svc-row"><label class="svc-field"><span>Name</span><input id="clone-name" type="text" maxlength="64" /></label></div>
             <div class="svc-row"><label class="svc-field svc-field-wide"><span>Files to include (optional, comma separated)</span><input id="clone-paths" type="text" maxlength="2048" /></label></div>
             <button id="clone-submit" class="button button-primary svc-button" type="button">Clone</button>`,
        ),
        panel(
            "config",
            "Server configuration",
            `<label class="svc-field svc-field-wide"><span>Configuration (JSON)</span><textarea id="config-editor" rows="16" spellcheck="false"></textarea></label>
             <p id="config-error" class="svc-meta" role="alert"></p>
             <div class="svc-row">
               <button id="config-validate" class="button svc-button" type="button">Validate</button>
               <button id="config-save" class="button button-primary svc-button" type="button">Save</button>
             </div>
             <p id="config-restart" class="svc-meta" hidden>Restart required for changes to take effect.</p>
             <pre id="config-diff" class="svc-output"></pre>`,
        ),
        panel(
            "flags",
            "Feature flags",
            `<ul id="flags-list" class="svc-list"></ul>
             <div class="svc-block"><h3>Plugin dependencies & capabilities</h3><div id="plugin-dependencies" class="svc-meta"></div></div>`,
        ),
    ].join("");
}

export function watchBannerMarkup(): string {
    return `
    <div id="watch-banner" class="watch-banner" role="status" hidden>
      <span id="watch-message">External change detected.</span>
      <button id="watch-reload" class="button" type="button">Reload from server</button>
      <button id="watch-keep" class="button button-quiet" type="button">Keep my edits</button>
    </div>`;
}

function guard(action: () => void | Promise<unknown>): void {
    void Promise.resolve(action()).catch((failure: unknown) => toast(errorText(failure), "error"));
}

function toolButton(id: string, action: () => void | Promise<unknown>): void {
    on(byId(id), "click", () => guard(action));
}

function bindPanels(): void {
    effect(() => {
        const active = state.activePanel;
        for (const name of ["history", "trash", "diff", "clone", "config", "flags"]) {
            const section = byId(`tools-${name}-state`).closest<HTMLElement>(".service-panel");
            if (section) section.classList.toggle("is-active", active === name);
        }
    });
}

function renderHistory(): void {
    const list = byId("history-list");
    bindDisabled(
        byId<HTMLButtonElement>("history-undo"),
        () => !state.historyCanUndo || state.historyUnavailable,
    );
    bindDisabled(
        byId<HTMLButtonElement>("history-redo"),
        () => !state.historyCanRedo || state.historyUnavailable,
    );
    effect(() => {
        const unavailableNode = byId("tools-history-state");
        const section = unavailableNode.closest<HTMLElement>(".service-panel");
        if (section)
            section.querySelector<HTMLElement>(".service-unavailable")!.hidden =
                !state.historyUnavailable;
        unavailableNode.textContent = state.historyState;
        list.replaceChildren(
            ...state.historyEntries.map((entry) => {
                const row = document.createElement("li");
                row.className = "svc-item";
                row.textContent = `${entry.undone ? "(undone) " : ""}${entry.label ?? entry.action ?? entry.id}${entry.path ? ` · ${entry.path}` : ""}`;
                return row;
            }),
        );
    });
}

function renderTrash(): void {
    const list = byId("trash-list");
    bindDisabled(
        byId<HTMLButtonElement>("trash-empty"),
        () => state.trashUnavailable || state.trashItems.length === 0,
    );
    effect(() => {
        const node = byId("tools-trash-state");
        const section = node.closest<HTMLElement>(".service-panel");
        if (section)
            section.querySelector<HTMLElement>(".service-unavailable")!.hidden =
                !state.trashUnavailable;
        node.textContent = state.trashState;
        list.replaceChildren(...state.trashItems.map(trashRow));
    });
    on(list, "click", (event) => {
        const button = (event.target as HTMLElement).closest<HTMLButtonElement>(
            "button[data-trash-act]",
        );
        if (!button) return;
        const entry = state.trashItems.find((item) => item.id === button.dataset.id);
        if (!entry) return;
        guard(() =>
            button.dataset.trashAct === "restore" ? restoreTrash(entry) : deleteTrash(entry),
        );
    });
}

function trashRow(entry: TrashEntry): HTMLLIElement {
    const row = document.createElement("li");
    row.className = "svc-item";
    const label = document.createElement("span");
    label.textContent = `${entry.path}${entry.deletedAt ? ` · ${entry.deletedAt}` : ""}`;
    const restore = document.createElement("button");
    restore.type = "button";
    restore.className = "svc-action";
    restore.textContent = "Restore";
    restore.dataset.trashAct = "restore";
    restore.dataset.id = entry.id;
    const remove = document.createElement("button");
    remove.type = "button";
    remove.className = "svc-action";
    remove.textContent = "Delete";
    remove.dataset.trashAct = "delete";
    remove.dataset.id = entry.id;
    row.append(label, restore, remove);
    return row;
}

function renderDiff(): void {
    const select = byId<HTMLSelectElement>("diff-snapshot");
    bindDisabled(
        byId<HTMLButtonElement>("diff-restore-tree"),
        () => !state.snapshotId || state.snapshotUnavailable,
    );
    effect(() => {
        const node = byId("tools-diff-state");
        const section = node.closest<HTMLElement>(".service-panel");
        if (section)
            section.querySelector<HTMLElement>(".service-unavailable")!.hidden =
                !state.snapshotUnavailable;
        node.textContent = state.snapshotState;
        select.replaceChildren(
            ...state.snapshotIds.map(
                (id) => new Option(id.slice(0, 8), id, false, id === state.snapshotId),
            ),
        );
        const rows = byId("diff-rows");
        rows.replaceChildren(...state.snapshotRows.map(diffRow));
    });
    on(select, "change", () => guard(() => loadDiff(select.value)));
    toolButton("diff-restore-tree", () => restoreSnapshot(state.snapshotId));
    on(byId("diff-rows"), "click", (event) => {
        const button = (event.target as HTMLElement).closest<HTMLButtonElement>(
            "button[data-restore]",
        );
        if (button?.dataset.restore)
            guard(() => restoreSnapshot(state.snapshotId, button.dataset.restore));
    });
}

function diffRow(row: SnapshotRow): HTMLLIElement {
    const item = document.createElement("li");
    item.className = `svc-item diff-row diff-${row.status}`;
    const label = document.createElement("span");
    const counts = [row.added ? `+${row.added}` : "", row.removed ? `-${row.removed}` : ""]
        .filter(Boolean)
        .join(" ");
    label.textContent = `${row.status.toUpperCase()} ${row.path}${counts ? ` (${counts})` : ""}`;
    const restore = document.createElement("button");
    restore.type = "button";
    restore.className = "svc-action";
    restore.textContent = "Restore file";
    restore.dataset.restore = row.path;
    item.append(label, restore);
    return item;
}

function renderClone(): void {
    const name = byId<HTMLInputElement>("clone-name");
    const paths = byId<HTMLInputElement>("clone-paths");
    effect(() => {
        const node = byId("tools-clone-state");
        const section = node.closest<HTMLElement>(".service-panel");
        if (section)
            section.querySelector<HTMLElement>(".service-unavailable")!.hidden =
                !state.cloneUnavailable;
        node.textContent = state.cloneState;
        if (!name.value && state.workspace) name.value = `${state.workspace.name}-copy`;
    });
    on(byId("clone-submit"), "click", () => {
        const source = state.workspace;
        if (!source) {
            toast("Open a workspace first.", "error");
            return;
        }
        const subset = paths.value
            .split(",")
            .map((path) => path.trim())
            .filter(Boolean);
        guard(() => cloneWorkspace(source, name.value.trim() || `${source.name}-copy`, subset));
    });
}

function renderConfig(): void {
    const editor = byId<HTMLTextAreaElement>("config-editor");
    bindValue(editor, () => state.config.value);
    bindText(byId("config-error"), () => state.config.error);
    bindText(byId("config-diff"), () => state.config.diff);
    bindHidden(byId("config-restart"), () => !state.config.restartRequired);
    on(editor, "input", () => setConfigValue(editor.value));
    toolButton("config-validate", () => {
        validateConfig();
        toast(
            state.config.error || "Configuration is valid.",
            state.config.error ? "error" : "success",
        );
    });
    toolButton("config-save", saveConfig);
    effect(() => {
        const node = byId("tools-config-state");
        const section = node.closest<HTMLElement>(".service-panel");
        if (section)
            section.querySelector<HTMLElement>(".service-unavailable")!.hidden =
                !state.config.unavailable;
        node.textContent = state.config.unavailable ? "Unavailable on this host" : "Configuration";
    });
}

function flagSelect(value: boolean | null): HTMLSelectElement {
    const select = document.createElement("select");
    for (const [label, option] of [
        ["Inherit", "inherit"],
        ["On", "on"],
        ["Off", "off"],
    ] as const)
        select.append(
            new Option(
                label,
                option,
                false,
                (value === null && option === "inherit") ||
                    (value === true && option === "on") ||
                    (value === false && option === "off"),
            ),
        );
    return select;
}

function renderFlags(): void {
    const list = byId("flags-list");
    effect(() => {
        const node = byId("tools-flags-state");
        const section = node.closest<HTMLElement>(".service-panel");
        if (section)
            section.querySelector<HTMLElement>(".service-unavailable")!.hidden =
                !state.flagsUnavailable;
        node.textContent = state.flagsState;
        list.replaceChildren(...state.flags.map(flagRow));
        renderPluginDependencies(byId("plugin-dependencies"));
    });
}

function flagRow(flag: FeatureFlag): HTMLLIElement {
    const row = document.createElement("li");
    row.className = "svc-item";
    const label = document.createElement("span");
    label.textContent = flag.description ? `${flag.name} — ${flag.description}` : flag.name;
    row.append(label);
    const overrides: Array<
        ["global" | "userOverride" | "workspaceOverride", boolean | null, string]
    > = [
        ["global", flag.enabled, "global"],
        ["userOverride", flag.userOverride ?? null, "user"],
        ["workspaceOverride", flag.workspaceOverride ?? null, "workspace"],
    ];
    for (const [key, value, title] of overrides) {
        const wrapper = document.createElement("label");
        wrapper.className = "svc-field";
        const caption = document.createElement("span");
        caption.textContent = title;
        const select = flagSelect(value);
        on(select, "change", () => {
            const next = select.value === "inherit" ? null : select.value === "on";
            setFlag(flag.name, key, next);
        });
        wrapper.append(caption, select);
        row.append(wrapper);
    }
    return row;
}

function bindWatchBanner(): void {
    const banner = byId("watch-banner");
    bindHidden(banner, () => !state.watchBannerVisible);
    on(byId("watch-reload"), "click", () => reloadExternalChanges(true));
    on(byId("watch-keep"), "click", () => reloadExternalChanges(false));
}

const loaded = new Set<string>();

function bindPanelLoading(): void {
    effect(() => {
        void state.workspace?.workspaceId;
        loaded.clear();
    });
    effect(() => {
        const panel = state.activePanel;
        if (!["history", "trash", "diff", "config", "flags"].includes(panel)) return;
        if (loaded.has(panel)) return;
        loaded.add(panel);
        if (panel === "history") void loadHistory().catch(() => undefined);
        if (panel === "trash") void loadTrash().catch(() => undefined);
        if (panel === "diff") void loadHistory().catch(() => undefined);
        if (panel === "config") void loadConfig().catch(() => undefined);
        if (panel === "flags") void loadFlags().catch(() => undefined);
    });
}

export function bindWorkspaceTools(): void {
    bindPanels();
    bindPanelLoading();
    renderHistory();
    renderTrash();
    renderDiff();
    renderClone();
    renderConfig();
    renderFlags();
    bindWatchBanner();
    toolButton("tools-history-refresh", loadHistory);
    toolButton("history-undo", undoHistory);
    toolButton("history-redo", redoHistory);
    toolButton("tools-trash-refresh", loadTrash);
    toolButton("trash-empty", emptyTrash);
    toolButton("tools-diff-refresh", () => loadHistory());
    toolButton("tools-clone-refresh", () => setState("cloneState", "Clone"));
    toolButton("tools-config-refresh", loadConfig);
    toolButton("tools-flags-refresh", loadFlags);
}
