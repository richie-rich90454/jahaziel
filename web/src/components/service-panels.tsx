///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { bindDisabled, bindText, byId, effect, on } from "../lib/dom";
import { t } from "../lib/i18n";
import { state } from "../lib/store";
import {
    call,
    json,
    loadAgent,
    loadClub,
    loadCollaboration,
    loadData,
    loadOps,
    paths,
    serviceState,
    type AgentMessage,
    type AgentPatch,
    type AgentPrompt,
    type AgentSession,
    type AuditRow,
    type CellOutput,
    type ClubCheckpoint,
    type ClubChat,
    type ClubProject,
    type ClubSession,
    type DataTable,
    type FileDiff,
    type FileVersion,
    type NotebookCell,
    type SearchHit,
    type ServiceSection,
} from "../lib/service-panels";

const sections: Array<{ panel: ServiceSection; label: string; intro: string }> = [
    {
        panel: "collaboration",
        label: t("service.collaboration"),
        intro: t("service.collaboration.intro"),
    },
    {
        panel: "agent",
        label: t("service.agent"),
        intro: t("service.agent.intro"),
    },
    {
        panel: "data",
        label: t("service.data"),
        intro: t("service.data.intro"),
    },
    {
        panel: "club",
        label: t("service.club"),
        intro: t("service.club.intro"),
    },
    {
        panel: "ops",
        label: t("service.ops"),
        intro: t("service.ops.intro"),
    },
];

function field(id: string, label: string, type = "text", value = ""): string {
    return `<label class="svc-field"><span>${label}</span><input id="${id}" type="${type}" value="${value}" /></label>`;
}

function button(id: string, label: string): string {
    return `<button id="${id}" class="button btn svc-button" type="button">${label}</button>`;
}

function area(id: string, label: string, rows = 4): string {
    return `<label class="svc-field svc-field-wide"><span>${label}</span><textarea id="${id}" rows="${rows}"></textarea></label>`;
}

export function servicePanelsMarkup(): string {
    return sections
        .map(
            ({ panel, label, intro }) => `
    <section class="service-panel" data-panel="${panel}" aria-label="${label}">
      <div class="service-head">
        <h2>${label}</h2>
        <span id="svc-${panel}-status" class="service-status" role="status">${t("service.notLoaded")}</span>
        ${button(`svc-${panel}-refresh`, t("common.refresh"))}
      </div>
      <p class="service-intro">${intro}</p>
      <p id="svc-${panel}-unavailable" class="service-unavailable" hidden>${t("service.unavailable")}</p>
      <p id="svc-${panel}-result" class="service-result" role="status"></p>
      ${panelMarkup(panel)}
    </section>`,
        )
        .join("");
}

