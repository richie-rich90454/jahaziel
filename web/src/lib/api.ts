///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

interface ApiErrorBody {
    detail?: string;
    message?: string;
    title?: string;
    code?: string;
    correlationId?: string;
}

/** Fetches JSON from the control plane with the browser session cookie. */
export async function api<T>(
    path: string,
    init: RequestInit = {},
): Promise<{ body: T; response: Response }> {
    let response: Response;
    try {
        response = await fetch(path, {
            credentials: "same-origin",
            ...init,
            headers: {
                ...(init.body ? { "Content-Type": "application/json" } : {}),
                ...init.headers,
            },
        });
    } catch {
        throw new Error("The Jahaziel server could not be reached.");
    }
    if (response.status === 204) return { body: null as T, response };
    if (!response.ok) {
        let detail = "Request failed";
        let correlationId: string | undefined;
        try {
            const body = (await response.json()) as ApiErrorBody;
            detail = body.detail ?? body.message ?? body.title ?? body.code ?? detail;
            correlationId = body.correlationId;
        } catch {
            /* Keep the status for non-JSON errors. */
        }
        const reference = correlationId ? ` (ref ${correlationId})` : "";
        throw new Error(`${response.status} ${detail}${reference}`);
    }
    if (!(response.headers.get("content-type") || "").includes("json"))
        return { body: null as T, response };
    return { body: (await response.json()) as T, response };
}

export function errorText(failure: unknown): string {
    return failure instanceof Error ? failure.message : "Something went wrong.";
}

/** True when the host does not expose an optional endpoint. */
export function isUnavailable(failure: unknown): boolean {
    return failure instanceof Error && /^(404|405)\b/.test(failure.message);
}
