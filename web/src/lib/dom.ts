///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import { createEffect, createRoot, getOwner, runWithOwner, type Owner } from "solid-js";

let rootOwner: Owner | undefined;

/** Mounts a Solid component tree into a host element, owning all effects. */
export function render(code: () => HTMLElement, host: HTMLElement): void {
    createRoot(() => {
        rootOwner = getOwner() ?? undefined;
        host.replaceChildren(code());
    });
}

/** Runs a Solid effect inside the application's reactive root. */
export function effect(work: () => unknown): void {
    const run = (): void =>
        createEffect(
            // Solid 2 tracks only in the compute phase. Callers pass one callback
            // that both reads reactive state and writes the DOM, so it must run
            // here. The effect phase forwards a returned cleanup so Solid
            // disposes it before the next run and on teardown.
            () => work(),
            (cleanup) => (typeof cleanup === "function" ? (cleanup as () => void) : undefined),
        );
    if (rootOwner) runWithOwner(rootOwner, run);
    else run();
}

/** Runs a disposable effect for listeners that must be removed when a subsystem stops. */
export function ownedEffect(work: () => unknown): () => void {
    let dispose = (): void => {};
    createRoot((done) => {
        dispose = done;
        createEffect(
            () => work(),
            (cleanup) => (typeof cleanup === "function" ? (cleanup as () => void) : undefined),
        );
    });
    return () => dispose();
}

/** Builds one element from a static HTML template string. */
export function html(
    strings: TemplateStringsArray,
    ...values: Array<string | number>
): HTMLElement {
    const template = document.createElement("template");
    let markup = "";
    for (let index = 0; index < strings.length; index += 1) {
        markup += strings[index];
        if (index < values.length) markup += String(values[index]);
    }
    template.innerHTML = markup.trim();
    const element = template.content.firstElementChild;
    if (!element) throw new Error("HTML template produced no element.");
    return element as HTMLElement;
}

export function byId<T extends HTMLElement = HTMLElement>(id: string): T {
    const found = document.getElementById(id);
    if (!found) throw new Error(`Missing page element #${id}`);
    return found as T;
}

export function qs<T extends HTMLElement = HTMLElement>(root: ParentNode, selector: string): T {
    const found = root.querySelector<T>(selector);
    if (!found) throw new Error(`Missing element ${selector}`);
    return found;
}

export function qsa<T extends HTMLElement = HTMLElement>(root: ParentNode, selector: string): T[] {
    return Array.from(root.querySelectorAll<T>(selector));
}

export function on<K extends keyof HTMLElementEventMap>(
    node: HTMLElement,
    type: K,
    handler: (event: HTMLElementEventMap[K]) => void,
): void {
    node.addEventListener(type, handler as EventListener);
}

export function bindText(node: Element, read: () => string): void {
    effect(() => {
        const value = read();
        if (node.textContent !== value) node.textContent = value;
    });
}

export function bindHidden(node: HTMLElement, read: () => boolean): void {
    effect(() => {
        node.hidden = read();
    });
}

export function bindDisabled(
    node: HTMLButtonElement | HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement,
    read: () => boolean,
): void {
    effect(() => {
        node.disabled = read();
    });
}

export function bindValue(
    node: HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement,
    read: () => string,
): void {
    effect(() => {
        const value = read();
        if (node.value !== value) node.value = value;
    });
}

export function bindAttribute(node: Element, name: string, read: () => string): void {
    effect(() => node.setAttribute(name, read()));
}

export function bindClass(node: Element, className: string, read: () => boolean): void {
    effect(() => node.classList.toggle(className, read()));
}

export function run(action: () => void | Promise<void>): void {
    void action();
}
