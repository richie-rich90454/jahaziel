///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

export type IconName =
    | "files"
    | "folder"
    | "plus"
    | "download"
    | "external"
    | "close"
    | "code"
    | "play"
    | "debug"
    | "branch"
    | "back"
    | "undo"
    | "redo"
    | "caret"
    | "more";

const paths: Record<IconName, string> = {
    files: '<rect x="3" y="2" width="10" height="12"/><path d="M6 5h4M6 8h4M6 11h4"/>',
    folder: '<path d="M2 5h5l1.5 2H14v7H2z"/>',
    plus: '<path d="M8 3v10M3 8h10"/>',
    download: '<path d="M8 2v8M5 7l3 3 3-3M3 13h10"/>',
    external: '<path d="M9 3h4v4M13 3L7 9M11 9v4H3V5h4"/>',
    close: '<path d="M4 4l8 8M12 4l-8 8"/>',
    code: '<path d="M6 3L2 8l4 5M10 3l4 5-4 5"/>',
    play: '<path d="M4 2.5l9 5.5-9 5.5z" fill="currentColor" stroke="none"/>',
    debug: '<path d="M8 1.5l6.5 6.5L8 14.5 1.5 8z" fill="currentColor" stroke="none"/>',
    branch: '<circle cx="4" cy="3.5" r="1.5"/><circle cx="12" cy="4" r="1.5"/><circle cx="4" cy="12.5" r="1.5"/><path d="M4 5v6M4 8h4a4 4 0 0 0 4-4"/>',
    back: '<path d="M13 8H3M3 8l4-4M3 8l4 4"/>',
    undo: '<path d="M5 5h4a4 4 0 1 1 0 8H6M5 5l3-3M5 5l3 3"/>',
    redo: '<path d="M11 5H7a4 4 0 1 0 0 8h3M11 5L8 2M11 5L8 8"/>',
    caret: '<path d="M4 6l4 4 4-4"/>',
    more: '<circle cx="3" cy="8" r="1" fill="currentColor" stroke="none"/><circle cx="8" cy="8" r="1" fill="currentColor" stroke="none"/><circle cx="13" cy="8" r="1" fill="currentColor" stroke="none"/>',
};

/** Inline 16px icon that inherits the surrounding text colour. */
export function icon(name: IconName): string {
    return `<svg class="icon" viewBox="0 0 16 16" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linejoin="miter" aria-hidden="true" focusable="false">${paths[name]}</svg>`;
}
