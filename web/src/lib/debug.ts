///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { parseDebugLaunchConfigurations } from "../debug-launch-config";
import { api, errorText } from "./api";
import { byId } from "./dom";
import {
    setPanel,
    setState,
    state,
    toast,
    type DapFrame,
    type DapVariable,
    type DebugSession,
    type DocumentBody,
} from "./store";
import { runtime, type BreakpointEntry, type DapMessage } from "./runtime";
import { openDocument } from "./editor";

function setDebugState(message: string): void {
    setState("debugState", message.replace(/ · /g, " ").toUpperCase());
}

export function setDebugLanguage(value: string): void {
    setState("debugLanguage", value);
    configureDebugLanguageFields();
}

export function setDebugProgram(value: string): void {
    setState("debugProgram", value);
}

export function setDebugMainClass(value: string): void {
    setState("debugMainClass", value);
}

export function setDebugWorkingDirectory(value: string): void {
    setState("debugWorkingDirectory", value);
}

export function setDebugArguments(value: string): void {
    setState("debugArguments", value);
}

export function setDebugStopOnEntry(value: boolean): void {
    setState("debugStopOnEntry", value);
}

export function setDebugAttachTarget(value: string): void {
    setState("debugAttachTarget", value);
    updateDebugControls();
}

export function setDebugBreakpointPath(value: string): void {
    setState("debugBreakpointPath", value);
}

export function setDebugBreakpointLines(value: string): void {
    setState("debugBreakpointLines", value);
}

export function setDebugWatchExpression(_value: string): void {
    /* expression is read from the form on submit */
}

function configureDebugLanguageFields(): void {
    const isJava = state.debugLanguage === "java";
    setState("debugProgramFieldHidden", isJava);
    setState("debugMainClassFieldHidden", !isJava);
}

export async function loadDebugLaunchConfigurations(): Promise<void> {
    const workspace = state.workspace;
    if (!workspace) return;
    clearDebugLaunchConfigurations();
    const query = new URLSearchParams({ path: ".vscode/launch.json" });
    const { body } = await api<DocumentBody>(
        `/api/v1/workspaces/${workspace.workspaceId}/files/document?${query}`,
    );
    if (state.workspace?.workspaceId !== workspace.workspaceId) return;
    const configurations = parseDebugLaunchConfigurations(body.content);
    setState("debugConfigs", configurations);
    setState("debugConfigFieldHidden", false);
    setState("debugConfigSelectHidden", false);
    setState(
        "debugConfigStatus",
        `${configurations.length} supported launch configuration${configurations.length === 1 ? "" : "s"} loaded.`,
    );
    setState("debugConfigName", configurations[0].name);
    applyDebugLaunchConfiguration(configurations[0].name);
    updateDebugControls();
}

export function chooseDebugLaunchConfiguration(name: string): void {
    setState("debugConfigName", name);
    applyDebugLaunchConfiguration(name);
}

function applyDebugLaunchConfiguration(name: string): void {
    const configuration = state.debugConfigs.find((item) => item.name === name);
    if (!configuration) return;
    setState("debugLanguage", configuration.languageId);
    if (configuration.request === "attach") {
        const matchingTarget = state.debugTargets.find(
            (target) => target.languageId === configuration.languageId,
        );
        setState("debugAttachTarget", matchingTarget?.id ?? "");
        setState(
            "debugConfigStatus",
            matchingTarget
                ? `${configuration.name} selected. Attach to the matching running task.`
                : `${configuration.name} selected. Start a debuggable ${configuration.languageId} task first.`,
        );
        configureDebugLanguageFields();
        updateDebugControls();
        return;
    }
    setState("debugProgram", configuration.program ?? "");
    setState("debugMainClass", configuration.mainClass ?? "");
    setState("debugWorkingDirectory", configuration.workingDirectory);
    setState("debugArguments", JSON.stringify(configuration.arguments));
    setState("debugStopOnEntry", configuration.stopOnEntry);
    setState("debugConfigStatus", `${configuration.name} launch settings loaded.`);
    configureDebugLanguageFields();
    updateDebugControls();
}

