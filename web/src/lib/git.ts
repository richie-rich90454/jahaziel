///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { api, errorText } from "./api";
import { setState, state, toast, type GitDiff, type GitStatus } from "./store";
import { runtime } from "./runtime";
import {
    checkoutGitBranch,
    loadGitBlame,
    loadGitBranches,
    loadGitHistory,
    stageGitHunk,
    type DiffHunk,
} from "./git-extras";

export async function refreshGit(): Promise<void> {
    const workspace = state.workspace;
    if (!workspace) return;
    runtime.gitHasStaged = false;
    setState("gitCommitDisabled", true);
    const { body } = await api<import("./store").GitStatus>(
        `/api/v1/workspaces/${workspace.workspaceId}/git`,
    );
    if (state.workspace?.workspaceId !== workspace.workspaceId) return;
    setState("gitChanges", []);
    setState("gitDiffHidden", true);
    setState("gitFormHidden", !body.repository);
    setState("gitState", body.repository ? `${body.branch} · ${body.changes.length}` : "No repo");
    const staged = body.changes.some(
        (change) => change.indexStatus !== " " && change.indexStatus !== "?",
    );
    runtime.gitHasStaged = body.repository && staged;
    setState("gitCommitDisabled", !body.repository || !staged || !state.gitMessage.trim());
    if (!body.repository) {
        setState("gitChanges", [
            {
                path: "No Git repository at the workspace root. Use the terminal to initialize or open one.",
                indexStatus: "",
                worktreeStatus: "",
            },
        ]);
        return;
    }
    if (body.changes.length === 0) {
        setState("gitChanges", [
            { path: "Working tree clean.", indexStatus: "", worktreeStatus: "" },
        ]);
        return;
    }
    setState("gitChanges", body.changes);
}

function refreshGitSafely(): void {
    refreshGit().catch((failure) => toast(errorText(failure), "error"));
}

export { refreshGitSafely };

export function resetGitPanel(): void {
    runtime.gitHasStaged = false;
    setState("gitCommitDisabled", true);
    setState("gitState", "Git");
    setState("gitChanges", []);
    setState("gitDiffHidden", true);
    setState("gitMessage", "");
    setState("gitHistory", null);
    setState("gitHistoryLoaded", false);
    setState("gitBranches", null);
    setState("gitBranchesLoaded", false);
    setState("gitBlame", null);
    setState("gitBlamePath", "");
    setState("gitHunkSupported", null);
}

export async function loadGitDiff(path: string): Promise<void> {
    const workspace = state.workspace;
    if (!workspace) return;
    const query = new URLSearchParams({ path });
    const { body } = await api<import("./store").GitDiff>(
        `/api/v1/workspaces/${workspace.workspaceId}/git/diff?${query}`,
    );
    if (state.workspace?.workspaceId !== workspace.workspaceId) return;
    setState("gitDiff", body.content || "No diff to display.");
    setState("gitDiffHidden", false);
}

export async function updateGitPath(operation: "stage" | "unstage", path: string): Promise<void> {
    const workspace = state.workspace;
    if (!workspace) return;
    await api(`/api/v1/workspaces/${workspace.workspaceId}/git/${operation}`, {
        method: "POST",
        body: JSON.stringify({ path }),
    });
    refreshGitSafely();
}

export async function commitGitChanges(event: SubmitEvent): Promise<void> {
    event.preventDefault();
    const workspace = state.workspace;
    const message = state.gitMessage.trim();
    if (!workspace || !message) return;
    const { body } = await api<{ commit: string; summary: string }>(
        `/api/v1/workspaces/${workspace.workspaceId}/git/commit`,
        { method: "POST", body: JSON.stringify({ message }) },
    );
    setState("gitMessage", "");
    toast(`Committed ${body.commit}.`);
    refreshGitSafely();
}

export function updateGitMessage(value: string): void {
    setState("gitMessage", value);
    setState("gitCommitDisabled", !runtime.gitHasStaged || !value.trim());
}

/** Best-effort load of history and branches; null means the host has no such endpoint. */
export async function loadGitExtras(): Promise<void> {
    const [history, branches] = await Promise.all([
        loadGitHistory().catch(() => null),
        loadGitBranches().catch(() => null),
    ]);
    setState("gitHistory", history);
    setState("gitHistoryLoaded", true);
    setState("gitBranches", branches);
    setState("gitBranchesLoaded", true);
}

export async function checkoutBranchSafe(name: string): Promise<void> {
    const changed = await checkoutGitBranch(name);
    if (!changed) {
        toast("Branch checkout is unavailable on this host.", "error");
        return;
    }
    refreshGitSafely();
    void loadGitExtras().catch((failure) => toast(errorText(failure), "error"));
}

export async function loadGitBlameFor(path: string): Promise<void> {
    setState("gitBlamePath", path);
    setState("gitBlame", null);
    const blame = await loadGitBlame(path).catch(() => null);
    if (state.gitBlamePath !== path) return;
    setState("gitBlame", blame);
}

export function setGitSideBySide(value: boolean): void {
    setState("gitSideBySide", value);
}

export async function stageHunk(path: string, hunk: DiffHunk): Promise<void> {
    const accepted = await stageGitHunk(path, hunk);
    setState("gitHunkSupported", accepted);
    if (!accepted) {
        toast("Per-hunk staging is unavailable on this host; use whole-file staging.", "error");
        return;
    }
    refreshGitSafely();
    await loadGitDiff(path);
}
