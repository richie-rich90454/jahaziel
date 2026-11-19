///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { api, errorText, isUnavailable } from "./api";
import { openWorkspace, renderWorkspaces } from "./identity";
import { setState, state, toast, type Workspace } from "./store";

export async function cloneWorkspace(
    source: Workspace,
    name: string,
    paths: string[] = [],
): Promise<Workspace | null> {
    try {
        const { body } = await api<{ id: string; name: string; copiedFiles: number }>(
            `/api/v1/workspaces/${source.workspaceId}/clone`,
            {
                method: "POST",
                body: JSON.stringify({ name, files: paths }),
            },
        );
        const { body: created } = await api<Workspace>(
            `/api/v1/workspaces/${encodeURIComponent(body.id)}`,
        );
        setState("cloneUnavailable", false);
        setState("cloneState", `Cloned ${body.copiedFiles} files`);
        toast(`${body.name || name} is ready to edit.`);
        await renderWorkspaces();
        await openWorkspace(created);
        return created;
    } catch (failure) {
        if (isUnavailable(failure)) {
            setState("cloneUnavailable", true);
            setState("cloneState", "Unavailable on this host");
            toast("Unavailable on this host", "error");
            return null;
        }
        setState("cloneState", errorText(failure));
        toast(errorText(failure), "error");
        return null;
    }
}

/** Prompts for a clone name and an optional comma-separated file subset. */
export async function promptClone(
    source: Workspace = state.workspace ?? sourceOrThrow(),
): Promise<void> {
    const name = window.prompt("Name for the cloned workspace", `${source.name}-copy`);
    if (!name?.trim()) return;
    const subset = window.prompt(
        "Files to include, comma separated (leave blank for the whole workspace)",
        "",
    );
    const paths = (subset ?? "")
        .split(",")
        .map((path) => path.trim())
        .filter(Boolean);
    await cloneWorkspace(source, name.trim(), paths);
}

function sourceOrThrow(): Workspace {
    throw new Error("Open a workspace first.");
}