function clearDebugLaunchConfigurations(): void {
    setState("debugConfigs", []);
    setState("debugConfigName", "");
    setState("debugConfigSelectHidden", true);
    setState("debugConfigFieldHidden", true);
    setState("debugConfigStatus", "");
}

function clearDebugInspectors(): void {
    runtime.debugThreadId = null;
    runtime.debugFrameId = null;
    runtime.debugPaused = false;
    runtime.debugFrames = [];
    runtime.debugVariables = [];
    runtime.debugWatchValues.clear();
    setState("debugFrames", []);
    setState("debugVariables", []);
    setState("debugWatchValues", {});
    updateDebugControls();
}

function appendDebugConsole(value: string): void {
    const current = state.debugConsole;
    const base = current === "Debugger output will appear here." ? "" : current;
    setState("debugConsole", `${base}${value}`.slice(-100_000));
    const output = byId<HTMLPreElement>("debug-console");
    if (output) output.scrollTop = output.scrollHeight;
}

function rejectDebugRequests(message: string): void {
    for (const [sequence, pending] of runtime.dapPending) {
        window.clearTimeout(pending.timer);
        pending.reject(new Error(message));
        runtime.dapPending.delete(sequence);
    }
    for (const waiters of runtime.dapEventWaiters.values()) {
        for (const waiter of waiters) {
            window.clearTimeout(waiter.timer);
            waiter.reject(new Error(message));
        }
    }
    runtime.dapEventWaiters.clear();
}

function openDebugSocket(session: DebugSession): Promise<void> {
    const workspaceId = state.workspace?.workspaceId;
    if (!workspaceId || workspaceId !== session.workspaceId) {
        return Promise.reject(new Error("The debug session belongs to a different workspace."));
    }
    if (runtime.debugSocket?.readyState === WebSocket.OPEN) return Promise.resolve();
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const socket = new WebSocket(
        `${protocol}//${window.location.host}/api/v1/workspaces/${encodeURIComponent(workspaceId)}/debug-sessions/${encodeURIComponent(session.id)}/stream`,
    );
    runtime.debugSocket = socket;
    setDebugState("Connecting");
    updateDebugControls();
    return new Promise<void>((resolve, reject) => {
        let opened = false;
        socket.addEventListener(
            "open",
            () => {
                opened = true;
                if (runtime.debugSocket !== socket) {
                    socket.close();
                    reject(new Error("The debug session changed while connecting."));
                    return;
                }
                setDebugState("Connected");
                updateDebugControls();
                resolve();
            },
            { once: true },
        );
        socket.addEventListener("message", (event) => handleDapMessage(socket, String(event.data)));
        socket.addEventListener("error", () => {
            if (!opened) reject(new Error("Could not connect to the debug stream."));
        });
        socket.addEventListener("close", () => {
            if (runtime.debugSocket !== socket) return;
            runtime.debugSocket = null;
            rejectDebugRequests("The debug stream disconnected.");
            updateDebugControls();
            if (runtime.debugSession) setDebugState("Disconnected, reconnect available");
            if (!opened) reject(new Error("The debug stream closed before connecting."));
        });
    });
}

function sendDap(message: Record<string, unknown>): void {
    const socket = runtime.debugSocket;
    if (!socket || socket.readyState !== WebSocket.OPEN) {
        throw new Error("Reconnect the debug stream before sending debugger commands.");
    }
    socket.send(JSON.stringify(message));
}

function dapRequest(command: string, args: Record<string, unknown> = {}): Promise<DapMessage> {
    const sequence = ++runtime.debugSequence;
    return new Promise<DapMessage>((resolve, reject) => {
        const timer = window.setTimeout(() => {
            runtime.dapPending.delete(sequence);
            reject(new Error(`The debugger did not answer ${command}.`));
        }, 30_000);
        runtime.dapPending.set(sequence, { resolve, reject, timer });
        try {
            sendDap({ seq: sequence, type: "request", command, arguments: args });
        } catch (failure) {
            window.clearTimeout(timer);
            runtime.dapPending.delete(sequence);
            reject(
                failure instanceof Error ? failure : new Error("Could not send debugger request."),
            );
        }
    });
}

