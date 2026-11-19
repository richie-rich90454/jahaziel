///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { createStore } from "solid-js";
import { api, errorText } from "./api";
import { state, toast } from "./store";

export interface AgentSession {
    id: string;
    name: string;
    status: string;
    workspaceId: string;
}
export interface AgentMessage {
    id: string;
    role: string;
    text: string;
    activeFile: string;
    createdAt: string;
}
export interface AgentPrompt {
    id: string;
    name: string;
    body: string;
    owner: string;
}
export interface AgentModel {
    id: string;
    capability: string;
}
export interface AgentUsage {
    tokensIn: number;
    tokensOut: number;
    messages: number;
}
export interface AgentPatch {
    id: string;
    filePath: string;
    status: string;
}
export interface DataTable {
    columns: string[];
    rows: string[][];
}
export interface NotebookCell {
    id: string;
    source: string;
}
export interface CellOutput {
    type: string;
    data: string;
}
export interface SearchHit {
    path: string;
    excerpt: string;
}
export interface FileVersion {
    id: string;
    tag: string;
    createdAt: string;
}
export interface FileDiff {
    path: string;
    change: string;
}
export interface ClubSession {
    id: string;
    joinCode: string;
    template: string;
    capacity: number;
    participants: number;
    leaderboard: boolean;
    locked: boolean;
}
export interface ClubCheckpoint {
    id: string;
    snapshotTag: string;
    note: string;
    feedback: string;
    promotedTo: string;
}
export interface ClubProject {
    id: string;
    title: string;
    description: string;
    tags: string[];
    screenshots: string[];
    sourceLabel: string;
}
export interface ClubChat {
    id: string;
    author: string;
    text: string;
    pinned: boolean;
}
export interface BackupStatus {
    latest: string;
    count: number;
    rpoMinutes: number;
    rtoMinutes: number;
}
export interface AuditRow {
    eventId: string;
    timestamp: string;
    actor: string;
    action: string;
    workspace: string;
    result: string;
}
export interface Approval {
    id: string;
    type: string;
    requester: string;
    target: string;
    status: string;
    createdAt: string;
}

interface SectionState {
    status: string;
    available: boolean;
    loaded: boolean;
    result: string;
}
interface CollaborationState extends SectionState {
    members: string[];
    presence: string[];
    comments: string[];
    shares: string[];
    audit: string[];
}
interface AgentState extends SectionState {
    sessions: AgentSession[];
    prompts: AgentPrompt[];
    models: AgentModel[];
    usage: AgentUsage | null;
    messages: AgentMessage[];
    patches: AgentPatch[];
    transcript: string[];
    hunkText: string;
    selectedPatchId: string;
}
interface DataState extends SectionState {
    tables: string[];
    table: DataTable | null;
    csv: DataTable | null;
    cells: NotebookCell[];
    outputs: CellOutput[];
    hits: SearchHit[];
    indexCount: number;
    versions: FileVersion[];
    history: FileDiff[];
}
interface ClubState extends SectionState {
    session: ClubSession | null;
    progress: number;
    checkpoints: ClubCheckpoint[];
    showcase: ClubProject[];
    chat: ClubChat[];
    helpSize: number;
}
interface OpsState extends SectionState {
    bundle: string;
    backup: BackupStatus | null;
    audit: AuditRow[];
    exportText: string;
    approvals: Approval[];
}

export interface ServiceState {
    collaboration: CollaborationState;
    agent: AgentState;
    data: DataState;
    club: ClubState;
    ops: OpsState;
}

export type ServiceSection = keyof ServiceState;

const [serviceState, rawSetServiceState] = createStore<ServiceState>({
    collaboration: {
        status: "Not loaded",
        available: false,
        loaded: false,
        result: "",
        members: [],
        presence: [],
        comments: [],
        shares: [],
        audit: [],
    },
    agent: {
        status: "Not loaded",
        available: false,
        loaded: false,
        result: "",
        sessions: [],
        prompts: [],
        models: [],
        usage: null,
        messages: [],
        patches: [],
        transcript: [],
        hunkText: "",
        selectedPatchId: "",
    },
    data: {
        status: "Not loaded",
        available: false,
        loaded: false,
        result: "",
        tables: [],
        table: null,
        csv: null,
        cells: [],
        outputs: [],
        hits: [],
        indexCount: 0,
        versions: [],
        history: [],
    },
    club: {
        status: "Not loaded",
        available: false,
        loaded: false,
        result: "",
        session: null,
        progress: 0,
        checkpoints: [],
        showcase: [],
        chat: [],
        helpSize: 0,
    },
    ops: {
        status: "Not loaded",
        available: false,
        loaded: false,
        result: "",
        bundle: "",
        backup: null,
        audit: [],
        exportText: "",
        approvals: [],
    },
});

export { serviceState };

function patch(section: ServiceSection, value: Record<string, unknown>): void {
    rawSetServiceState((draft) => {
        Object.assign(draft[section], value);
    });
}

