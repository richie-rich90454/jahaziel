///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { api, errorText } from "./api";
import { byId } from "./dom";
import {
    setState,
    state,
    toast,
    type DebugTarget,
    type SetupStatus,
    type TaskRun,
    type WorkspaceTask,
} from "./store";
import { runtime, setupSummaries, workspaceRunning } from "./runtime";
import { refreshPortStatuses } from "./collaboration";
import { updateDebugControls } from "./debug";

export async function refreshTasks(): Promise<void> {
    const workspace = state.workspace;
    if (!workspace) return;
    ensureHistoryLoaded();
    const [setupResult, tasksResult, latestRunResult] = await Promise.all([
        api<SetupStatus>(`/api/v1/workspaces/${workspace.workspaceId}/setup`),
        api<WorkspaceTask[]>(`/api/v1/workspaces/${workspace.workspaceId}/tasks`),
        api<TaskRun>(`/api/v1/workspaces/${workspace.workspaceId}/task-runs/latest`),
    ]);
    if (state.workspace?.workspaceId !== workspace.workspaceId) return;
    const setup = setupResult.body;
    renderSetupStatus(setup);
    byId("setup-output").textContent = setup.output;
    setState("setupOutput", setup.output);
    setState("setupOutputHidden", !setup.output);
    setState("tasks", tasksResult.body);
    const latestRun = latestRunResult.body;
    recordTaskRun(latestRun);
    renderTaskRun(latestRun);
    await refreshDebugTargets().catch(() => {
        setState("debugTargets", []);
        renderDebugTargets();
    });
    if (latestRun?.status === "RUNNING") void pollTaskRun(workspace.workspaceId, latestRun);
    if (setup.status === "RUNNING" && !runtime.setupPolling) void pollSetup(workspace.workspaceId);
}

export async function refreshDebugTargets(): Promise<void> {
    const workspace = state.workspace;
    if (!workspace) return;
    const { body } = await api<DebugTarget[]>(
        `/api/v1/workspaces/${workspace.workspaceId}/tasks/debug-targets`,
    );
    if (state.workspace?.workspaceId !== workspace.workspaceId) return;
    setState("debugTargets", body);
    renderDebugTargets();
}

function renderDebugTargets(): void {
    const targets = state.debugTargets;
    const previous = state.debugAttachTarget;
    setState(
        "debugAttachTarget",
        targets.length === 0
            ? ""
            : targets.some((target) => target.id === previous)
              ? previous
              : targets[0].id,
    );
    const selectedConfig = state.debugConfigs.find(
        (configuration) => configuration.name === state.debugConfigName,
    );
    if (selectedConfig?.request === "attach") {
        const matchingTarget = targets.find(
            (target) => target.languageId === selectedConfig.languageId,
        );
        if (matchingTarget) {
            setState("debugAttachTarget", matchingTarget.id);
            setState(
                "debugConfigStatus",
                `${selectedConfig.name} selected. Attach to the matching running task.`,
            );
        }
    }
    updateDebugControls();
}

async function pollSetup(workspaceId: string): Promise<void> {
    runtime.setupPolling = true;
    try {
        while (state.workspace?.workspaceId === workspaceId) {
            await new Promise((resolve) => window.setTimeout(resolve, 1000));
            const { body } = await api<SetupStatus>(`/api/v1/workspaces/${workspaceId}/setup`);
            renderSetupStatus(body);
            setState("setupOutput", body.output);
            setState("setupOutputHidden", !body.output);
            if (body.status !== "RUNNING") break;
        }
    } finally {
        runtime.setupPolling = false;
        if (state.workspace?.workspaceId === workspaceId) await refreshTasks();
    }
}

function renderSetupStatus(setup: SetupStatus): void {
    setState("setupState", setup.status);
    setState(
        "setupSummary",
        setup.error || setupSummaries[setup.status] || "Template setup status is unavailable.",
    );
    setState(
        "setupRetryHidden",
        !(setup.status === "FAILED" || setup.status === "PENDING") || !workspaceRunning(),
    );
    setState(
        "setupCorrelation",
        setup.correlationId ? `Setup reference ${setup.correlationId}` : "",
    );
    setState("setupCorrelationHidden", !setup.correlationId);
}

export async function retrySetup(): Promise<void> {
    const workspace = state.workspace;
    if (!workspace) return;
    await api(`/api/v1/workspaces/${workspace.workspaceId}/setup/retry`, { method: "POST" });
    await refreshTasks();
}

export async function runTask(task: WorkspaceTask): Promise<void> {
    const workspace = state.workspace;
    if (!workspace) return;
    const { body: run } = await api<TaskRun>(
        `/api/v1/workspaces/${workspace.workspaceId}/tasks/${encodeURIComponent(task.id)}/run`,
        { method: "POST" },
    );
    recordTaskRun(run);
    renderTaskRun(run);
    void refreshPortStatuses().catch(() => undefined);
    await pollTaskRun(workspace.workspaceId, run);
}

