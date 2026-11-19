///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { byId, effect, on } from "../lib/dom";
import { t, subscribeLocale } from "../lib/i18n";
import { state } from "../lib/store";
import { dismissTour, getTourStep, setTourStep, TOUR_STEPS, type TourStep } from "../lib/tour";

let highlighted: HTMLElement | null = null;

export function tourMarkup(): string {
    return `
    <div id="onboarding-tour" class="onboarding-tour" role="dialog" aria-modal="false" aria-labelledby="tour-title" hidden>
      <div class="tour-card">
        <span id="tour-progress" class="tour-progress"></span>
        <div id="tour-title" class="tour-title"></div>
        <p id="tour-body"></p>
        <div class="tour-actions">
          <button id="tour-skip" class="button button-quiet" type="button"></button>
          <button id="tour-next" class="button btn btn-primary" type="button"></button>
        </div>
      </div>
    </div>
  `;
}

function stepFor(step: number): TourStep | null {
    if (step < 0 || step >= TOUR_STEPS.length) return null;
    const candidate = TOUR_STEPS[step];
    return candidate.page === state.page ? candidate : null;
}

function paint(): void {
    const overlay = byId("onboarding-tour");
    if (highlighted) {
        highlighted.classList.remove("tour-highlight");
        highlighted = null;
    }
    let current = getTourStep();
    if (state.page === "workbench" && current === 0) {
        setTourStep(1);
        current = 1;
    }
    const step = stepFor(current);
    if (!step || !state.user) {
        overlay.hidden = true;
        return;
    }
    overlay.hidden = false;
    byId("tour-progress").textContent = t("tour.progress", {
        current: current + 1,
        total: TOUR_STEPS.length,
    });
    byId("tour-title").textContent = t(step.titleKey);
    byId("tour-body").textContent = t(step.bodyKey);
    byId("tour-next").textContent =
        current === TOUR_STEPS.length - 1 ? t("tour.finish") : t("tour.next");
    byId("tour-skip").textContent = t("tour.skip");
    if (step.target) {
        highlighted = document.querySelector<HTMLElement>(step.target);
        highlighted?.classList.add("tour-highlight");
    }
}

export function bindTour(): void {
    effect(paint);
    effect(() => {
        void state.page;
        void state.user;
        paint();
    });
    subscribeLocale(paint);
    on(byId("tour-next"), "click", () => {
        const next = getTourStep() + 1;
        if (next >= TOUR_STEPS.length) dismissTour();
        else setTourStep(next);
        paint();
    });
    on(byId("tour-skip"), "click", () => {
        dismissTour();
        paint();
    });
}