function panelMarkup(panel: ServiceSection): string {
    if (panel === "collaboration")
        return `
      <div class="svc-block"><h3>Members</h3><ul id="svc-collaboration-members" class="svc-list"></ul>
        <div class="svc-row">${field("svc-collab-invitee", "Invitee")}${field("svc-collab-role", "Role", "text", "EDITOR")}${button("svc-collab-invite", "Invite")}</div>
        <div class="svc-row">${field("svc-collab-token", "Invitation token")}${button("svc-collab-accept", "Accept invitation")}</div>
      </div>
      <div class="svc-block"><h3>Presence</h3><ul id="svc-collaboration-presence" class="svc-list"></ul>
        <div class="svc-row">${button("svc-collab-presence-join", "Join")}${button("svc-collab-presence-leave", "Leave")}${field("svc-collab-file", "Active file")}${button("svc-collab-presence-file", "Set file")}</div>
      </div>
      <div class="svc-block"><h3>Comments</h3><ul id="svc-collaboration-comments" class="svc-list"></ul>
        <div class="svc-row">${field("svc-collab-comment-path", "Path")}${field("svc-collab-comment-line", "Line", "number", "1")}${button("svc-collab-comment-add", "Add comment")}</div>
        ${area("svc-collab-comment-text", "Comment", 2)}
      </div>
      <div class="svc-block"><h3>Share links</h3><ul id="svc-collaboration-shares" class="svc-list"></ul>
        <div class="svc-row">${field("svc-collab-share-scope", "Scope", "text", "VIEW")}<label class="svc-field svc-field-check"><input id="svc-collab-one-time" type="checkbox" /><span>One time</span></label>${button("svc-collab-share-create", "Create link")}</div>
      </div>
      <div class="svc-block"><h3>Audit</h3><ul id="svc-collaboration-audit" class="svc-list"></ul></div>`;
    if (panel === "agent")
        return `
      <div class="svc-block"><h3>Sessions</h3><ul id="svc-agent-sessions" class="svc-list"></ul>
        <div class="svc-row">${field("svc-agent-session-name", "Session name")}${field("svc-agent-session-id", "Session id")}${button("svc-agent-session-create", "New session")}</div>
        <pre id="svc-agent-transcript" class="svc-output"></pre>
        <div class="svc-row">${field("svc-agent-line", "Transcript line")}${button("svc-agent-line-add", "Append line")}</div>
      </div>
      <div class="svc-block"><h3>Prompt library</h3><ul id="svc-agent-prompts" class="svc-list"></ul>
        <div class="svc-row">${field("svc-agent-prompt-name", "Snippet name")}${button("svc-agent-prompt-create", "Save snippet")}</div>
        ${area("svc-agent-prompt-text", "Snippet text", 3)}
      </div>
      <div class="svc-block"><h3>Staged diff review</h3><ul id="svc-agent-patches" class="svc-list"></ul>
        ${area("svc-agent-patch-text", "Unified patch to stage", 6)}
        <div class="svc-row">${button("svc-agent-patch-stage", "Stage patch")}</div>
        <ul id="svc-agent-hunks" class="svc-list"></ul>
      </div>
      <div class="svc-block"><h3>Chat</h3><div id="svc-agent-chat" class="svc-output"></div>
        <div class="svc-row">${field("svc-agent-chat-file", "Active file")}${field("svc-agent-chat-selection", "Selection")}${button("svc-agent-chat-send", "Send")}${button("svc-agent-chat-cancel", "Cancel")}</div>
        ${area("svc-agent-chat-text", "Message", 3)}
      </div>
      <div class="svc-block"><h3>Model and usage</h3><ul id="svc-agent-models" class="svc-list"></ul><p id="svc-agent-usage" class="svc-meta"></p></div>`;
    if (panel === "data")
        return `
      <div class="svc-block"><h3>SQLite</h3>
        <div class="svc-row">${field("svc-data-sqlite-path", "Database path", "text", "app.db")}${button("svc-data-sqlite-tables", "List tables")}${button("svc-data-sqlite-schema", "Schema")}</div>
        <div class="svc-row"><label class="svc-field"><span>Table</span><select id="svc-data-table"></select></label>${button("svc-data-sqlite-rows", "View rows")}${field("svc-data-sql", "SQL query")}${button("svc-data-sqlite-query", "Run query")}</div>
        <div id="svc-data-table-view" class="svc-table-view"></div>
      </div>
      <div class="svc-block"><h3>CSV</h3>${area("svc-data-csv", "CSV content", 4)}
        <div class="svc-row">${field("svc-data-csv-path", "Save path")}${field("svc-data-csv-version", "Expected version")}${button("svc-data-csv-parse", "Parse")}${button("svc-data-csv-write", "Write")}${button("svc-data-csv-save", "Save")}</div>
        <div id="svc-data-csv-view" class="svc-table-view"></div>
      </div>
      <div class="svc-block"><h3>Notebook</h3>
        <div class="svc-row">${field("svc-data-notebook", "Notebook path")}${field("svc-data-task-id", "Task id")}${button("svc-data-cells-load", "Load cells")}${button("svc-data-cells-save", "Save cells")}${button("svc-data-notebook-run", "Run")}</div>
        ${area("svc-data-cells", "Cells (one per line)", 4)}
        <pre id="svc-data-outputs" class="svc-output"></pre>
      </div>
      <div class="svc-block"><h3>Search index</h3>
        <div class="svc-row">${field("svc-data-search", "Query")}${button("svc-data-index", "Index")}${button("svc-data-rebuild", "Rebuild")}${button("svc-data-search-run", "Search")}</div>
        <p id="svc-data-index-count" class="svc-meta"></p><ul id="svc-data-hits" class="svc-list"></ul>
      </div>
      <div class="svc-block"><h3>File history</h3><ul id="svc-data-versions" class="svc-list"></ul>
        <div class="svc-row">${field("svc-data-from", "From version")}${field("svc-data-to", "To version")}${button("svc-data-history-diff", "Compare")}</div>
        <div class="svc-row">${field("svc-data-snapshot", "Snapshot id")}${field("svc-data-restore-path", "Restore path")}${button("svc-data-restore", "Restore")}</div>
        <ul id="svc-data-history" class="svc-list"></ul>
      </div>`;
    if (panel === "club")
        return `
      <div class="svc-block"><h3>Session</h3><div id="svc-club-session" class="svc-meta"></div>
        <div class="svc-row">${field("svc-club-session-id", "Session id")}${button("svc-club-session-get", "Load")}${button("svc-club-session-fork", "Fork")}</div>
        <div class="svc-row">${field("svc-club-template", "Template", "text", "python")}${field("svc-club-capacity", "Capacity", "number", "20")}${button("svc-club-session-create", "Create")}</div>
        <div class="svc-row">${field("svc-club-code", "Join code")}${field("svc-club-role", "Role", "text", "LEARNER")}${button("svc-club-session-join", "Join")}${button("svc-club-code-rotate", "Rotate code")}${button("svc-club-leaderboard", "Leaderboard")}</div>
      </div>
      <div class="svc-block"><h3>Tutorial progress</h3>
        <div class="svc-row">${field("svc-club-lesson", "Lesson id")}${field("svc-club-step", "Step id")}${button("svc-club-lesson-load", "Load lesson")}${button("svc-club-step-verify", "Verify step")}${button("svc-club-progress", "Progress")}</div>
        <p id="svc-club-progress-value" class="svc-meta"></p>
      </div>
      <div class="svc-block"><h3>Checkpoints</h3><ul id="svc-club-checkpoints" class="svc-list"></ul>
        <div class="svc-row">${field("svc-club-checkpoint-tag", "Tag")}${field("svc-club-checkpoint-note", "Note")}${button("svc-club-checkpoint-submit", "Submit")}${button("svc-club-checkpoint-list", "List")}${button("svc-club-export", "Export")}</div>
        ${area("svc-club-checkpoint-content", "Checkpoint content", 2)}
      </div>
      <div class="svc-block"><h3>Showcase</h3><ul id="svc-club-showcase" class="svc-list"></ul>${button("svc-club-showcase-refresh", "Refresh showcase")}</div>
      <div class="svc-block"><h3>Chat and moderation</h3><div id="svc-club-chat" class="svc-output"></div>
        <div class="svc-row">${field("svc-club-chat-text", "Message")}${button("svc-club-chat-send", "Post")}${button("svc-club-chat-refresh", "Refresh")}${button("svc-club-chat-pinned", "Pinned")}</div>
        <div class="svc-row">${field("svc-club-target", "Target user id")}${button("svc-club-mute", "Mute")}${button("svc-club-unmute", "Unmute")}${button("svc-club-remove", "Remove")}${button("svc-club-lock", "Lock")}${button("svc-club-unlock", "Unlock")}</div>
        <div class="svc-row">${button("svc-club-help-join", "Join help queue")}${button("svc-club-help-leave", "Leave help queue")}${button("svc-club-help-size", "Queue size")}${button("svc-club-help-next", "Next learner")}</div>
      </div>`;
    return `
      <div class="svc-block"><h3>Support and backups</h3>
        <div class="svc-row">${button("svc-ops-bundle", "Create support bundle")}<span id="svc-ops-bundle-result" class="svc-meta"></span></div>
        <div class="svc-row">${button("svc-ops-backup", "Backup status")}<span id="svc-ops-backup-result" class="svc-meta"></span></div>
      </div>
      <div class="svc-block"><h3>Audit</h3>
        <div class="svc-row">${field("svc-ops-audit-from", "From")}${field("svc-ops-audit-to", "To")}${field("svc-ops-audit-actor", "Actor")}${field("svc-ops-audit-action", "Action")}</div>
        <div class="svc-row">${field("svc-ops-audit-workspace", "Workspace")}${field("svc-ops-audit-result", "Result")}${field("svc-ops-audit-format", "Format", "text", "csv")}${button("svc-ops-audit-query", "Query")}${button("svc-ops-audit-export", "Export")}</div>
        <ul id="svc-ops-audit" class="svc-list"></ul><pre id="svc-ops-export" class="svc-output"></pre>
      </div>
      <div class="svc-block"><h3>GDPR</h3>
        <div class="svc-row">${field("svc-ops-gdpr-user", "User id")}${field("svc-ops-gdpr-confirm", "Confirmation")}${button("svc-ops-gdpr-export", "Export")}${button("svc-ops-gdpr-delete", "Delete")}</div>
        <p id="svc-ops-gdpr" class="svc-meta"></p>
      </div>
      <div class="svc-block"><h3>Approvals</h3><ul id="svc-ops-approvals" class="svc-list"></ul>
        <div class="svc-row">${field("svc-ops-approval-type", "Type")}${field("svc-ops-approval-target", "Target")}${button("svc-ops-approval-submit", "Submit")}</div>
      </div>`;
}