function waitForDapEvent(name: string): Promise<void> {
    if (runtime.dapEarlyEvents.delete(name)) return Promise.resolve();
    return new Promise<void>((resolve, reject) => {
        const timer = window.setTimeout(() => {
            const waiters = runtime.dapEventWaiters.get(name) ?? [];
            runtime.dapEventWaiters.set(
                name,
                waiters.filter((item) => item.timer !== timer),
            );
            reject(new Error(`The debug adapter did not send the ${name} event.`));
        }, 15_000);
        const waiters = runtime.dapEventWaiters.get(name) ?? [];
        waiters.push({ resolve, reject, timer });
        runtime.dapEventWaiters.set(name, waiters);
    });
}

function resolveDapEvent(name: string): void {
    const waiters = runtime.dapEventWaiters.get(name);
    const waiter = waiters?.shift();
    if (!waiter) {
        runtime.dapEarlyEvents.add(name);
        return;
    }
    window.clearTimeout(waiter.timer);
    waiter.resolve();
    if (waiters?.length === 0) runtime.dapEventWaiters.delete(name);
}

function handleDapMessage(socket: WebSocket, payload: string): void {
    if (runtime.debugSocket !== socket) return;
    let message: DapMessage;
    try {
        message = JSON.parse(payload) as DapMessage;
        if (!message || typeof message.type !== "string") throw new Error("invalid DAP message");
    } catch {
        socket.close(1007, "invalid debugger message");
        return;
    }
    if (message.type === "response" && typeof message.request_seq === "number") {
        const pending = runtime.dapPending.get(message.request_seq);
        if (!pending) return;
        runtime.dapPending.delete(message.request_seq);
        window.clearTimeout(pending.timer);
        if (message.success === false)
            pending.reject(
                new Error(message.message || `${message.command || "Debugger request"} failed.`),
            );
        else pending.resolve(message);
        return;
    }
    if (message.type === "event" && message.event) {
        if (message.event === "initialized") resolveDapEvent("initialized");
        if (message.event === "output") {
            const output = message.body?.output;
            if (typeof output === "string") appendDebugConsole(output);
        }
        if (message.event === "stopped") {
            const threadId = message.body?.threadId;
            runtime.debugThreadId = typeof threadId === "number" ? threadId : null;
            runtime.debugPaused = true;
            setDebugState("Paused");
            updateDebugControls();
            void refreshDebugStack().catch((failure) =>
                appendDebugConsole(`\n[debug] ${errorText(failure)}\n`),
            );
        }
        if (message.event === "continued") {
            clearDebugInspectors();
            runtime.debugPaused = false;
            const threadId = message.body?.threadId;
            if (typeof threadId === "number") runtime.debugThreadId = threadId;
            else void refreshDebugThread().catch(() => undefined);
            setDebugState("Running");
            updateDebugControls();
        }
        if (message.event === "exited") {
            const code = message.body?.exitCode;
            appendDebugConsole(
                `\n[debug] program exited${typeof code === "number" ? ` with code ${code}` : ""}\n`,
            );
        }
        if (message.event === "terminated") {
            appendDebugConsole("\n[debug] program terminated\n");
            setDebugState("Finished");
            window.setTimeout(() => {
                void stopDebugSession(false);
            }, 0);
        }
        return;
    }
    if (message.type === "request" && typeof message.seq === "number") {
        try {
            sendDap({
                seq: ++runtime.debugSequence,
                type: "response",
                request_seq: message.seq,
                command: message.command || "",
                success: false,
                message: "This adapter request is not supported by Jahaziel.",
            });
        } catch {
            socket.close(1008, "unsupported adapter request");
        }
    }
}

