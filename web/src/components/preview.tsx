///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { errorText } from "../lib/api";
import {
    findPreviewPorts,
    openPreview,
    refreshPortStatuses,
    registerPreviewPort,
    reloadPreview,
    resizePreviewBy,
    resizePreviewStart,
    selectPreviewPort,
} from "../lib/controller";
import { bindDisabled, bindHidden, bindText, byId, effect, on } from "../lib/dom";
import { subscribeLocale, t } from "../lib/i18n";
import { icon } from "../lib/icons";
import { state, toast } from "../lib/store";

export function previewMarkup(): string {
    return `
    <div id="preview-resizer" class="preview-resizer" role="separator" aria-orientation="horizontal" aria-label="${t("preview.resize")}" data-i18n-aria="preview.resize" aria-valuemin="120" aria-valuemax="720" aria-valuenow="240" tabindex="0"></div>
    <section id="preview-dock" class="preview-dock" aria-label="${t("preview.aria")}" data-i18n-aria="preview.aria" hidden>
      <div class="preview-heading">
        <label for="preview-port-select" data-i18n="preview.heading">${t("preview.heading")}</label>
        <select id="preview-port-select" aria-label="${t("preview.port")}" data-i18n-aria="preview.port"></select>
        <span id="preview-state">${t("preview.selectPort")}</span>
        <button id="find-preview-ports" class="button button-quiet" type="button"><span data-i18n="preview.findPorts">${t("preview.findPorts")}</span></button>
        <button id="preview-reload" class="button button-quiet" type="button" disabled><span data-i18n="preview.reload">${t("preview.reload")}</span></button>
        <a id="preview-new-tab" class="button button-quiet" href="#" target="_blank" rel="noreferrer" aria-disabled="true"><span data-i18n="preview.newTab">${t("preview.newTab")}</span></a>
      </div>
      <ul id="preview-candidates" class="debug-list" hidden></ul>
      <iframe id="preview-frame" title="${t("preview.aria")}" sandbox="allow-forms allow-scripts allow-same-origin allow-popups allow-downloads" hidden></iframe>
      <div id="preview-empty" class="preview-empty" data-i18n="preview.empty">${t("preview.empty")}</div>
    </section>
  `;
}

export function bindPreview(): void {
    const links = byId("preview-links");
    const renderLinks = (): void => {
        links.replaceChildren(
            ...(state.workspace?.ports ?? [])
                .map((port) => {
                    const button = document.createElement("button");
                    button.type = "button";
                    button.className = "preview-chip";
                    button.innerHTML = `:${port.port} ${t("preview.heading")} ${icon("external")}`;
                    button.disabled = port.listening !== true;
                    button.title =
                        port.listening === true
                            ? t("preview.openPort", { port: port.port })
                            : t("preview.closedPort", { port: port.port });
                    on(button, "click", () => openPreview(port));
                    const status = document.createElement("span");
                    status.className = "preview-port-status";
                    status.dataset.listening = String(port.listening === true);
                    status.textContent =
                        port.listening === null
                            ? t("preview.checking")
                            : port.listening
                              ? t("preview.listening")
                              : t("preview.notListening");
                    return [button, status];
                })
                .flat(),
        );
    };
    effect(renderLinks);
    subscribeLocale(renderLinks);
    bindHidden(byId("refresh-preview-ports"), () => state.refreshPortsHidden);
    on(byId("refresh-preview-ports"), "click", () => {
        void refreshPortStatuses().catch((failure) => toast(errorText(failure), "error"));
    });

    bindHidden(byId("preview-dock"), () => state.activePanel !== "preview");
    bindText(byId("preview-state"), () => state.previewState);
    bindHidden(byId("preview-frame"), () => state.previewFrameHidden);
    bindHidden(byId("preview-empty"), () => state.previewEmptyHidden);
    const reload = byId<HTMLButtonElement>("preview-reload");
    bindDisabled(reload, () => state.previewReloadDisabled);
    on(reload, "click", reloadPreview);
    const newTab = byId<HTMLAnchorElement>("preview-new-tab");
    effect(() => newTab.setAttribute("aria-disabled", String(state.previewNewTabDisabled)));
    on(newTab, "click", (event) => {
        if (newTab.getAttribute("aria-disabled") === "true") event.preventDefault();
    });
    const select = byId<HTMLSelectElement>("preview-port-select");
    on(select, "change", () => selectPreviewPort(Number(select.value)));
    const resizer = byId("preview-resizer");
    on(resizer, "pointerdown", (event) => resizePreviewStart(event as PointerEvent));
    on(resizer, "keydown", (event) => {
        const steps: Record<string, number> = {
            ArrowUp: 24,
            ArrowDown: -24,
            PageUp: 120,
            PageDown: -120,
            Home: -100_000,
            End: 100_000,
        };
        const delta = steps[event.key];
        if (delta === undefined) return;
        event.preventDefault();
        resizePreviewBy(delta);
    });
    on(byId("find-preview-ports"), "click", () => {
        void findPreviewPorts().catch((failure) => toast(errorText(failure), "error"));
    });

    const candidates = byId("preview-candidates");
    const renderCandidates = (): void => {
        const ports = state.previewCandidates;
        if (!ports || ports.length === 0) {
            candidates.replaceChildren();
            candidates.hidden = !ports;
            if (ports?.length === 0) {
                const empty = document.createElement("li");
                empty.className = "debug-empty";
                empty.textContent = t("preview.noCandidates");
                candidates.append(empty);
                candidates.hidden = false;
            }
            return;
        }
        candidates.hidden = false;
        candidates.replaceChildren(
            ...ports.map((candidate) => {
                const item = document.createElement("li");
                item.className = "debug-empty";
                item.textContent = `:${candidate.port}, ${candidate.sources.join(", ") || "tracked"}`;
                const register = document.createElement("button");
                register.type = "button";
                register.className = "text-action";
                register.textContent = t("preview.register");
                on(register, "click", () => {
                    void registerPreviewPort(candidate.port).catch((failure) =>
                        toast(errorText(failure), "error"),
                    );
                });
                item.append(register);
                return item;
            }),
        );
    };
    effect(renderCandidates);
    subscribeLocale(renderCandidates);
}
