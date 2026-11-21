///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { api, errorText } from "./api";
import { byId } from "./dom";
import { setPanel, setState, state, toast, type TerminalSession } from "./store";
import { runtime, type StreamMessage } from "./runtime";

const RECONNECT_BASE_MS = 500;
const RECONNECT_MAX_MS = 8000;

function isFinalTerminalClose(code: number): boolean {
    return code === 1000 || code === 1008 || (code >= 4400 && code < 4500);
}

function isPolicyTerminalClose(code: number): boolean {
    return code === 1008 || (code >= 4400 && code < 4500);
}

function clearReconnectTimer(): void {
    if (runtime.terminalReconnectTimer !== null) {
        window.clearTimeout(runtime.terminalReconnectTimer);
        runtime.terminalReconnectTimer = null;
    }
}

function scheduleReconnect(session: TerminalSession, workspaceId: string): void {
    clearReconnectTimer();
    const attempt = runtime.terminalReconnectAttempts;
    runtime.terminalReconnectAttempts = attempt + 1;
    runtime.terminalReconnectTimer = window.setTimeout(
        () => {
            runtime.terminalReconnectTimer = null;
            if (
                state.workspace?.workspaceId !== workspaceId ||
                runtime.terminalSessionId ||
                runtime.socket?.readyState === WebSocket.OPEN
            )
                return;
            void closeTerminal(false);
            void connectTerminal(session, workspaceId).catch((failure) =>
                toast(errorText(failure), "error"),
            );
        },
        Math.min(RECONNECT_BASE_MS * 2 ** attempt, RECONNECT_MAX_MS),
    );
}

export async function refreshTerminalSessions(preserveState = false): Promise<void> {
    const workspace = state.workspace;
    if (!workspace) return;
    const { body } = await api<TerminalSession[]>(
        `/api/v1/workspaces/${workspace.workspaceId}/terminals`,
    );
    if (state.workspace?.workspaceId !== workspace.workspaceId) return;
    setState("terminalSessions", body);
    const detached = body.filter((session) => session.sessionId !== runtime.terminalSessionId);
    setState("terminalSelectHidden", detached.length === 0);
    setState("terminalSelectDisabled", detached.length === 0);
    setState("terminalReconnectHidden", detached.length === 0);
    setState("terminalReconnectDisabled", detached.length === 0);
    if (!runtime.terminalSessionId && !preserveState) {
        setState(
            "terminalState",
            detached.length === 0
                ? "No session"
                : `${detached.length} detached session${detached.length === 1 ? "" : "s"}`,
        );
    }
}

async function autoReconnectTerminal(): Promise<void> {
    const workspace = state.workspace;
    if (!workspace || runtime.terminalSessionId) return;
    const detached = state.terminalSessions.filter(
        (session) => session.sessionId !== runtime.terminalSessionId,
    );
    if (detached.length === 0) return;
    await closeTerminal(false);
    await connectTerminal(detached[0], workspace.workspaceId);
}

function setTerminalModifier(modifier: "ctrl" | "alt", enabled: boolean): void {
    setState("terminalModifiers", modifier, enabled);
}

export function toggleTerminalModifier(modifier: "ctrl" | "alt"): void {
    setTerminalModifier(modifier, !state.terminalModifiers[modifier]);
    runtime.terminal?.focus();
}

function applyTerminalModifiers(data: string): string {
    let output = data;
    if (state.terminalModifiers.ctrl) {
        const chars = Array.from(output);
        const first = chars.shift();
        if (first) {
            const code = first.toUpperCase().codePointAt(0) ?? 0;
            if (code >= 64 && code <= 95) output = String.fromCodePoint(code - 64) + chars.join("");
            else if (first === "?") output = "\u007f" + chars.join("");
        }
        setTerminalModifier("ctrl", false);
    }
    if (state.terminalModifiers.alt) {
        output = `\u001b${output}`;
        setTerminalModifier("alt", false);
    }
    return output;
}

