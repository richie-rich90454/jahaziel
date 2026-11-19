///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

export type ThemeName = "light" | "dark" | "high-contrast";

export const THEMES: readonly ThemeName[] = ["light", "dark", "high-contrast"];

export const THEME_LABELS: Record<ThemeName, string> = {
    light: "Calm light",
    dark: "Dark",
    "high-contrast": "High contrast",
};

const STORAGE_KEY = "jahaziel:theme";
const listeners = new Set<() => void>();

function readStoredTheme(): ThemeName {
    try {
        const value = localStorage.getItem(STORAGE_KEY);
        if (value && THEMES.includes(value as ThemeName)) return value as ThemeName;
    } catch {
        /* Storage can be disabled. */
    }
    return "light";
}

let current: ThemeName = readStoredTheme();

function apply(): void {
    if (typeof document === "undefined") return;
    document.documentElement.dataset.theme = current;
    document.documentElement.style.colorScheme =
        current === "light" ? "light" : current === "dark" ? "dark" : "light";
}

apply();

export function getTheme(): ThemeName {
    return current;
}

export function setTheme(theme: ThemeName): void {
    if (!THEMES.includes(theme) || theme === current) return;
    current = theme;
    try {
        localStorage.setItem(STORAGE_KEY, theme);
    } catch {
        /* Storage can be disabled. */
    }
    apply();
    for (const listener of listeners) listener();
}

export function subscribeTheme(listener: () => void): () => void {
    listeners.add(listener);
    return () => listeners.delete(listener);
}