function value(id: string): string {
    return byId<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>(id).value.trim();
}

function guard(action: () => unknown): void {
    void Promise.resolve(action());
}

function list(id: string, rows: HTMLElement[]): void {
    byId(id).replaceChildren(...rows);
}

function item(text: string, actions: HTMLElement[] = []): HTMLLIElement {
    const row = document.createElement("li");
    row.className = "svc-item";
    const label = document.createElement("span");
    label.textContent = text;
    row.append(label, ...actions);
    return row;
}

function action(label: string, data: Record<string, string>): HTMLButtonElement {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "svc-action";
    button.textContent = label;
    for (const [key, entry] of Object.entries(data)) button.dataset[key] = entry;
    return button;
}

function table(host: HTMLElement, data: DataTable | null): void {
    if (!data) {
        host.textContent = "No table loaded.";
        return;
    }
    const element = document.createElement("table");
    element.className = "svc-table";
    const head = document.createElement("thead");
    const headRow = document.createElement("tr");
    for (const column of data.columns ?? []) {
        const cell = document.createElement("th");
        cell.textContent = column;
        headRow.append(cell);
    }
    head.append(headRow);
    const body = document.createElement("tbody");
    for (const row of data.rows ?? []) {
        const line = document.createElement("tr");
        for (const cellValue of row) {
            const cell = document.createElement("td");
            cell.textContent = cellValue;
            line.append(cell);
        }
        body.append(line);
    }
    element.append(head, body);
    host.replaceChildren(element);
}

function bindSection(panel: ServiceSection): void {
    const node = byId<HTMLElement>(`svc-${panel}-unavailable`);
    const available = (): boolean => serviceState[panel].available;
    effect(() => {
        node.hidden = available();
        byId(`svc-${panel}-status`).textContent = serviceState[panel].status;
        byId(`svc-${panel}-result`).textContent = serviceState[panel].result;
    });
    bindDisabled(byId<HTMLButtonElement>(`svc-${panel}-refresh`), () => !available());
    on(byId(`svc-${panel}-refresh`), "click", () => guard(() => refresh(panel)));
}

function refresh(panel: ServiceSection): Promise<void> {
    if (panel === "collaboration") return loadCollaboration();
    if (panel === "agent") return loadAgent();
    if (panel === "data") return loadData();
    if (panel === "club") return loadClub();
    return loadOps();
}