function sendTerminalInput(data: string): void {
    const socket = runtime.socket;
    if (!socket || socket.readyState !== WebSocket.OPEN) {
        toast("Open or reconnect a terminal session first.", "error");
        return;
    }
    setTerminalModifier("ctrl", false);
    setTerminalModifier("alt", false);
    socket.send(JSON.stringify({ type: "input", data } satisfies StreamMessage));
    runtime.terminal?.focus();
}

export function terminalInput(data: string): void {
    sendTerminalInput(data);
}

export function terminalShortcut(key: string): void {
    sendTerminalInput(String.fromCharCode((key || "c").charCodeAt(0) & 0x1f));
}

export async function copyTerminalSelection(): Promise<void> {
    const selection = runtime.terminal?.getSelection() ?? "";
    if (!selection) {
        toast("Select terminal text first.", "error");
        return;
    }
    try {
        await navigator.clipboard.writeText(selection);
        toast("Terminal selection copied.");
    } catch {
        toast(
            "The browser did not allow clipboard access. Select and copy the text manually.",
            "error",
        );
    }
}

function closeTerminal(closeServerSession: boolean, refreshSessions = false): Promise<void> {
    clearReconnectTimer();
    runtime.terminalObserver?.disconnect();
    runtime.terminalObserver = null;
    runtime.socket?.close();
    runtime.socket = null;
    runtime.terminal?.dispose();
    runtime.terminal = null;
    runtime.fitAddon = null;
    setState("terminalCloseHidden", true);
    setState("terminalSelectHidden", true);
    setState("terminalReconnectHidden", true);
    setState("keyStripHidden", true);
    setTerminalModifier("ctrl", false);
    setTerminalModifier("alt", false);
    const sessionId = runtime.terminalSessionId;
    runtime.terminalSessionId = null;
    byId("terminal-surface").replaceChildren();
    if (closeServerSession && sessionId) {
        return api(`/api/v1/terminals/${sessionId}`, { method: "DELETE" }).then(() =>
            refreshSessions ? refreshTerminalSessions() : undefined,
        );
    }
    return Promise.resolve();
}

export function closeActiveTerminal(): void {
    closeTerminal(true, true).catch((failure) => toast(errorText(failure), "error"));
}