export async function startDebugSession(attachTargetId?: string): Promise<void> {
    const workspace = state.workspace;
    if (!workspace) return;
    if (runtime.debugSession && !(await stopDebugSession(true))) return;
    const requestMode: "launch" | "attach" = attachTargetId ? "attach" : "launch";
    let requestBody: Record<string, unknown>;
    if (attachTargetId) {
        requestBody = { targetId: attachTargetId };
    } else {
        const languageId = state.debugLanguage;
        let argumentsList: unknown;
        try {
            argumentsList = JSON.parse(state.debugArguments || "[]") as unknown;
        } catch {
            throw new Error('Arguments must be a JSON array of strings, such as ["--inspect"].');
        }
        if (
            !Array.isArray(argumentsList) ||
            argumentsList.some((value) => typeof value !== "string")
        ) {
            throw new Error("Arguments must be a JSON array of strings.");
        }
        requestBody = {
            languageId,
            program: languageId === "java" ? null : state.debugProgram.trim(),
            mainClass: languageId === "java" ? state.debugMainClass.trim() : null,
            arguments: argumentsList,
            workingDirectory: state.debugWorkingDirectory.trim() || ".",
            stopOnEntry: state.debugStopOnEntry,
        };
    }
    const { body: session } = await api<DebugSession>(
        `/api/v1/workspaces/${workspace.workspaceId}/debug-sessions${attachTargetId ? "/attach" : ""}`,
        { method: "POST", body: JSON.stringify(requestBody) },
    );
    if (state.workspace?.workspaceId !== workspace.workspaceId) {
        await api(`/api/v1/workspaces/${workspace.workspaceId}/debug-sessions/${session.id}/stop`, {
            method: "POST",
        }).catch(() => undefined);
        return;
    }
    runtime.debugSession = session;
    if (attachTargetId) {
        setState("debugLanguage", session.languageId);
        configureDebugLanguageFields();
    }
    runtime.debugInitialized = false;
    runtime.debugSequence = 0;
    runtime.dapEarlyEvents.clear();
    clearDebugInspectors();
    setState("debugConsole", "");
    setState("debugBreakpointPath", state.activePath ?? "");
    setPanel("debug");
    updateDebugControls();
    try {
        await openDebugSocket(session);
        const initialize = await dapRequest("initialize", {
            clientID: "jahaziel",
            clientName: "Jahaziel",
            adapterID: session.adapterId,
            pathFormat: "path",
            linesStartAt1: true,
            columnsStartAt1: true,
            supportsVariableType: true,
            supportsVariablePaging: true,
            supportsRunInTerminalRequest: false,
        });
        const startResponse = dapRequest(requestMode, {});
        await waitForDapEvent("initialized");
        runtime.debugInitialized = true;
        const selectedBreakpointPath = state.debugBreakpointPath.trim() || state.activePath;
        await applyDebugBreakpoints(false);
        await applyStoredDebugBreakpoints(selectedBreakpointPath);
        if (initialize.body?.supportsConfigurationDoneRequest !== false) {
            await dapRequest("configurationDone", {});
        }
        await startResponse;
        await refreshDebugThread();
        setDebugState("Running");
    } catch (failure) {
        await stopDebugSession(false);
        throw failure;
    }
}

export async function attachDebugSession(): Promise<void> {
    const targetId = state.debugAttachTarget;
    if (targetId) await startDebugSession(targetId);
}

export async function reconnectDebugSession(): Promise<void> {
    const session = runtime.debugSession;
    if (!session) return;
    const { body: current } = await api<DebugSession>(
        `/api/v1/workspaces/${session.workspaceId}/debug-sessions/${session.id}`,
    );
    if (current.state !== "RUNNING") {
        await stopDebugSession(false);
        throw new Error("This debug session has already ended.");
    }
    runtime.debugSession = current;
    await openDebugSocket(current);
    runtime.debugInitialized = true;
    await applyStoredDebugBreakpoints();
    setDebugState("Reconnected");
}

export async function refreshDebugSessions(): Promise<void> {
    const workspace = state.workspace;
    if (!workspace) return;
    const { body: sessions } = await api<DebugSession[]>(
        `/api/v1/workspaces/${workspace.workspaceId}/debug-sessions`,
    );
    if (state.workspace?.workspaceId !== workspace.workspaceId) return;
    const activeSessions = sessions.filter(
        (session) => session.state !== "STOPPED" && session.state !== "FAILED",
    );
    const current =
        activeSessions.find((session) => session.id === runtime.debugSession?.id) ??
        activeSessions[0] ??
        null;
    if (runtime.debugSession && current?.id !== runtime.debugSession.id && runtime.debugSocket) {
        runtime.debugSocket.close(1000, "debug session changed");
        runtime.debugSocket = null;
        rejectDebugRequests("The debug session changed.");
    }
    runtime.debugSession = current;
    if (current) {
        setDebugState(
            runtime.debugSocket?.readyState === WebSocket.OPEN
                ? "Connected"
                : "Disconnected, reconnect available",
        );
    } else {
        clearDebugInspectors();
        setDebugState("Not running");
    }
    updateDebugControls();
}