export function unavailableText(failure: unknown): string {
    const message = errorText(failure);
    return /404|not found|could not be reached/i.test(message)
        ? "Unavailable on this host"
        : message;
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const { body } = await api<T>(path, init);
    return body;
}

function json(body: unknown): string {
    return JSON.stringify(body ?? {});
}

function workspaceId(): string {
    return state.workspace?.workspaceId ?? "";
}

/** Invokes an endpoint from a control. Failures become an explicit panel state. */
export async function call<T>(
    section: ServiceSection,
    path: string,
    init: RequestInit = {},
): Promise<T | null> {
    try {
        const body = await request<T>(path, init);
        patch(section, { status: "Ready", available: true, result: "Done." });
        return body;
    } catch (failure) {
        const message = unavailableText(failure);
        patch(section, {
            status: message,
            available: message !== "Unavailable on this host",
            result: message,
        });
        toast(message, "error");
        return null;
    }
}

async function load<K extends ServiceSection>(
    section: K,
    jobs: Array<() => Promise<void>>,
): Promise<void> {
    patch(section, { status: "Loading…", loaded: false });
    const settled = await Promise.allSettled(jobs.map((job) => job()));
    const rejected = settled.filter(
        (item): item is PromiseRejectedResult => item.status === "rejected",
    );
    if (
        rejected.length === jobs.length &&
        rejected.every((item) => unavailableText(item.reason) === "Unavailable on this host")
    ) {
        patch(section, { status: "Unavailable on this host", available: false, loaded: true });
        return;
    }
    if (rejected.length > 0) {
        patch(section, {
            status: `Limited access (${rejected.length} endpoint${rejected.length === 1 ? "" : "s"} unavailable)`,
            available: true,
            loaded: true,
        });
        return;
    }
    patch(section, { status: "Ready", available: true, loaded: true });
}

function withoutWorkspace(section: ServiceSection): boolean {
    if (workspaceId()) return false;
    patch(section, {
        status: "Unavailable on this host",
        available: false,
        loaded: true,
        result: "Open a workspace to use this panel.",
    });
    return true;
}

export async function loadCollaboration(): Promise<void> {
    if (withoutWorkspace("collaboration")) return;
    const base = `/api/v1/workspaces/${workspaceId()}`;
    await load("collaboration", [
        async () => patch("collaboration", { members: await request<string[]>(`${base}/members`) }),
        async () =>
            patch("collaboration", { presence: await request<string[]>(`${base}/presence`) }),
        async () =>
            patch("collaboration", { comments: await request<string[]>(`${base}/comments`) }),
        async () =>
            patch("collaboration", { shares: await request<string[]>(`${base}/share-links`) }),
        async () => patch("collaboration", { audit: await request<string[]>(`${base}/audit`) }),
    ]);
}

export async function loadAgent(): Promise<void> {
    if (withoutWorkspace("agent")) return;
    const id = workspaceId();
    const base = `/api/v1/agent/workspaces/${id}`;
    await load("agent", [
        async () => patch("agent", { sessions: await request<AgentSession[]>(`${base}/sessions`) }),
        async () =>
            patch("agent", { prompts: await request<AgentPrompt[]>("/api/v1/agent/prompts") }),
        async () => patch("agent", { models: await request<AgentModel[]>("/api/v1/agent/models") }),
        async () => patch("agent", { usage: await request<AgentUsage>("/api/v1/agent/usage/me") }),
        async () => patch("agent", { messages: await request<AgentMessage[]>(`${base}/chat`) }),
        async () => patch("agent", { patches: await request<AgentPatch[]>(`${base}/diffs`) }),
    ]);
}

export async function loadData(): Promise<void> {
    if (withoutWorkspace("data")) return;
    const base = `/api/v1/workspaces/${workspaceId()}/data`;
    await load("data", [
        async () =>
            patch("data", { versions: await request<FileVersion[]>(`${base}/history/versions`) }),
    ]);
}

export async function loadClub(): Promise<void> {
    await load("club", [
        async () =>
            patch("club", { showcase: await request<ClubProject[]>("/api/v1/club/showcase") }),
    ]);
}

export async function loadOps(): Promise<void> {
    await load("ops", [
        async () =>
            patch("ops", {
                backup: await request<BackupStatus>("/api/v1/admin/ops/backups/status"),
            }),
        async () =>
            patch("ops", {
                approvals: await request<Approval[]>("/api/v1/admin/ops/approvals"),
            }),
    ]);
}

export function paths(): Record<string, string> {
    const id = workspaceId();
    return {
        workspace: `/api/v1/workspaces/${id}`,
        agent: `/api/v1/agent/workspaces/${id}`,
        data: `/api/v1/workspaces/${id}/data`,
        club: "/api/v1/club",
        ops: "/api/v1/admin/ops",
    };
}

export { json, request, patch, workspaceId };
