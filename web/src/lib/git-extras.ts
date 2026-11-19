///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { api } from "./api";
import { state } from "./store";

export interface GitCommit {
    id: string;
    subject: string;
    author: string;
    date: string;
}

export interface GitBranch {
    name: string;
    current: boolean;
}

export interface GitBlameLine {
    line: number;
    commit: string;
    author: string;
    text: string;
}

export interface DiffLine {
    kind: "context" | "add" | "remove";
    text: string;
    oldLine: number | null;
    newLine: number | null;
}

export interface DiffHunk {
    header: string;
    oldStart: number;
    oldCount: number;
    newStart: number;
    newCount: number;
    lines: DiffLine[];
}

export interface ParsedDiff {
    file: string;
    hunks: DiffHunk[];
}

function endpoint(path: string): string {
    const id = state.workspace?.workspaceId;
    if (!id) throw new Error("Open a workspace first.");
    return `/api/v1/workspaces/${id}/git/${path}`;
}

function isMissing(failure: unknown): boolean {
    return failure instanceof Error && /^(404|405)\b/.test(failure.message);
}

export async function loadGitHistory(limit = 20): Promise<GitCommit[] | null> {
    try {
        const { body } = await api<GitCommit[]>(
            `${endpoint("history")}?${new URLSearchParams({ limit: String(limit) })}`,
        );
        return Array.isArray(body) ? body : null;
    } catch (failure) {
        return isMissing(failure) ? null : Promise.reject(failure);
    }
}

export async function loadGitBranches(): Promise<GitBranch[] | null> {
    try {
        const { body } = await api<GitBranch[]>(endpoint("branches"));
        return Array.isArray(body) ? body : null;
    } catch (failure) {
        return isMissing(failure) ? null : Promise.reject(failure);
    }
}

export async function checkoutGitBranch(name: string): Promise<boolean> {
    try {
        await api(endpoint("checkout"), {
            method: "POST",
            body: JSON.stringify({ branch: name }),
        });
        return true;
    } catch (failure) {
        if (isMissing(failure)) return false;
        throw failure;
    }
}

export async function loadGitBlame(path: string): Promise<GitBlameLine[] | null> {
    try {
        const query = new URLSearchParams({ path });
        const { body } = await api<GitBlameLine[]>(`${endpoint("blame")}?${query}`);
        return Array.isArray(body) ? body : null;
    } catch (failure) {
        return isMissing(failure) ? null : Promise.reject(failure);
    }
}

export async function stageGitHunk(path: string, hunk: DiffHunk): Promise<boolean> {
    try {
        await api(endpoint("stage"), {
            method: "POST",
            body: JSON.stringify({ path, hunk: hunk.header }),
        });
        return true;
    } catch (failure) {
        if (isMissing(failure)) return false;
        throw failure;
    }
}

/** Parses a unified diff into per-file hunks for the side-by-side view. */
export function parseUnifiedDiff(content: string): ParsedDiff[] {
    const files: ParsedDiff[] = [];
    let currentFile: ParsedDiff | null = null;
    let currentHunk: DiffHunk | null = null;
    let oldLine = 0;
    let newLine = 0;

    for (const line of content.replaceAll("\r\n", "\n").split("\n")) {
        if (line.startsWith("diff --git ")) {
            const names = /diff --git a\/(.+?) b\/(.+)$/.exec(line);
            currentFile = { file: names?.[2] ?? names?.[1] ?? "file", hunks: [] };
            files.push(currentFile);
            currentHunk = null;
            continue;
        }
        const hunkHeader = /^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@(.*)$/.exec(line);
        if (hunkHeader && currentFile) {
            oldLine = Number(hunkHeader[1]);
            newLine = Number(hunkHeader[3]);
            currentHunk = {
                header: line,
                oldStart: oldLine,
                oldCount: Number(hunkHeader[2] ?? 1),
                newStart: newLine,
                newCount: Number(hunkHeader[4] ?? 1),
                lines: [],
            };
            currentFile.hunks.push(currentHunk);
            continue;
        }
        if (!currentHunk || !line) continue;
        if (line.startsWith("+++") || line.startsWith("---") || line.startsWith("index ")) continue;
        if (line.startsWith("+")) {
            currentHunk.lines.push({
                kind: "add",
                text: line.slice(1),
                oldLine: null,
                newLine: newLine++,
            });
        } else if (line.startsWith("-")) {
            currentHunk.lines.push({
                kind: "remove",
                text: line.slice(1),
                oldLine: oldLine++,
                newLine: null,
            });
        } else if (line.startsWith(" ") || line.startsWith("\\")) {
            if (line.startsWith("\\")) continue;
            currentHunk.lines.push({
                kind: "context",
                text: line.slice(1),
                oldLine: oldLine++,
                newLine: newLine++,
            });
        }
    }
    return files;
}