function bindCollaboration(): void {
    const p = paths();
    effect(() => {
        const s = serviceState.collaboration;
        list(
            "svc-collaboration-members",
            s.members.map((entry) =>
                item(entry, [
                    action("Change role", { act: "role", id: entry.split(":")[0] }),
                    action("Remove", { act: "remove", id: entry.split(":")[0] }),
                ]),
            ),
        );
        list(
            "svc-collaboration-presence",
            s.presence.map((entry) => item(entry)),
        );
        list(
            "svc-collaboration-comments",
            s.comments.map((entry) =>
                item(entry, [
                    action("Reply", { act: "reply", id: entry.split(":")[0] }),
                    action("Resolve", { act: "resolve", id: entry.split(":")[0] }),
                    action("Delete", { act: "delete", id: entry.split(":")[0] }),
                ]),
            ),
        );
        list(
            "svc-collaboration-shares",
            s.shares.map((entry) => item(entry, [action("Revoke", { act: "revoke", id: entry })])),
        );
        list(
            "svc-collaboration-audit",
            s.audit.map((entry) => item(entry)),
        );
    });
    on(byId("svc-collaboration-comments"), "click", (event) => {
        const button = (event.target as HTMLElement).closest<HTMLButtonElement>("button[data-act]");
        if (!button) return;
        const id = button.dataset.id ?? "";
        const act = button.dataset.act;
        if (act === "reply") {
            const text = window.prompt("Reply text") ?? "";
            if (text)
                guard(() =>
                    call("collaboration", `${p.workspace}/comments/${id}/reply`, {
                        method: "POST",
                        body: json({ body: text }),
                    }),
                );
        } else if (act === "resolve") {
            guard(() =>
                call("collaboration", `${p.workspace}/comments/${id}/resolve`, {
                    method: "POST",
                    body: json({ resolved: "true" }),
                }),
            );
        } else if (act === "delete") {
            guard(() =>
                call("collaboration", `${p.workspace}/comments/${id}`, { method: "DELETE" }),
            );
        }
    });
    on(byId("svc-collaboration-shares"), "click", (event) => {
        const button = (event.target as HTMLElement).closest<HTMLButtonElement>("button[data-act]");
        if (button?.dataset.act === "revoke")
            guard(() =>
                call("collaboration", `${p.workspace}/share-links/${button.dataset.id}`, {
                    method: "DELETE",
                }),
            );
    });
    on(byId("svc-collaboration-members"), "click", (event) => {
        const button = (event.target as HTMLElement).closest<HTMLButtonElement>("button[data-act]");
        if (!button) return;
        const target = button.dataset.id ?? "";
        if (button.dataset.act === "role") {
            const role = window.prompt("Role (VIEWER, EDITOR, OWNER)") ?? "";
            if (role)
                guard(() =>
                    call("collaboration", `${p.workspace}/members/${target}/role`, {
                        method: "POST",
                        body: json({ role }),
                    }),
                );
        } else {
            guard(() =>
                call("collaboration", `${p.workspace}/members/${target}`, { method: "DELETE" }),
            );
        }
    });
    on(byId("svc-collab-invite"), "click", () =>
        guard(() =>
            call("collaboration", `${p.workspace}/members/invite`, {
                method: "POST",
                body: json({
                    invitee: value("svc-collab-invitee"),
                    role: value("svc-collab-role"),
                }),
            }),
        ),
    );
    on(byId("svc-collab-accept"), "click", () =>
        guard(() =>
            call(
                "collaboration",
                `${p.workspace}/invitations/${encodeURIComponent(value("svc-collab-token"))}/accept`,
                { method: "POST", body: json({}) },
            ),
        ),
    );
    on(byId("svc-collab-presence-join"), "click", () =>
        guard(() =>
            call("collaboration", `${p.workspace}/presence/join`, {
                method: "POST",
                body: json({}),
            }),
        ),
    );
    on(byId("svc-collab-presence-leave"), "click", () =>
        guard(() =>
            call("collaboration", `${p.workspace}/presence/leave`, {
                method: "POST",
                body: json({}),
            }),
        ),
    );
    on(byId("svc-collab-presence-file"), "click", () =>
        guard(() =>
            call("collaboration", `${p.workspace}/presence/file`, {
                method: "POST",
                body: json({ path: value("svc-collab-file") }),
            }),
        ),
    );
    on(byId("svc-collab-comment-add"), "click", () =>
        guard(() =>
            call("collaboration", `${p.workspace}/comments`, {
                method: "POST",
                body: json({
                    path: value("svc-collab-comment-path"),
                    startLine: value("svc-collab-comment-line"),
                    endLine: value("svc-collab-comment-line"),
                    body: value("svc-collab-comment-text"),
                }),
            }),
        ),
    );
    on(byId("svc-collab-share-create"), "click", () =>
        guard(() =>
            call("collaboration", `${p.workspace}/share-links`, {
                method: "POST",
                body: json({
                    scope: value("svc-collab-share-scope"),
                    oneTime: String(byId<HTMLInputElement>("svc-collab-one-time").checked),
                }),
            }),
        ),
    );
}

const stagedPatches = new Map<string, string>();

