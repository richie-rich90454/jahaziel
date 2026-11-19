/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.basis;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An OAuth error returned by basis-auth, mapped to the documented application code. The message
 * never carries the raw response body, tokens, or secrets.
 */
public final class BasisOAuthException extends BasisException {
    private static final long serialVersionUID = 1L;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, Integer> CODES = new LinkedHashMap<String, Integer>();
    private static final Map<Integer, String> RECOVERY = new LinkedHashMap<Integer, String>();

    static {
        code("client_not_registered", 14001, "Check the client ID and environment.");
        code("unknown_client", 14001, "Check the client ID and environment.");
        code("client_disabled", 14002, "Ask the operator to re-enable the client.");
        code("client_suspended", 14002, "Ask the operator to re-enable the client.");
        code("public_client_secret", 14003, "Use PKCE and send no client secret.");
        code("public_client_with_secret", 14003, "Use PKCE and send no client secret.");
        code("secret_not_allowed", 14003, "Use PKCE and send no client secret.");
        code(
                "invalid_client",
                14004,
                "Use HTTP Basic with the current confidential client secret.");
        code(
                "invalid_client_secret",
                14004,
                "Use HTTP Basic with the current confidential client secret.");
        code(
                "client_no_organizations",
                14005,
                "Ask the operator to link an organization to the client.");
        code("no_organizations", 14005, "Ask the operator to link an organization to the client.");
        code(
                "invalid_redirect_uri",
                14100,
                "Compare the redirect URI character-for-character with the registered value.");
        code(
                "unregistered_redirect_uri",
                14100,
                "Compare the redirect URI character-for-character with the registered value.");
        code(
                "redirect_uri_mismatch",
                14100,
                "Compare the redirect URI character-for-character with the registered value.");
        code(
                "invalid_scope",
                14401,
                "Align resource scopes, client scopes, and the requested scope.");
        code(
                "scope_not_allowed",
                14401,
                "Align resource scopes, client scopes, and the requested scope.");
        code(
                "resource_not_registered",
                14407,
                "Ask the operator to register the resource audience.");
        code("unknown_resource", 14407, "Ask the operator to register the resource audience.");
        code("invalid_resource", 14407, "Ask the operator to register the resource audience.");
        code("unsupported_response_type", 14429, "Send response_type=code.");
        code(
                "resource_not_registered_for_client",
                14501,
                "Ask the operator to add the audience to this client.");
        code("resource_not_allowed", 14501, "Ask the operator to add the audience to this client.");
        code("interaction_required", 2400, "Restart authorization.");
        code("interaction_expired", 2400, "Restart authorization.");
        code("login_required", 2400, "Restart authorization.");
    }

    private final int code;
    private final String error;
    private final String recovery;

    private BasisOAuthException(int status, int code, String error, String recovery) {
        super("basis-auth request failed with HTTP " + status);
        this.code = code;
        this.error = error;
        this.recovery = recovery;
    }

    /** Parses an OAuth error body into a coded exception without keeping the raw body. */
    public static BasisOAuthException from(int status, String body) {
        String error = null;
        try {
            JsonNode node = MAPPER.readTree(body);
            if (node != null && node.get("error") != null && !node.get("error").isNull()) {
                error = node.get("error").asString();
            }
        } catch (Exception ignored) {
            // A non-JSON error body still fails closed with the generic code.
        }
        Integer mapped = error == null ? null : CODES.get(error);
        int code = mapped == null ? 0 : mapped.intValue();
        String recovery = RECOVERY.get(Integer.valueOf(code));
        if (recovery == null) {
            recovery = "Sign-in failed; restart authorization or contact the operator.";
        }
        return new BasisOAuthException(status, code, error, recovery);
    }

    /** The documented application code, or 0 when the issuer error is unknown. */
    public int code() {
        return code;
    }

    /** The issuer's error value, used internally to recognize invalid_grant. */
    public String error() {
        return error;
    }

    /** Operator-facing recovery guidance, safe to show in a login failure response. */
    public String recovery() {
        return recovery;
    }

    private static void code(String error, int code, String recovery) {
        CODES.put(error, Integer.valueOf(code));
        if (!RECOVERY.containsKey(Integer.valueOf(code))) {
            RECOVERY.put(Integer.valueOf(code), recovery);
        }
    }
}
