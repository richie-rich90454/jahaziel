///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { api, errorText } from "./api";
import { setState, state, toast, type AgentToolStatus, type TerminalSession } from "./store";
import { runtime } from "./runtime";
import { connectTerminal } from "./terminal";

export async function refreshAgentToolStatus(installWhenMissing: boolean): Promise<void> {
    if (runtime.agentToolPolling) return;
    runtime.agentToolPolling = true;
    try {
        let status = await readAgentToolStatus();
        renderAgentToolStatus(status);
        if (installWhenMissing && status?.status === "NOT_INSTALLED") {
            const { body } = await api<AgentToolStatus>("/api/v1/me/tools/opencode/install", {
                method: "POST",
            });
            status = body;
            renderAgentToolStatus(status);
        }
        while (status?.status === "INSTALLING") {
            await new Promise((resolve) => window.setTimeout(resolve, 1800));
            status = await readAgentToolStatus();
            renderAgentToolStatus(status);
        }
    } catch (failure) {
        setState("opencodeStatus", `OpenCode, ${errorText(failure)}`);
    } finally {
        runtime.agentToolPolling = false;
    }
}

async function readAgentToolStatus(): Promise<AgentToolStatus | null> {
    const { body } = await api<AgentToolStatus[]>("/api/v1/me/tools");
    return body.find((tool) => tool.id === "opencode") ?? null;
}

function renderAgentToolStatus(status: AgentToolStatus | null): void {
    if (!status) {
        setState("opencodeStatus", "OpenCode, unavailable");
        setState("opencodeLaunchLabel", "Open OpenCode");
        setState("opencodeLaunchDisabled", true);
        setState("opencodeUpdateHidden", true);
        setState("opencodeRemoveHidden", true);
        return;
    }
    const installed = status.status === "INSTALLED";
    const installing = status.status === "INSTALLING";
    let label: string;
    if (installing) label = `OpenCode, installing ${status.progressPercent}%`;
    else if (installed) label = `OpenCode ${status.version}, ready`;
    else if (status.status === "FAILED") label = `OpenCode, install failed: ${status.error}`;
    else if (status.status === "UNSUPPORTED")
        label = `OpenCode, unsupported ${status.architecture}`;
    else label = `OpenCode, ${Math.ceil(status.downloadSizeBytes / 1_000_000)} MB install`;
    setState("opencodeStatus", label);
    setState(
        "opencodeLaunchLabel",
        installed ? "Open OpenCode" : status.status === "FAILED" ? "Retry install" : "Installing",
    );
    setState("opencodeLaunchDisabled", installing || status.status === "UNSUPPORTED");
    setState("opencodeUpdateHidden", !installed);
    setState("opencodeUpdateDisabled", installing);
    setState("opencodeRemoveHidden", !installed);
    setState("opencodeRemoveDisabled", installing);
}

export async function launchOpenCode(): Promise<void> {
    const workspace = state.workspace;
    if (!workspace) return;
    const { body } = await api<AgentToolStatus[]>("/api/v1/me/tools");
    const toolStatus = body.find((tool) => tool.id === "opencode") ?? null;
    renderAgentToolStatus(toolStatus);
    if (toolStatus?.status !== "INSTALLED") {
        if (toolStatus?.status !== "INSTALLING") {
            if (toolStatus?.status === "NOT_INSTALLED" || toolStatus?.status === "FAILED") {
                const installed = await api<AgentToolStatus>("/api/v1/me/tools/opencode/install", {
                    method: "POST",
                });
                renderAgentToolStatus(installed.body);
                void refreshAgentToolStatus(false);
            }
        }
        toast(
            "OpenCode is not ready yet. Its install status is shown in the terminal panel.",
            "error",
        );
        return;
    }
    const { body: session } = await api<TerminalSession>(
        `/api/v1/workspaces/${workspace.workspaceId}/terminals/opencode?rows=28&cols=100`,
        { method: "POST" },
    );
    if (state.workspace?.workspaceId !== workspace.workspaceId) {
        await api(`/api/v1/terminals/${session.sessionId}`, { method: "DELETE" }).catch(
            () => undefined,
        );
        return;
    }
    await connectTerminal(session, workspace.workspaceId);
}

export async function updateOpenCode(): Promise<void> {
    const { body } = await api<AgentToolStatus>("/api/v1/me/tools/opencode/update", {
        method: "POST",
    });
    renderAgentToolStatus(body);
    void refreshAgentToolStatus(false);
}

export async function removeOpenCode(): Promise<void> {
    if (
        !window.confirm(
            "Remove the per-user OpenCode executable? Its profile and provider sign-in will be kept.",
        )
    )
        return;
    const { body } = await api<AgentToolStatus>("/api/v1/me/tools/opencode", { method: "DELETE" });
    renderAgentToolStatus(body);
}