function bindAgent(): void {
    const p = paths();
    effect(() => {
        const s = serviceState.agent;
        list(
            "svc-agent-sessions",
            s.sessions.map((session: AgentSession) =>
                item(`${session.name} (${session.status})`, [
                    action("Resume", { act: "resume", id: session.id }),
                    action("Restart", { act: "restart", id: session.id }),
                    action("Kill", { act: "kill", id: session.id }),
                    action("Transcript", { act: "transcript", id: session.id }),
                ]),
            ),
        );
        list(
            "svc-agent-prompts",
            s.prompts.map((prompt: AgentPrompt) =>
                item(`${prompt.name} (${prompt.owner})`, [
                    action("Render", { act: "render", id: prompt.id }),
                    action("Delete", { act: "delete", id: prompt.id }),
                ]),
            ),
        );
        list(
            "svc-agent-patches",
            s.patches.map((patch: AgentPatch) =>
                item(`${patch.filePath} (${patch.status})`, [
                    action("Review", { act: "review", id: patch.id }),
                ]),
            ),
        );
        const hunks = s.hunkText.split(/^@@ /m).slice(1);
        list(
            "svc-agent-hunks",
            hunks.map((hunk, index) =>
                item(`Hunk ${index}: ${hunk.split("\n")[0].replace(/^@@ /, "").slice(0, 60)}`, [
                    action("Accept", { act: "accept", id: String(index) }),
                    action("Reject", { act: "reject", id: String(index) }),
                ]),
            ),
        );
        list(
            "svc-agent-models",
            s.models.map((model) => item(`${model.id}, ${model.capability}`)),
        );
        byId("svc-agent-transcript").textContent = s.transcript.join("\n");
        byId("svc-agent-chat").textContent = s.messages
            .map((message: AgentMessage) => `${message.role}: ${message.text}`)
            .join("\n");
        const usage = s.usage;
        byId("svc-agent-usage").textContent = usage
            ? `${usage.messages} messages, ${usage.tokensIn} in, ${usage.tokensOut} out`
            : "No usage recorded.";
    });
    on(byId("svc-agent-sessions"), "click", (event) => {
        const button = (event.target as HTMLElement).closest<HTMLButtonElement>("button[data-act]");
        if (!button) return;
        const id = button.dataset.id ?? "";
        const act = button.dataset.act;
        if (act === "transcript")
            guard(async () => {
                const lines = await call<string[]>("agent", `${p.agent}/sessions/${id}/transcript`);
                if (lines) serviceState.agent.transcript = lines;
            });
        else if (act && ["resume", "restart", "kill"].includes(act))
            guard(async () => {
                const session = await call<AgentSession>(
                    "agent",
                    `${p.agent}/sessions/${id}/${act}`,
                    { method: "POST", body: json({}) },
                );
                if (session) await loadAgent();
            });
    });
    on(byId("svc-agent-prompts"), "click", (event) => {
        const button = (event.target as HTMLElement).closest<HTMLButtonElement>("button[data-act]");
        if (!button) return;
        const id = button.dataset.id ?? "";
        if (button.dataset.act === "delete")
            guard(async () => {
                await call("agent", `/api/v1/agent/prompts/${id}`, { method: "DELETE" });
                await loadAgent();
            });
        else
            guard(async () => {
                const result = await call<{ rendered: string }>(
                    "agent",
                    `/api/v1/agent/prompts/${id}/render`,
                    {
                        method: "POST",
                        body: json({
                            file: value("svc-agent-chat-file"),
                            selection: value("svc-agent-chat-selection"),
                        }),
                    },
                );
                if (result) byId("svc-agent-chat-text").textContent = result.rendered;
            });
    });
    on(byId("svc-agent-patches"), "click", (event) => {
        const button = (event.target as HTMLElement).closest<HTMLButtonElement>("button[data-act]");
        if (button?.dataset.act === "review") {
            const id = button.dataset.id ?? "";
            serviceState.agent.selectedPatchId = id;
            serviceState.agent.hunkText = stagedPatches.get(id) ?? "";
        }
    });
    on(byId("svc-agent-hunks"), "click", (event) => {
        const button = (event.target as HTMLElement).closest<HTMLButtonElement>("button[data-act]");
        const patchId = serviceState.agent.selectedPatchId;
        if (!button || !patchId) return;
        guard(async () => {
            const decision = button.dataset.act === "accept" ? "accept" : "reject";
            await call(
                "agent",
                `${p.agent}/diffs/${patchId}/hunks/${button.dataset.id}/${decision}`,
                { method: "POST", body: json({}) },
            );
            await loadAgent();
        });
    });
    on(byId("svc-agent-session-create"), "click", () =>
        guard(async () => {
            await call("agent", `${p.agent}/sessions`, {
                method: "POST",
                body: json({ name: value("svc-agent-session-name") }),
            });
            await loadAgent();
        }),
    );
    on(byId("svc-agent-line-add"), "click", () =>
        guard(async () => {
            await call(
                "agent",
                `${p.agent}/sessions/${value("svc-agent-session-id") || serviceState.agent.sessions[0]?.id || ""}/transcript`,
                {
                    method: "POST",
                    body: json({ line: value("svc-agent-line") }),
                },
            );
            await loadAgent();
        }),
    );
    on(byId("svc-agent-prompt-create"), "click", () =>
        guard(async () => {
            await call("agent", "/api/v1/agent/prompts", {
                method: "POST",
                body: json({
                    name: value("svc-agent-prompt-name"),
                    text: value("svc-agent-prompt-text"),
                }),
            });
            await loadAgent();
        }),
    );
    on(byId("svc-agent-patch-stage"), "click", () =>
        guard(async () => {
            const text = value("svc-agent-patch-text");
            const patch = await call<AgentPatch>("agent", `${p.agent}/diffs`, {
                method: "POST",
                body: json({ patch: text }),
            });
            if (patch) {
                stagedPatches.set(patch.id, text);
                serviceState.agent.selectedPatchId = patch.id;
                serviceState.agent.hunkText = text;
                await loadAgent();
            }
        }),
    );
    on(byId("svc-agent-chat-send"), "click", () =>
        guard(async () => {
            const messages = await call<AgentMessage[]>("agent", `${p.agent}/chat`, {
                method: "POST",
                body: json({
                    text: value("svc-agent-chat-text"),
                    file: value("svc-agent-chat-file"),
                    selection: value("svc-agent-chat-selection"),
                }),
            });
            if (messages) serviceState.agent.messages = messages;
        }),
    );
    on(byId("svc-agent-chat-cancel"), "click", () =>
        guard(() => call("agent", `${p.agent}/chat/cancel`, { method: "POST", body: json({}) })),
    );
}