function renderTaskRun(run: TaskRun | null): void {
    if (!run) {
        runtime.taskRunId = null;
        setState("taskStopHidden", true);
        setState("taskStopDisabled", false);
        setState("taskCorrelationHidden", true);
        setState("taskOutputHidden", true);
        setState("taskOutput", "");
        return;
    }
    runtime.taskRunId = run.id;
    setState("taskStopHidden", run.status !== "RUNNING");
    setState("taskStopDisabled", false);
    setState("taskCorrelationHidden", false);
    setState("taskCorrelation", `Task reference ${run.id}`);
    setState("taskOutputHidden", false);
    setState(
        "taskOutput",
        `${run.label}, ${run.status}${run.exitCode === null ? "" : `, exit ${run.exitCode}`}\n${run.output}`,
    );
    const output = byId<HTMLPreElement>("task-output");
    output.scrollTop = output.scrollHeight;
}

const TASK_HISTORY_KEY = "jahaziel:task-history";
const runStartTimes = new Map<string, number>();
let historyLoaded = false;

function loadTaskHistory(): TaskRun[] {
    try {
        const raw = localStorage.getItem(TASK_HISTORY_KEY);
        if (!raw) return [];
        const parsed: unknown = JSON.parse(raw);
        if (!Array.isArray(parsed)) return [];
        return parsed
            .filter(
                (item): item is TaskRun =>
                    typeof item === "object" &&
                    item !== null &&
                    typeof (item as TaskRun).id === "string" &&
                    typeof (item as TaskRun).label === "string",
            )
            .slice(0, 10);
    } catch {
        return [];
    }
}

function persistTaskHistory(): void {
    try {
        localStorage.setItem(TASK_HISTORY_KEY, JSON.stringify(state.runHistory));
    } catch {
        /* Browser storage can be disabled. */
    }
}

function ensureHistoryLoaded(): void {
    if (historyLoaded) return;
    historyLoaded = true;
    const saved = loadTaskHistory();
    if (saved.length) setState("runHistory", saved);
}

function recordTaskRun(run: TaskRun | null): void {
    if (!run) return;
    const existingIndex = state.runHistory.findIndex((item) => item.id === run.id);
    const existing = existingIndex >= 0 ? state.runHistory[existingIndex] : null;
    if (existing?.durationMs !== undefined && run.durationMs === undefined)
        run.durationMs = existing.durationMs;
    if (!runStartTimes.has(run.id)) runStartTimes.set(run.id, Date.now());
    if (run.status !== "RUNNING" && run.status !== "IDLE" && run.durationMs === undefined) {
        const started = runStartTimes.get(run.id);
        if (started !== undefined) run.durationMs = Math.max(0, Date.now() - started);
    }
    if (existingIndex >= 0) setState("runHistory", existingIndex, run);
    else setState("runHistory", (items) => [run, ...items].slice(0, 10));
    persistTaskHistory();
}

export function showRun(run: TaskRun): void {
    renderTaskRun(run);
}

async function pollTaskRun(workspaceId: string, initialRun: TaskRun): Promise<void> {
    if (runtime.taskPollingId === initialRun.id) return;
    runtime.taskPollingId = initialRun.id;
    let current = initialRun;
    try {
        while (
            state.workspace?.workspaceId === workspaceId &&
            runtime.taskRunId === initialRun.id &&
            current.status === "RUNNING"
        ) {
            await new Promise((resolve) => window.setTimeout(resolve, 1000));
            if (state.workspace?.workspaceId !== workspaceId || runtime.taskRunId !== initialRun.id)
                break;
            const { body } = await api<TaskRun>(
                `/api/v1/workspaces/${workspaceId}/task-runs/${initialRun.id}`,
            );
            current = body;
            if (
                state.workspace?.workspaceId === workspaceId &&
                runtime.taskRunId === initialRun.id
            ) {
                recordTaskRun(current);
                renderTaskRun(current);
            }
            await refreshDebugTargets().catch(() => undefined);
            await refreshPortStatuses().catch(() => undefined);
        }
    } catch (failure) {
        if (state.workspace?.workspaceId === workspaceId && runtime.taskRunId === initialRun.id) {
            toast(`Task status could not be refreshed: ${errorText(failure)}`, "error");
        }
    } finally {
        if (runtime.taskPollingId === initialRun.id) runtime.taskPollingId = null;
    }
}

export async function stopTask(): Promise<void> {
    const workspace = state.workspace;
    const runId = runtime.taskRunId;
    if (!workspace || !runId) return;
    setState("taskStopDisabled", true);
    try {
        const { body: run } = await api<TaskRun>(
            `/api/v1/workspaces/${workspace.workspaceId}/task-runs/${runId}/stop`,
            { method: "POST" },
        );
        renderTaskRun(run);
    } finally {
        setState("taskStopDisabled", false);
        await refreshPortStatuses().catch(() => undefined);
    }
}
