///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

export function escapeHtml(value: string): string {
    return value
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}

function safeUrl(value: string): string | null {
    const trimmed = value.trim();
    if (trimmed.includes("\\") || /^[\\/]{2}/.test(trimmed)) return null;
    if (/^(https?:|mailto:|\/|#|\.\/|\.\.\/)/i.test(trimmed)) return trimmed;
    if (/^[\w./-]+(?:\?[\w=&%-]*)?(?:#[\w-]+)?$/.test(trimmed)) return trimmed;
    return null;
}

function inline(value: string): string {
    let result = escapeHtml(value);
    result = result.replace(/`([^`]+)`/g, (_match, code: string) => `<code>${code}</code>`);
    result = result.replace(
        /\*\*([^*]+)\*\*/g,
        (_match, text: string) => `<strong>${text}</strong>`,
    );
    result = result.replace(
        /(^|[^*])\*([^*]+)\*/g,
        (_match, before: string, text: string) => `${before}<em>${text}</em>`,
    );
    result = result.replace(/\[([^\]]+)\]\(([^)]+)\)/g, (match, label: string, url: string) => {
        const safe = safeUrl(url);
        return safe ? `<a href="${safe}" rel="noreferrer" target="_blank">${label}</a>` : match;
    });
    return result;
}

/** Minimal, dependency-free Markdown renderer. Output is HTML-escaped before markup is added. */
export function renderMarkdown(source: string): string {
    const lines = source.replaceAll("\r\n", "\n").split("\n");
    const output: string[] = [];
    let inCode = false;
    let codeBuffer: string[] = [];
    let listBuffer: string[] = [];

    const flushList = (): void => {
        if (listBuffer.length) {
            output.push(
                `<ul>${listBuffer.map((item) => `<li>${inline(item)}</li>`).join("")}</ul>`,
            );
            listBuffer = [];
        }
    };

    for (const line of lines) {
        if (line.trim().startsWith("```")) {
            if (inCode) {
                output.push(`<pre><code>${escapeHtml(codeBuffer.join("\n"))}</code></pre>`);
                codeBuffer = [];
            }
            inCode = !inCode;
            continue;
        }
        if (inCode) {
            codeBuffer.push(line);
            continue;
        }
        const heading = /^(#{1,6})\s+(.*)$/.exec(line);
        if (heading) {
            flushList();
            const level = heading[1].length;
            output.push(`<h${level}>${inline(heading[2])}</h${level}>`);
            continue;
        }
        if (/^\s*[-*+]\s+/.test(line)) {
            listBuffer.push(line.replace(/^\s*[-*+]\s+/, ""));
            continue;
        }
        flushList();
        if (/^\s*>\s?/.test(line)) {
            output.push(`<blockquote>${inline(line.replace(/^\s*>\s?/, ""))}</blockquote>`);
            continue;
        }
        if (/^\s*(---+|\*\*\*+|___+)\s*$/.test(line)) {
            output.push("<hr />");
            continue;
        }
        if (!line.trim()) {
            output.push("");
            continue;
        }
        output.push(`<p>${inline(line)}</p>`);
    }
    flushList();
    if (codeBuffer.length)
        output.push(`<pre><code>${escapeHtml(codeBuffer.join("\n"))}</code></pre>`);
    return output.join("\n");
}