function bindData(): void {
    const p = paths();
    const dbPath = (): string => value("svc-data-sqlite-path");
    const showTable = (data: DataTable | null): void => table(byId("svc-data-table-view"), data);
    effect(() => {
        const s = serviceState.data;
        byId<HTMLSelectElement>("svc-data-table").replaceChildren(
            ...s.tables.map((name) => {
                const option = document.createElement("option");
                option.value = name;
                option.textContent = name;
                return option;
            }),
        );
        list(
            "svc-data-hits",
            s.hits.map((hit: SearchHit) => item(`${hit.path}: ${hit.excerpt}`)),
        );
        list(
            "svc-data-versions",
            s.versions.map((version: FileVersion) =>
                item(`${version.id}: ${version.tag}, ${version.createdAt}`),
            ),
        );
        list(
            "svc-data-history",
            s.history.map((diff: FileDiff) => item(`${diff.path}: ${diff.change}`)),
        );
        byId("svc-data-index-count").textContent = `${s.indexCount} indexed files.`;
    });
    on(byId("svc-data-sqlite-tables"), "click", () =>
        guard(async () => {
            const params = new URLSearchParams({ path: dbPath() });
            const tables = await call<string[]>("data", `${p.data}/sqlite/tables?${params}`);
            if (tables) serviceState.data.tables = tables;
        }),
    );
    on(byId("svc-data-sqlite-schema"), "click", () =>
        guard(async () => {
            const params = new URLSearchParams({ path: dbPath() });
            const data = await call<DataTable>("data", `${p.data}/sqlite/schema?${params}`);
            if (data) showTable(data);
        }),
    );
    on(byId("svc-data-sqlite-rows"), "click", () =>
        guard(async () => {
            const params = new URLSearchParams({
                path: dbPath(),
                table: value("svc-data-table"),
                offset: "0",
                limit: "100",
            });
            const data = await call<DataTable>("data", `${p.data}/sqlite/rows?${params}`);
            if (data) showTable(data);
        }),
    );
    on(byId("svc-data-sqlite-query"), "click", () =>
        guard(async () => {
            const data = await call<DataTable>("data", `${p.data}/sqlite/query`, {
                method: "POST",
                body: json({ path: dbPath(), sql: value("svc-data-sql") }),
            });
            if (data) showTable(data);
        }),
    );
    on(byId("svc-data-csv-parse"), "click", () =>
        guard(async () => {
            const data = await call<DataTable>("data", `${p.data}/csv/parse`, {
                method: "POST",
                body: json({ content: value("svc-data-csv"), maxRows: 100 }),
            });
            if (data) {
                serviceState.data.csv = data;
                table(byId("svc-data-csv-view"), data);
            }
        }),
    );
    on(byId("svc-data-csv-write"), "click", () =>
        guard(async () => {
            const result = await call<{ content: string }>("data", `${p.data}/csv/write`, {
                method: "POST",
                body: json({ content: value("svc-data-csv") }),
            });
            if (result) byId("svc-data-csv").textContent = result.content;
        }),
    );
    on(byId("svc-data-csv-save"), "click", () =>
        guard(() =>
            call("data", `${p.data}/csv/save`, {
                method: "POST",
                body: json({
                    path: value("svc-data-csv-path"),
                    content: value("svc-data-csv"),
                    expectedVersion: value("svc-data-csv-version"),
                }),
            }),
        ),
    );
    const notebook = (): string => encodeURIComponent(value("svc-data-notebook"));
    on(byId("svc-data-cells-load"), "click", () =>
        guard(async () => {
            const result = await call<{ cells: NotebookCell[] }>(
                "data",
                `${p.data}/notebooks/${notebook()}/cells`,
            );
            if (result) {
                serviceState.data.cells = result.cells;
                byId("svc-data-cells").textContent = result.cells
                    .map((cell) => cell.source)
                    .join("\n");
            }
        }),
    );
    on(byId("svc-data-cells-save"), "click", () =>
        guard(() =>
            call("data", `${p.data}/notebooks/${notebook()}/cells`, {
                method: "POST",
                body: json({
                    cells: value("svc-data-cells")
                        .split("\n")
                        .map((source, index) => ({ id: `cell-${index}`, source })),
                }),
            }),
        ),
    );
    on(byId("svc-data-notebook-run"), "click", () =>
        guard(async () => {
            const result = await call<{ outputs: CellOutput[] }>(
                "data",
                `${p.data}/notebooks/${notebook()}/run`,
                {
                    method: "POST",
                    body: json({ taskId: value("svc-data-task-id") }),
                },
            );
            if (result) {
                serviceState.data.outputs = result.outputs;
                byId("svc-data-outputs").textContent = result.outputs
                    .map((entry) => `${entry.type}: ${entry.data}`)
                    .join("\n");
            }
        }),
    );
    on(byId("svc-data-index"), "click", () =>
        guard(async () => {
            const result = await call<{ count: number }>("data", `${p.data}/search/index`, {
                method: "POST",
                body: json({}),
            });
            if (result) serviceState.data.indexCount = result.count;
        }),
    );
    on(byId("svc-data-rebuild"), "click", () =>
        guard(async () => {
            const result = await call<{ count: number }>("data", `${p.data}/search/rebuild`, {
                method: "POST",
                body: json({}),
            });
            if (result) serviceState.data.indexCount = result.count;
        }),
    );
    on(byId("svc-data-search-run"), "click", () =>
        guard(async () => {
            const hits = await call<SearchHit[]>("data", `${p.data}/search/query`, {
                method: "POST",
                body: json({ query: value("svc-data-search"), limit: 50 }),
            });
            if (hits) serviceState.data.hits = hits;
        }),
    );
    on(byId("svc-data-history-diff"), "click", () =>
        guard(async () => {
            const diffs = await call<FileDiff[]>("data", `${p.data}/history/diff`, {
                method: "POST",
                body: json({ fromId: value("svc-data-from"), toId: value("svc-data-to") }),
            });
            if (diffs) serviceState.data.history = diffs;
        }),
    );
    on(byId("svc-data-restore"), "click", () =>
        guard(() =>
            call("data", `${p.data}/history/restore`, {
                method: "POST",
                body: json({
                    snapshotId: value("svc-data-snapshot"),
                    path: value("svc-data-restore-path"),
                    expectedVersion: "",
                }),
            }),
        ),
    );
}