export async function stopDebugSession(notifyFailure: boolean): Promise<boolean> {
    const session = runtime.debugSession;
    if (session) {
        try {
            const { body: stopped } = await api<DebugSession>(
                `/api/v1/workspaces/${session.workspaceId}/debug-sessions/${session.id}/stop`,
                { method: "POST" },
            );
            if (stopped.state === "FAILED") {
                runtime.debugSession = stopped;
                setDebugState(
                    stopped.error ||
                        "The debugger did not confirm disconnect; the workspace task is still running.",
                );
                updateDebugControls();
                if (notifyFailure)
                    toast(stopped.error || "The debugger did not confirm disconnect.", "error");
                return false;
            }
        } catch (failure) {
            setDebugState(`Stop request failed: ${errorText(failure)}`);
            updateDebugControls();
            if (notifyFailure) toast(`Could not stop debugging: ${errorText(failure)}`, "error");
            return false;
        }
    }
    runtime.debugSession = null;
    runtime.debugInitialized = false;
    const socket = runtime.debugSocket;
    runtime.debugSocket = null;
    if (socket && socket.readyState < WebSocket.CLOSING)
        socket.close(1000, "debug session stopped");
    rejectDebugRequests("The debug session was stopped.");
    runtime.dapEarlyEvents.clear();
    clearDebugInspectors();
    setDebugState("Not running");
    updateDebugControls();
    return true;
}

export async function applyDebugBreakpoints(showToast = true): Promise<void> {
    const workspace = state.workspace;
    if (!workspace || !runtime.debugSession) return;
    const path = state.debugBreakpointPath.trim() || state.activePath;
    if (!path) {
        if (showToast)
            throw new Error("Open a workspace file or enter a workspace-relative source path.");
        return;
    }
    const lines = [
        ...new Set(
            state.debugBreakpointLines
                .split(/[\s,]+/)
                .filter(Boolean)
                .map(Number)
                .filter((line) => Number.isSafeInteger(line) && line > 0),
        ),
    ];
    setState("debugBreakpoints", (entries) => [
        ...entries.filter((entry: BreakpointEntry) => entry.path !== path),
        { path, lines },
    ]);
    refreshBreakpointGutterForPath(path);
    const response = await sendDebugBreakpointSet(path, lines);
    if (showToast) {
        const verified = response.body?.breakpoints;
        const count = Array.isArray(verified)
            ? verified.filter((value) => (value as { verified?: unknown }).verified === true).length
            : lines.length;
        const name = path.split("/").at(-1) || path;
        toast(`${count} breakpoint${count === 1 ? "" : "s"} accepted for ${name}.`);
    }
}

async function sendDebugBreakpointSet(path: string, lines: number[]): Promise<DapMessage> {
    const name = path.split("/").at(-1) || path;
    return dapRequest("setBreakpoints", {
        source: { name, path },
        breakpoints: lines.map((line) => ({ line })),
        sourceModified: false,
    });
}

async function applyStoredDebugBreakpoints(skipPath?: string | null): Promise<void> {
    for (const entry of state.debugBreakpoints) {
        if (entry.path !== skipPath) await sendDebugBreakpointSet(entry.path, entry.lines);
    }
}

export async function toggleDebugBreakpoint(path: string, line: number): Promise<void> {
    const current = state.debugBreakpoints.find((entry) => entry.path === path)?.lines ?? [];
    const lines = new Set(current);
    if (lines.has(line)) lines.delete(line);
    else lines.add(line);
    const next = [...lines].sort((left, right) => left - right);
    setState("debugBreakpoints", (entries) => [
        ...entries.filter((entry: BreakpointEntry) => entry.path !== path),
        { path, lines: next },
    ]);
    refreshBreakpointGutterForPath(path);
    if (state.activePath === path) setState("debugBreakpointLines", next.join(", "));
    if (runtime.debugInitialized && runtime.debugSocket?.readyState === WebSocket.OPEN) {
        await sendDebugBreakpointSet(path, next);
    }
}

