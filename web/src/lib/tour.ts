///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

export interface TourStep {
    key: string;
    titleKey: string;
    bodyKey: string;
    target: string;
    page: "home" | "workbench";
}

export const TOUR_STEPS: readonly TourStep[] = [
    {
        key: "workspace",
        titleKey: "tour.step1Title",
        bodyKey: "tour.step1Body",
        target: "#workspace-form",
        page: "home",
    },
    {
        key: "file",
        titleKey: "tour.step2Title",
        bodyKey: "tour.step2Body",
        target: "#file-tree",
        page: "workbench",
    },
    {
        key: "run",
        titleKey: "tour.step3Title",
        bodyKey: "tour.step3Body",
        target: "#task-list",
        page: "workbench",
    },
    {
        key: "preview",
        titleKey: "tour.step4Title",
        bodyKey: "tour.step4Body",
        target: '.workbench-panel-nav [data-panel="preview"]',
        page: "workbench",
    },
];

const STORAGE_KEY = "jahaziel:onboarding";
const DONE = "done";

export function isTourDismissed(): boolean {
    try {
        return localStorage.getItem(STORAGE_KEY) === DONE;
    } catch {
        return false;
    }
}

export function getTourStep(): number {
    if (isTourDismissed()) return -1;
    try {
        const raw = localStorage.getItem(STORAGE_KEY);
        const value = raw === null ? 0 : Number(raw);
        if (!Number.isInteger(value) || value < 0 || value >= TOUR_STEPS.length) return 0;
        return value;
    } catch {
        return 0;
    }
}

export function setTourStep(step: number): void {
    try {
        localStorage.setItem(STORAGE_KEY, String(Math.max(0, Math.min(step, TOUR_STEPS.length))));
    } catch {
        /* Storage can be disabled. */
    }
}

export function dismissTour(): void {
    try {
        localStorage.setItem(STORAGE_KEY, DONE);
    } catch {
        /* Storage can be disabled. */
    }
}

export function resetTour(): void {
    try {
        localStorage.removeItem(STORAGE_KEY);
    } catch {
        /* Storage can be disabled. */
    }
}