function bindClub(): void {
    const p = paths();
    const session = (): string => encodeURIComponent(value("svc-club-session-id"));
    effect(() => {
        const s = serviceState.club;
        byId("svc-club-session").textContent = s.session
            ? `${s.session.template}, ${s.session.participants}/${s.session.capacity} participants, code ${s.session.joinCode}, leaderboard ${s.session.leaderboard}, locked ${s.session.locked}`
            : "No session loaded.";
        byId("svc-club-progress-value").textContent = `${s.progress} steps completed.`;
        list(
            "svc-club-checkpoints",
            s.checkpoints.map((entry: ClubCheckpoint) =>
                item(
                    `${entry.snapshotTag}: ${entry.note || "no note"}, ${entry.promotedTo || "not promoted"}`,
                ),
            ),
        );
        list(
            "svc-club-showcase",
            s.showcase.map((project: ClubProject) =>
                item(`${project.title}: ${project.description}`),
            ),
        );
        byId("svc-club-chat").textContent = s.chat
            .map(
                (message: ClubChat) =>
                    `${message.pinned ? "[pinned] " : ""}${message.author}: ${message.text}`,
            )
            .join("\n");
    });
    on(byId("svc-club-session-get"), "click", () =>
        guard(async () => {
            const result = await call<ClubSession>("club", `${p.club}/sessions/${session()}`);
            if (result) serviceState.club.session = result;
        }),
    );
    on(byId("svc-club-session-create"), "click", () =>
        guard(() => {
            const startsAt = new Date();
            const endsAt = new Date(startsAt.getTime() + 86_400_000);
            return call("club", `${p.club}/sessions`, {
                method: "POST",
                body: json({
                    template: value("svc-club-template"),
                    capacity: Number(value("svc-club-capacity")),
                    startsAt: startsAt.toISOString(),
                    endsAt: endsAt.toISOString(),
                }),
            });
        }),
    );
    on(byId("svc-club-session-join"), "click", () =>
        guard(() =>
            call("club", `${p.club}/sessions/${session()}/join`, {
                method: "POST",
                body: json({ code: value("svc-club-code"), role: value("svc-club-role") }),
            }),
        ),
    );
    on(byId("svc-club-session-fork"), "click", () =>
        guard(() =>
            call("club", `${p.club}/sessions/${session()}/fork`, {
                method: "POST",
                body: json({}),
            }),
        ),
    );
    on(byId("svc-club-code-rotate"), "click", () =>
        guard(async () => {
            const result = await call<ClubSession>(
                "club",
                `${p.club}/sessions/${session()}/join-code/rotate`,
                { method: "POST", body: json({}) },
            );
            if (result) serviceState.club.session = result;
        }),
    );
    on(byId("svc-club-leaderboard"), "click", () =>
        guard(() =>
            call("club", `${p.club}/sessions/${session()}/leaderboard`, {
                method: "POST",
                body: json({}),
            }),
        ),
    );
    on(byId("svc-club-lesson-load"), "click", () =>
        guard(() =>
            call("club", `${p.club}/tutorials/${encodeURIComponent(value("svc-club-lesson"))}`),
        ),
    );
    on(byId("svc-club-step-verify"), "click", () =>
        guard(() =>
            call("club", `${p.club}/sessions/${session()}/steps/verify`, {
                method: "POST",
                body: json({ lessonId: value("svc-club-lesson"), stepId: value("svc-club-step") }),
            }),
        ),
    );
    on(byId("svc-club-progress"), "click", () =>
        guard(async () => {
            const result = await call<{ count: number }>(
                "club",
                `${p.club}/sessions/${session()}/tutorials/${encodeURIComponent(value("svc-club-lesson"))}/progress`,
            );
            if (result) serviceState.club.progress = result.count;
        }),
    );
    on(byId("svc-club-checkpoint-submit"), "click", () =>
        guard(async () => {
            await call("club", `${p.club}/sessions/${session()}/checkpoints`, {
                method: "POST",
                body: json({
                    tag: value("svc-club-checkpoint-tag"),
                    note: value("svc-club-checkpoint-note"),
                    content: value("svc-club-checkpoint-content"),
                }),
            });
            await loadClub();
        }),
    );
    on(byId("svc-club-checkpoint-list"), "click", () =>
        guard(async () => {
            const rows = await call<ClubCheckpoint[]>(
                "club",
                `${p.club}/sessions/${session()}/checkpoints`,
            );
            if (rows) serviceState.club.checkpoints = rows;
        }),
    );
    on(byId("svc-club-export"), "click", () =>
        guard(() => call("club", `${p.club}/sessions/${session()}/export`)),
    );
    on(byId("svc-club-showcase-refresh"), "click", () => guard(() => loadClub()));
    on(byId("svc-club-chat-send"), "click", () =>
        guard(async () => {
            await call("club", `${p.club}/sessions/${session()}/chat`, {
                method: "POST",
                body: json({ text: value("svc-club-chat-text") }),
            });
            await clubHistory();
        }),
    );
    on(byId("svc-club-chat-refresh"), "click", () => guard(() => clubHistory()));
    on(byId("svc-club-chat-pinned"), "click", () =>
        guard(async () => {
            const rows = await call<ClubChat[]>(
                "club",
                `${p.club}/sessions/${session()}/chat/pinned`,
            );
            if (rows) serviceState.club.chat = rows;
        }),
    );
    on(byId("svc-club-mute"), "click", () => clubModeration("mute"));
    on(byId("svc-club-unmute"), "click", () => clubModeration("unmute"));
    on(byId("svc-club-remove"), "click", () => clubModeration("remove"));
    on(byId("svc-club-lock"), "click", () =>
        guard(() =>
            call("club", `${p.club}/sessions/${session()}/moderation/lock`, {
                method: "POST",
                body: json({}),
            }),
        ),
    );
    on(byId("svc-club-unlock"), "click", () =>
        guard(() =>
            call("club", `${p.club}/sessions/${session()}/moderation/unlock`, {
                method: "POST",
                body: json({}),
            }),
        ),
    );
    on(byId("svc-club-help-join"), "click", () =>
        guard(() =>
            call("club", `${p.club}/sessions/${session()}/help/join`, {
                method: "POST",
                body: json({}),
            }),
        ),
    );
    on(byId("svc-club-help-leave"), "click", () =>
        guard(() =>
            call("club", `${p.club}/sessions/${session()}/help/leave`, {
                method: "POST",
                body: json({}),
            }),
        ),
    );
    on(byId("svc-club-help-size"), "click", () =>
        guard(async () => {
            const result = await call<{ count: number }>(
                "club",
                `${p.club}/sessions/${session()}/help/size`,
            );
            if (result) serviceState.club.helpSize = result.count;
        }),
    );
    on(byId("svc-club-help-next"), "click", () =>
        guard(() =>
            call("club", `${p.club}/sessions/${session()}/help/next`, {
                method: "POST",
                body: json({}),
            }),
        ),
    );
}