export async function openTerminal(): Promise<void> {
    const workspace = state.workspace;
    if (!workspace) return;
    await closeTerminal(true);
    const { body: session } = await api<TerminalSession>(
        `/api/v1/workspaces/${workspace.workspaceId}/terminals?rows=28&cols=100`,
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

export async function reconnectTerminal(): Promise<void> {
    const workspace = state.workspace;
    const sessionId = byId<HTMLSelectElement>("terminal-session-select").value;
    const session = state.terminalSessions.find((candidate) => candidate.sessionId === sessionId);
    if (!workspace || !session) return;
    await closeTerminal(false);
    await connectTerminal(session, workspace.workspaceId);
}

async function connectTerminal(session: TerminalSession, workspaceId: string): Promise<void> {
    if (state.workspace?.workspaceId !== workspaceId || session.workspaceId !== workspaceId) return;
    runtime.terminalSessionId = session.sessionId;
    let Terminal: typeof import("@xterm/xterm").Terminal;
    let FitAddon: typeof import("@xterm/addon-fit").FitAddon;
    try {
        [{ Terminal }, { FitAddon }] = await Promise.all([
            import("@xterm/xterm"),
            import("@xterm/addon-fit"),
        ]);
    } catch (failure) {
        await closeTerminal(true).catch(() => undefined);
        throw failure;
    }
    if (
        state.workspace?.workspaceId !== workspaceId ||
        runtime.terminalSessionId !== session.sessionId
    )
        return;
    const terminal = new Terminal({
        rows: session.rows,
        cols: session.cols,
        cursorBlink: true,
        fontFamily: '"IBM Plex Mono", "Cascadia Code", monospace',
        fontSize: 13,
        theme: {
            background: "#ffffff",
            foreground: "#28332b",
            cursor: "#2f6f52",
            selectionBackground: "#d8e7db",
        },
        convertEol: false,
    });
    const fit = new FitAddon();
    terminal.loadAddon(fit);
    terminal.open(byId("terminal-surface"));
    runtime.terminal = terminal;
    runtime.fitAddon = fit;
    setPanel("terminal");
    setState("keyStripHidden", false);
    setTerminalModifier("ctrl", false);
    setTerminalModifier("alt", false);
    setState("terminalCloseHidden", false);
    setState("terminalState", "Connecting…");
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const socket = new WebSocket(
        `${protocol}//${window.location.host}/api/v1/terminals/${session.sessionId}/stream`,
    );
    runtime.socket = socket;
    let exited = false;
    socket.addEventListener("open", () => {
        if (runtime.socket !== socket || runtime.terminalSessionId !== session.sessionId) {
            socket.close();
            return;
        }
        clearReconnectTimer();
        runtime.terminalReconnectAttempts = 0;
        terminal.focus();
        setState("terminalState", "Restoring…");
    });
    socket.addEventListener("message", (event) => {
        try {
            const message = JSON.parse(String(event.data)) as StreamMessage;
            if (message.type === "history") {
                const restore = (): void => {
                    if (
                        runtime.socket !== socket ||
                        runtime.terminalSessionId !== session.sessionId
                    )
                        return;
                    fit.fit();
                    if (socket.readyState === WebSocket.OPEN) {
                        socket.send(
                            JSON.stringify({
                                type: "resize",
                                rows: terminal.rows,
                                cols: terminal.cols,
                            } satisfies StreamMessage),
                        );
                    }
                    terminal.scrollToBottom();
                    terminal.focus();
                    if (!exited) setState("terminalState", "Connected");
                };
                if (message.data) terminal.write(message.data, restore);
                else restore();
            }
            if (message.type === "output" && message.data) terminal.write(message.data);
            if (message.type === "exit") {
                exited = true;
                setState("terminalState", `Exited (${message.exitCode ?? "unknown"})`);
            }
        } catch {
            terminal.write("\r\n[terminal] invalid server message\r\n");
        }
    });
    socket.addEventListener("close", (event) => {
        if (runtime.socket !== socket || runtime.terminalSessionId !== session.sessionId) return;
        if (state.workspace?.workspaceId !== workspaceId) return;
        const final = isFinalTerminalClose(event.code);
        const reason = final ? `Disconnected (${event.code})` : "Disconnected — reconnecting…";
        void closeTerminal(false);
        void refreshTerminalSessions(true)
            .catch(() => undefined)
            .then(() => {
                if (state.workspace?.workspaceId !== workspaceId || runtime.terminalSessionId)
                    return;
                setState("terminalState", reason);
                if (final) {
                    setState("terminalReconnectHidden", false);
                    setState("terminalReconnectDisabled", false);
                    toast(
                        isPolicyTerminalClose(event.code)
                            ? "Terminal connection refused by policy. Reconnect after the issue is resolved."
                            : "Terminal session closed. Use Reconnect to resume it.",
                        "error",
                    );
                } else scheduleReconnect(session, workspaceId);
            });
    });
    socket.addEventListener("error", () => {
        if (runtime.socket === socket) setState("terminalState", "Connection error");
    });
    terminal.onData((data) => {
        const input = applyTerminalModifiers(data);
        if (socket.readyState === WebSocket.OPEN)
            socket.send(JSON.stringify({ type: "input", data: input } satisfies StreamMessage));
    });
    terminal.onResize(({ rows, cols }) => {
        if (socket.readyState === WebSocket.OPEN)
            socket.send(JSON.stringify({ type: "resize", rows, cols } satisfies StreamMessage));
    });
    const observer = new ResizeObserver(() => {
        try {
            fit.fit();
        } catch {
            /* Collapsed terminal panel. */
        }
    });
    observer.observe(byId("terminal-surface"));
    runtime.terminalObserver = observer;
    await refreshTerminalSessions();
}

export function focusTerminalPanel(): void {
    window.requestAnimationFrame(() => {
        try {
            runtime.fitAddon?.fit();
        } catch {
            /* Panel has no measurable size yet. */
        }
    });
}

export { autoReconnectTerminal, closeTerminal, connectTerminal };
