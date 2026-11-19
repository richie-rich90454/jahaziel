/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.security;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pattern-based redaction applied to every log line and toast. Tokens, authorization codes,
 * secrets, PKCE verifiers, bearer values, raw cookies, and JWTs never leave this class unmasked.
 */
public final class SecretRedactor {

    private static final String MASK = "[redacted]";
    private static final ArrayList<Pattern> PATTERNS = new ArrayList<Pattern>();

    static {
        PATTERNS.add(Pattern.compile("(?i)\\b(?:cookie|set-cookie)\\s*:\\s*[^\\r\\n]+"));
        PATTERNS.add(Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/-]+=*"));
        PATTERNS.add(
                Pattern.compile("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}"));
        PATTERNS.add(
                Pattern.compile(
                        "(?i)[?&](?:code|token|refresh[_-]?token|access_token|id_token|"
                                + "code_verifier)=[^&\\s]+"));
        PATTERNS.add(
                Pattern.compile(
                        "(?i)\\b(?:token|refresh[_-]?token|code|secret|verifier|password|passwd|"
                                + "api[_-]?key|client[_-]?secret|authorization)\\b\\s*[:=]\\s*"
                                + "(?:\"[^\"]*\"|'[^']*'|\\bBearer\\s+[A-Za-z0-9._~+/-]+=*|[^\\s,;&]+)"));
    }

    private SecretRedactor() {}

    /** Returns the text with every recognized secret replaced by a mask. */
    public static String redact(String text) {
        if (text == null) return null;
        String current = text;
        for (int i = 0; i < PATTERNS.size(); i++) {
            current = replace(current, PATTERNS.get(i));
        }
        return current;
    }

    /** Applies the same redaction before a string is shown in a browser toast. */
    public static String safeForToast(String text) {
        return redact(text);
    }

    private static String replace(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder out = new StringBuilder();
        int cursor = 0;
        while (matcher.find()) {
            out.append(text, cursor, matcher.start());
            out.append(MASK);
            cursor = matcher.end();
        }
        out.append(text, cursor, text.length());
        return out.toString();
    }
}
