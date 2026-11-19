/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.systemconfig;

import dev.jahaziel.platform.DotEnv;
import dev.jahaziel.workspace.safety.SafetyPaths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Validated view and editor for the jar-adjacent .env configuration file. */
@Component
public final class SystemConfigService {
    public static final String MASK = "********";

    private static final String[] DENIED_KEYS = {
        "PATH",
        "CLASSPATH",
        "JAVA_TOOL_OPTIONS",
        "JDK_JAVA_OPTIONS",
        "LD_PRELOAD",
        "LD_LIBRARY_PATH",
        "NODE_OPTIONS"
    };

    private final Path jarDir;

    public SystemConfigService(@Value("${jahaziel.storage.root:data}") String storageRoot) {
        Path root = SafetyPaths.root(storageRoot);
        Path parent = root.getParent();
        this.jarDir = parent == null ? root : parent;
    }

    /** Returns the masked configuration view. */
    public synchronized Map<String, String> view() {
        return mask(DotEnv.parse(envFile()));
    }

    /** Validates and merges changes into the .env file, returning the new masked view. */
    public synchronized Map<String, String> update(
            Map<String, String> changes, String requestedPath) {
        Path target = resolveConfigPath(requestedPath);
        Map<String, String> current = new LinkedHashMap<String, String>(DotEnv.parse(target));
        if (changes != null) {
            for (Map.Entry<String, String> entry : changes.entrySet()) {
                String key = validateKey(entry.getKey());
                String value = validateValue(entry.getValue());
                if (MASK.equals(value) && current.containsKey(key)) {
                    continue;
                }
                current.put(key, value);
            }
        }
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, String> entry : current.entrySet()) {
            text.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
        SafetyPaths.writeText(target, text.toString());
        return mask(DotEnv.parse(target));
    }

    /** Returns whether a caller-supplied path stays inside the jar directory. */
    public boolean isInsideJarDir(Path candidate) {
        if (candidate == null) {
            return false;
        }
        Path resolved = candidate.toAbsolutePath().normalize();
        Path base = jarDir.toAbsolutePath().normalize();
        return resolved.startsWith(base)
                && resolved.getParent() != null
                && resolved.getParent().equals(base);
    }

    /** Returns the .env file path this service manages. */
    public Path envFile() {
        return jarDir.resolve(".env");
    }

    private Path resolveConfigPath(String requested) {
        if (requested == null || requested.isBlank() || ".env".equals(requested)) {
            return envFile();
        }
        if (requested.indexOf('/') >= 0
                || requested.indexOf('\\') >= 0
                || requested.equals(".")
                || requested.equals("..")
                || requested.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(
                    "config path must be a file inside the jar directory");
        }
        Path candidate = jarDir.resolve(requested).normalize();
        if (!isInsideJarDir(candidate)) {
            throw new IllegalArgumentException("config path escapes the jar directory");
        }
        return candidate;
    }

    private static String validateKey(String key) {
        if (key == null || key.isEmpty() || key.length() > 128) {
            throw new IllegalArgumentException("config key must be 1 to 128 characters");
        }
        for (int index = 0; index < key.length(); index++) {
            char c = key.charAt(index);
            boolean valid =
                    (c >= 'A' && c <= 'Z')
                            || (c >= 'a' && c <= 'z')
                            || (c >= '0' && c <= '9')
                            || c == '_'
                            || c == '.'
                            || c == '-';
            if (!valid) {
                throw new IllegalArgumentException("config key contains invalid characters");
            }
        }
        char first = key.charAt(0);
        if (!((first >= 'A' && first <= 'Z') || (first >= 'a' && first <= 'z') || first == '_')) {
            throw new IllegalArgumentException("config key must start with a letter or underscore");
        }
        String upper = key.toUpperCase(Locale.ROOT);
        for (int index = 0; index < DENIED_KEYS.length; index++) {
            if (DENIED_KEYS[index].equals(upper)) {
                throw new IllegalArgumentException("config key is not editable");
            }
        }
        return key;
    }

    private static String validateValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("config value is required");
        }
        if (value.length() > 4096) {
            throw new IllegalArgumentException("config value is too long");
        }
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            if (c < 32 && c != '\t') {
                throw new IllegalArgumentException("config value contains a control character");
            }
        }
        return value;
    }

    private static Map<String, String> mask(Map<String, String> values) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        List<String> keys = new ArrayList<String>(values.keySet());
        for (int index = 0; index < keys.size(); index++) {
            String key = keys.get(index);
            String value = values.get(key);
            String upper = key.toUpperCase(Locale.ROOT);
            boolean secret =
                    upper.contains("SECRET")
                            || upper.contains("PASSWORD")
                            || upper.contains("TOKEN")
                            || upper.endsWith("_KEY")
                            || upper.equals("KEY");
            result.put(key, secret && value != null && !value.isEmpty() ? MASK : value);
        }
        return result;
    }
}
