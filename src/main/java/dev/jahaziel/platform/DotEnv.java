/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal {@code .env} parser. Handles CRLF/LF, {@code #} comments, single and double quotes, and
 * an optional {@code export} prefix. Real environment variables always win over file values.
 */
public final class DotEnv {
    /** The five basis-auth variables. Login stays off until all are present. */
    public static final List<String> BASIS_VARS =
            List.of(
                    "BASIS_AUTH_ISSUER",
                    "BASIS_REDIRECT_URI",
                    "BASIS_CLIENT_ID",
                    "BASIS_CLIENT_SECRET",
                    "BASIS_RESOURCE");

    /**
     * Canonical basis-auth names. Each is an alias for a {@link #BASIS_VARS} entry; either spelling
     * satisfies the same requirement. CURRENT_URL_ORIGIN derives the callback when no redirect URI
     * is set.
     */
    public static final List<String> BASIS_ALIAS_VARS =
            List.of(
                    "BASIS_AUTH_ISSUER",
                    "BASIS_AUTH_CLIENT_ID",
                    "BASIS_AUTH_CLIENT_SECRET",
                    "BASIS_AUTH_RESOURCE",
                    "BASIS_AUTH_RESOURCE_SECRET",
                    "BASIS_AUTH_INTERNAL_URL",
                    "BASIS_AUTH_COOKIE_SECRET",
                    "CURRENT_URL_ORIGIN");

    private DotEnv() {}

    /**
     * Returns the legacy variable satisfied by a canonical alias, or null when the key has no
     * legacy counterpart.
     */
    public static String legacyCounterpart(String key) {
        if (key == null) return null;
        switch (key) {
            case "BASIS_AUTH_ISSUER":
                return "BASIS_AUTH_ISSUER";
            case "BASIS_AUTH_CLIENT_ID":
                return "BASIS_CLIENT_ID";
            case "BASIS_AUTH_CLIENT_SECRET":
                return "BASIS_CLIENT_SECRET";
            case "BASIS_AUTH_RESOURCE":
                return "BASIS_RESOURCE";
            case "CURRENT_URL_ORIGIN":
                return "BASIS_REDIRECT_URI";
            default:
                return null;
        }
    }

    /** Reports whether a legacy variable, or any of its canonical aliases, has a value. */
    public static boolean basisPresent(Map<String, String> env, String legacyKey) {
        String value = env.get(legacyKey);
        if (value != null && !value.isBlank()) {
            return true;
        }
        for (int index = 0; index < BASIS_ALIAS_VARS.size(); index++) {
            String alias = BASIS_ALIAS_VARS.get(index);
            if (legacyKey.equals(legacyCounterpart(alias))) {
                String aliasValue = env.get(alias);
                if (aliasValue != null && !aliasValue.isBlank()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Parses a {@code .env} file. Missing files yield an empty map.
     *
     * @param file the file to parse
     * @return the entries, insertion ordered
     */
    public static Map<String, String> parse(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return Map.of();
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            // A UTF-8 BOM would otherwise attach itself to the first key.
            if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
                text = text.substring(1);
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (String raw : text.split("\r?\n")) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("export ")) {
                    line = line.substring("export ".length()).trim();
                }
                int equals = line.indexOf('=');
                if (equals <= 0) {
                    continue;
                }
                String key = line.substring(0, equals).trim();
                // NOTE: key is never empty here: equals > 0 plus the trimmed line means the
                // substring holds a non-blank first character, so no isEmpty check is needed.
                if (key.contains(" ") || key.contains("\t")) {
                    continue;
                }
                String value = stripInlineComment(line.substring(equals + 1).trim());
                values.put(key, unquote(value));
            }
            return Collections.unmodifiableMap(values);
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot read " + file, failure);
        }
    }

    /**
     * Merges file values under the real environment: real env wins, file fills the gaps.
     *
     * @param fileValues the parsed file
     * @param environment the real environment, usually {@link System#getenv()}
     * @return the merged map
     */
    public static Map<String, String> withRealEnvWins(
            Map<String, String> fileValues, Map<String, String> environment) {
        Map<String, String> merged = new LinkedHashMap<>(fileValues);
        merged.putAll(environment);
        return Collections.unmodifiableMap(merged);
    }

    /**
     * Returns the missing BASIS_* names from an effective environment.
     *
     * @param effectiveEnv the merged env
     * @return the missing names, empty when login can run
     */
    public static List<String> missingBasisVars(Map<String, String> effectiveEnv) {
        List<String> missing = new ArrayList<>();
        for (String key : BASIS_VARS) {
            if (!basisPresent(effectiveEnv, key)) {
                missing.add(key);
            }
        }
        return missing;
    }

    private static String stripInlineComment(String value) {
        boolean single = false;
        boolean doubled = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\'' && !doubled) {
                single = !single;
            } else if (c == '"' && !single) {
                doubled = !doubled;
            } else if (c == '#' && !single && !doubled) {
                if (i > 0 && Character.isWhitespace(value.charAt(i - 1))) {
                    return value.substring(0, i).trim();
                }
            }
        }
        return value;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