async function clubHistory(): Promise<void> {
    const rows = await call<ClubChat[]>(
        "club",
        `${paths().club}/sessions/${encodeURIComponent(value("svc-club-session-id"))}/chat`,
    );
    if (rows) serviceState.club.chat = rows;
}

function clubModeration(verb: string): void {
    guard(() =>
        call(
            "club",
            `${paths().club}/sessions/${encodeURIComponent(value("svc-club-session-id"))}/moderation/${verb}`,
            {
                method: "POST",
                body: json({ target: value("svc-club-target") }),
            },
        ),
    );
}

function bindOps(): void {
    const p = paths();
    effect(() => {
        const s = serviceState.ops;
        byId("svc-ops-bundle-result").textContent = s.bundle;
        byId("svc-ops-backup-result").textContent = s.backup
            ? `${s.backup.count} backups, latest ${s.backup.latest || "none"}, RPO ${s.backup.rpoMinutes}m, RTO ${s.backup.rtoMinutes}m`
            : "";
        byId("svc-ops-export").textContent = s.exportText;
        byId("svc-ops-gdpr").textContent = s.result;
        list(
            "svc-ops-audit",
            s.audit.map((row: AuditRow) =>
                item(`${row.timestamp} ${row.actor} ${row.action} ${row.workspace} ${row.result}`),
            ),
        );
        list(
            "svc-ops-approvals",
            s.approvals.map((approval) =>
                item(`${approval.type} ${approval.target} ${approval.status}`, [
                    action("Approve", { act: "approve", id: approval.id }),
                    action("Reject", { act: "reject", id: approval.id }),
                ]),
            ),
        );
    });
    const auditBody = (): Record<string, string> => {
        const body: Record<string, string> = {};
        for (const key of ["from", "to", "actor", "action", "workspace", "result"]) {
            const entered = value(`svc-ops-audit-${key}`);
            if (entered) body[key] = entered;
        }
        return body;
    };
    on(byId("svc-ops-bundle"), "click", () =>
        guard(async () => {
            const result = await call<{ fileName: string; verified: boolean }>(
                "ops",
                `${p.ops}/support-bundle`,
                { method: "POST", body: json({}) },
            );
            if (result) serviceState.ops.bundle = `${result.fileName}, verified ${result.verified}`;
        }),
    );
    on(byId("svc-ops-backup"), "click", () => guard(() => loadOps()));
    on(byId("svc-ops-audit-query"), "click", () =>
        guard(async () => {
            const rows = await call<AuditRow[]>("ops", `${p.ops}/audit/query`, {
                method: "POST",
                body: json(auditBody()),
            });
            if (rows) serviceState.ops.audit = rows;
        }),
    );
    on(byId("svc-ops-audit-export"), "click", () =>
        guard(async () => {
            const body = auditBody();
            body.format = value("svc-ops-audit-format");
            const result = await call<{ content: string }>("ops", `${p.ops}/audit/export`, {
                method: "POST",
                body: json(body),
            });
            if (result) serviceState.ops.exportText = result.content;
        }),
    );
    on(byId("svc-ops-gdpr-export"), "click", () =>
        guard(() =>
            call("ops", `${p.ops}/gdpr/export`, {
                method: "POST",
                body: json({ userId: value("svc-ops-gdpr-user") }),
            }),
        ),
    );
    on(byId("svc-ops-gdpr-delete"), "click", () => {
        if (!window.confirm("Permanently delete this user's data?")) return;
        guard(() =>
            call("ops", `${p.ops}/gdpr/delete`, {
                method: "POST",
                body: json({
                    userId: value("svc-ops-gdpr-user"),
                    confirmation: value("svc-ops-gdpr-confirm"),
                }),
            }),
        );
    });
    on(byId("svc-ops-approval-submit"), "click", () =>
        guard(async () => {
            await call("ops", `${p.ops}/approvals`, {
                method: "POST",
                body: json({
                    type: value("svc-ops-approval-type"),
                    target: value("svc-ops-approval-target"),
                    payload: {},
                }),
            });
            await loadOps();
        }),
    );
    on(byId("svc-ops-approvals"), "click", (event) => {
        const button = (event.target as HTMLElement).closest<HTMLButtonElement>("button[data-act]");
        if (!button) return;
        const id = button.dataset.id ?? "";
        if (button.dataset.act === "approve")
            guard(async () => {
                await call("ops", `${p.ops}/approvals/${id}/approve`, {
                    method: "POST",
                    body: json({}),
                });
                await loadOps();
            });
        else
            guard(async () => {
                const reason = window.prompt("Rejection reason") ?? "";
                await call("ops", `${p.ops}/approvals/${id}/reject`, {
                    method: "POST",
                    body: json({ reason }),
                });
                await loadOps();
            });
    });
}

export function bindServicePanels(): void {
    for (const { panel } of sections) bindSection(panel);
    bindCollaboration();
    bindAgent();
    bindData();
    bindClub();
    bindOps();
    effect(() => {
        const panel = state.activePanel;
        for (const { panel: name } of sections) {
            byId(`svc-${name}-status`)
                .closest<HTMLElement>(".service-panel")
                ?.classList.toggle("is-active", panel === name);
        }
        if (panel === "collaboration" && !serviceState.collaboration.loaded)
            void loadCollaboration();
        if (panel === "agent" && !serviceState.agent.loaded) void loadAgent();
        if (panel === "data" && !serviceState.data.loaded) void loadData();
        if (panel === "club" && !serviceState.club.loaded) void loadClub();
        if (panel === "ops" && !serviceState.ops.loaded) void loadOps();
    });
}