export function refreshBreakpointGutters(): void {
    for (const opened of runtime.openDocuments.values()) {
        refreshBreakpointGutter(opened.wrapper, opened.path);
    }
}

function refreshBreakpointGutterForPath(path: string): void {
    const opened = runtime.openDocuments.get(path);
    if (opened) refreshBreakpointGutter(opened.wrapper, path);
}

function refreshBreakpointGutter(wrapper: HTMLElement, path: string): void {
    const active = new Set(
        state.debugBreakpoints.find((entry) => entry.path === path)?.lines ?? [],
    );
    wrapper
        .querySelectorAll<HTMLElement>(".cm-lineNumbers .cm-gutterElement")
        .forEach((element) => {
            const line = Number(element.textContent?.trim());
            if (!Number.isSafeInteger(line) || line < 1) return;
            const enabled = active.has(line);
            element.classList.toggle("cm-debug-breakpoint", enabled);
            element.dataset.debugLine = String(line);
            element.setAttribute("role", "button");
            element.setAttribute("tabindex", "0");
            element.setAttribute(
                "aria-label",
                `${enabled ? "Clear" : "Set"} breakpoint at ${path}, line ${line}`,
            );
            element.setAttribute("aria-pressed", String(enabled));
        });
}

async function sendDebugControl(command: string): Promise<void> {
    if (runtime.debugThreadId === null)
        throw new Error("Pause the program before using stepping controls.");
    await dapRequest(command, { threadId: runtime.debugThreadId });
    if (command === "continue") setDebugState("Running");
}

export function debugCommand(command: string): void {
    sendDebugControl(command).catch((failure) => toast(errorText(failure), "error"));
}

async function refreshDebugThread(): Promise<void> {
    if (runtime.debugSocket?.readyState !== WebSocket.OPEN) return;
    const { body } = await dapRequest("threads");
    const threads = Array.isArray(body?.threads) ? body.threads : [];
    const first = threads.find(
        (thread): thread is { id: number } =>
            typeof thread === "object" &&
            thread !== null &&
            typeof (thread as { id?: unknown }).id === "number",
    );
    if (runtime.debugThreadId === null && first) runtime.debugThreadId = first.id;
    updateDebugControls();
}

async function refreshDebugStack(): Promise<void> {
    if (runtime.debugThreadId === null || runtime.debugSocket?.readyState !== WebSocket.OPEN)
        return;
    const { body } = await dapRequest("stackTrace", {
        threadId: runtime.debugThreadId,
        levels: 30,
    });
    const frames = body?.stackFrames;
    const typed = Array.isArray(frames)
        ? frames.filter(
              (frame): frame is DapFrame =>
                  typeof frame === "object" &&
                  frame !== null &&
                  typeof (frame as DapFrame).id === "number",
          )
        : [];
    runtime.debugFrames = typed;
    setState("debugFrames", typed);
    if (typed.length) await selectDebugFrame(typed[0]);
    else {
        runtime.debugFrameId = null;
        runtime.debugVariables = [];
        setState("debugVariables", []);
    }
}

export function selectDebugStackFrame(frame: DapFrame): void {
    void selectDebugFrame(frame).catch((failure) => toast(errorText(failure), "error"));
    if (frame.source?.path && !frame.source.path.startsWith("/")) {
        openDocument(frame.source.path).catch((failure) => toast(errorText(failure), "error"));
    }
}

async function selectDebugFrame(frame: DapFrame): Promise<void> {
    runtime.debugFrameId = frame.id;
    const { body } = await dapRequest("scopes", { frameId: frame.id });
    const scopes = Array.isArray(body?.scopes) ? body.scopes : [];
    const references = scopes
        .map((scope) => (scope as { variablesReference?: unknown }).variablesReference)
        .filter((reference): reference is number => typeof reference === "number" && reference > 0);
    if (references.length === 0) {
        runtime.debugVariables = [];
        setState("debugVariables", []);
    } else {
        const variables = await dapRequest("variables", { variablesReference: references[0] });
        const values = variables.body?.variables;
        const typed = Array.isArray(values)
            ? values.filter(
                  (value): value is DapVariable =>
                      typeof value === "object" &&
                      value !== null &&
                      typeof (value as DapVariable).name === "string",
              )
            : [];
        runtime.debugVariables = typed;
        setState("debugVariables", typed);
    }
    await refreshDebugWatches();
}

