///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { api, errorText, isUnavailable } from "./api";
import { setState, state, toast, type ConfigView, type FeatureFlag } from "./store";

let configEtag = "";

interface ConfigResponse {
    config?: unknown;
    restartRequired?: boolean;
}

function updateConfig(patch: Partial<ConfigView>): void {
    setState("config", (current: ConfigView) => ({ ...current, ...patch }));
}

function stringify(value: unknown): string {
    return JSON.stringify(value ?? {}, null, 2);
}

export function configDiff(before: string, after: string): string {
    const oldLines = before.split("\n");
    const newLines = after.split("\n");
    const rows: string[] = [];
    for (let index = 0; index < Math.max(oldLines.length, newLines.length); index += 1) {
        if (oldLines[index] === newLines[index]) continue;
        if (index < oldLines.length) rows.push(`- ${oldLines[index]}`);
        if (index < newLines.length) rows.push(`+ ${newLines[index]}`);
    }
    return rows.join("\n");
}

export function setConfigValue(value: string): void {
    updateConfig({ value, error: "", diff: configDiff(state.config.saved, value) });
}

/** Validates the editor text without contacting the server. */
export function validateConfig(): boolean {
    const value = state.config.value.trim() || "{}";
    try {
        const parsed = JSON.parse(value) as Record<string, unknown>;
        if (parsed === null || Array.isArray(parsed) || typeof parsed !== "object")
            throw new Error("Configuration must be a JSON object.");
        for (const [key, entry] of Object.entries(parsed)) {
            if (key.startsWith("_")) continue;
            if (typeof entry !== "string") throw new Error(`${key} must have a string value.`);
        }
        updateConfig({ error: "", diff: configDiff(state.config.saved, value) });
        return true;
    } catch (failure) {
        updateConfig({ error: errorText(failure) });
        return false;
    }
}

export async function loadConfig(): Promise<void> {
    try {
        const { body, response } = await api<ConfigResponse | unknown>("/api/v1/admin/config");
        const payload = (body ?? {}) as ConfigResponse;
        const value = stringify(payload.config ?? payload);
        configEtag = response.headers.get("ETag") ?? "";
        updateConfig({
            value,
            saved: value,
            diff: "",
            error: "",
            restartRequired: payload.restartRequired === true,
            unavailable: false,
        });
    } catch (failure) {
        if (!isUnavailable(failure)) throw failure;
        updateConfig({ unavailable: true });
    }
}

export async function saveConfig(): Promise<void> {
    if (!validateConfig()) return;
    try {
        const { response } = await api("/api/v1/admin/config", {
            method: "PUT",
            headers: configEtag ? { "If-Match": configEtag } : {},
            body: state.config.value,
        });
        configEtag = response.headers.get("ETag") ?? configEtag;
        const saved = state.config.value;
        updateConfig({ saved, diff: "", error: "", restartRequired: true });
        toast("Configuration saved. A restart is required.");
    } catch (failure) {
        if (isUnavailable(failure)) {
            updateConfig({ unavailable: true });
            toast("Unavailable on this host", "error");
            return;
        }
        updateConfig({ error: errorText(failure) });
        toast(errorText(failure), "error");
    }
}

interface FlagView {
    flags?: Record<string, boolean>;
    overrides?: Array<{ name: string; scope: string; target: string; enabled: string }>;
}

function applyFlags(body: FlagView | null): void {
    const globals = body?.flags ?? {};
    const overrides = body?.overrides ?? [];
    const flags: FeatureFlag[] = Object.keys(globals).map((name) => {
        const flag: FeatureFlag = { name, enabled: globals[name] === true };
        for (const entry of overrides) {
            if (entry.name !== name) continue;
            if (entry.scope === "user") flag.userOverride = String(entry.enabled) === "true";
            if (entry.scope === "workspace")
                flag.workspaceOverride = String(entry.enabled) === "true";
        }
        return flag;
    });
    setState("flags", flags);
    setState("flagsState", `${flags.length} flags`);
}

export async function loadFlags(): Promise<void> {
    try {
        const { body } = await api<FlagView | null>("/api/v1/admin/flags");
        applyFlags(body);
        setState("flagsUnavailable", false);
    } catch (failure) {
        if (!isUnavailable(failure)) throw failure;
        setState("flagsUnavailable", true);
        setState("flagsState", "Unavailable on this host");
    }
}

type Override = "global" | "userOverride" | "workspaceOverride";

/** Writes one flag value; a null value clears an override. */
export async function setFlag(
    name: string,
    override: Override,
    value: boolean | null,
): Promise<void> {
    const scope =
        override === "global" ? "global" : override === "userOverride" ? "user" : "workspace";
    const target =
        scope === "global"
            ? "-"
            : scope === "workspace"
              ? (state.workspace?.workspaceId ?? "")
              : (window.prompt("User id for the override") ?? "");
    if (scope !== "global" && !target) return;
    try {
        const { body } = await api<FlagView | null>("/api/v1/admin/flags", {
            method: "PUT",
            body: JSON.stringify(
                value === null
                    ? { action: "clear", name, scope, target }
                    : { name, scope, target, enabled: value },
            ),
        });
        applyFlags(body);
        toast("Feature flag saved.");
    } catch (failure) {
        if (isUnavailable(failure)) {
            setState("flagsUnavailable", true);
            toast("Unavailable on this host", "error");
            return;
        }
        toast(errorText(failure), "error");
    }
}