async function refreshDebugWatches(): Promise<void> {
    if (runtime.debugFrameId === null || runtime.debugSocket?.readyState !== WebSocket.OPEN) return;
    for (const expression of runtime.debugWatches) {
        try {
            const { body } = await dapRequest("evaluate", {
                expression,
                frameId: runtime.debugFrameId,
                context: "watch",
            });
            runtime.debugWatchValues.set(
                expression,
                typeof body?.result === "string" ? body.result : "<no value>",
            );
        } catch (failure) {
            runtime.debugWatchValues.set(expression, errorText(failure));
        }
    }
    setState("debugWatchValues", Object.fromEntries(runtime.debugWatchValues));
}

export function addDebugWatch(event: SubmitEvent): void {
    event.preventDefault();
    const input = byId<HTMLInputElement>("debug-watch-expression");
    const expression = input.value.trim();
    if (!expression) return;
    if (runtime.debugWatches.length >= 32) {
        toast("A debug session can track up to 32 watch expressions.", "error");
        return;
    }
    if (!runtime.debugWatches.includes(expression)) runtime.debugWatches.push(expression);
    setState("debugWatches", [...runtime.debugWatches]);
    input.value = "";
    void refreshDebugWatches();
}

export function evaluateDebugConsole(event: SubmitEvent): void {
    event.preventDefault();
    const input = byId<HTMLInputElement>("debug-console-input");
    const expression = input.value.trim();
    if (!expression) return;
    input.value = "";
    void (async () => {
        try {
            const { body } = await dapRequest("evaluate", {
                expression,
                ...(runtime.debugFrameId === null ? {} : { frameId: runtime.debugFrameId }),
                context: "repl",
            });
            const result = typeof body?.result === "string" ? body.result : "<no value>";
            appendDebugConsole(`> ${expression}\n${result}\n`);
        } catch (failure) {
            appendDebugConsole(`> ${expression}\n[error] ${errorText(failure)}\n`);
        }
    })();
}

export function updateDebugControls(): void {
    const connected = runtime.debugSocket?.readyState === WebSocket.OPEN;
    const hasSession = runtime.debugSession !== null;
    setState("debugStartDisabled", hasSession);
    setState("debugLoadLaunchConfigDisabled", hasSession || state.workspace === null);
    setState("debugConfigSelectDisabled", hasSession || state.debugConfigs.length === 0);
    setState("debugStopDisabled", !hasSession);
    setState("debugReconnectHidden", !hasSession || connected);
    setState("debugLanguageDisabled", hasSession);
    setState("debugAttachTargetDisabled", hasSession || state.debugTargets.length === 0);
    setState(
        "debugAttachDisabled",
        hasSession || !state.debugTargets.some((target) => target.id === state.debugAttachTarget),
    );
    setState("debugProgramDisabled", hasSession);
    setState("debugMainClassDisabled", hasSession);
    setState("debugWorkingDirectoryDisabled", hasSession);
    setState("debugArgumentsDisabled", hasSession);
    setState("debugStopOnEntryDisabled", hasSession);
    setState("debugApplyBreakpointsDisabled", !connected);
    setState("debugWatchSubmitDisabled", !connected);
    setState("debugConsoleSubmitDisabled", !connected);
    const paused = (command: string): boolean => command !== "pause";
    setState("debugControls", {
        continue:
            !connected ||
            runtime.debugThreadId === null ||
            runtime.debugPaused !== paused("continue"),
        pause:
            !connected || runtime.debugThreadId === null || runtime.debugPaused !== paused("pause"),
        next:
            !connected || runtime.debugThreadId === null || runtime.debugPaused !== paused("next"),
        stepIn:
            !connected ||
            runtime.debugThreadId === null ||
            runtime.debugPaused !== paused("stepIn"),
        stepOut:
            !connected ||
            runtime.debugThreadId === null ||
            runtime.debugPaused !== paused("stepOut"),
    });
}

export { clearDebugLaunchConfigurations, refreshBreakpointGutter };
