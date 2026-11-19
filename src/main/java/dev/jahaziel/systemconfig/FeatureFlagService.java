/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.systemconfig;

import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.safety.SafetyPaths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Feature and plugin flags with persisted global, user, and workspace overrides. */
@Component
public final class FeatureFlagService {
    public static final String GLOBAL = "global";
    public static final String USER = "user";
    public static final String WORKSPACE = "workspace";

    private final String storageRoot;

    public FeatureFlagService(@Value("${jahaziel.storage.root:data}") String storageRoot) {
        this.storageRoot = SafetyPaths.root(storageRoot).toString();
    }

    /** Returns global flags and every persisted override. */
    public synchronized Map<String, Object> view() {
        List<Entry> entries = load();
        Map<String, Boolean> globals = new LinkedHashMap<String, Boolean>();
        List<Map<String, String>> overrides = new ArrayList<Map<String, String>>();
        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            if (GLOBAL.equals(entry.scope)) {
                globals.put(entry.name, Boolean.valueOf(entry.enabled));
            } else {
                Map<String, String> row = new LinkedHashMap<String, String>();
                row.put("name", entry.name);
                row.put("scope", entry.scope);
                row.put("target", entry.target);
                row.put("enabled", String.valueOf(entry.enabled));
                overrides.add(row);
            }
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("flags", globals);
        result.put("overrides", overrides);
        return result;
    }

    /** Returns the effective value: workspace, then user, then global, then false. */
    public synchronized boolean isEnabled(String name, UserId user, WorkspaceId workspace) {
        validateName(name);
        Boolean fallback = Boolean.FALSE;
        Boolean userValue = null;
        Boolean workspaceValue = null;
        List<Entry> entries = load();
        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            if (!entry.name.equals(name)) {
                continue;
            }
            if (GLOBAL.equals(entry.scope)) {
                fallback = Boolean.valueOf(entry.enabled);
            } else if (USER.equals(entry.scope)
                    && user != null
                    && entry.target.equals(user.value().toString())) {
                userValue = Boolean.valueOf(entry.enabled);
            } else if (WORKSPACE.equals(entry.scope)
                    && workspace != null
                    && entry.target.equals(workspace.value().toString())) {
                workspaceValue = Boolean.valueOf(entry.enabled);
            }
        }
        if (workspaceValue != null) {
            return workspaceValue.booleanValue();
        }
        if (userValue != null) {
            return userValue.booleanValue();
        }
        return fallback.booleanValue();
    }

    /** Sets a global, user, or workspace override. */
    public synchronized void set(String name, String scope, String target, boolean enabled) {
        validateName(name);
        String normalized = normalizeScope(scope);
        String validatedTarget = validateTarget(normalized, target);
        List<Entry> entries = load();
        Entry found = find(entries, name, normalized, validatedTarget);
        if (found == null) {
            entries.add(new Entry(name, normalized, validatedTarget, enabled));
        } else {
            found.enabled = enabled;
        }
        save(entries);
    }

    /** Removes one override. */
    public synchronized void clear(String name, String scope, String target) {
        validateName(name);
        String normalized = normalizeScope(scope);
        String validatedTarget = validateTarget(normalized, target);
        List<Entry> entries = load();
        Entry found = find(entries, name, normalized, validatedTarget);
        if (found != null) {
            entries.remove(found);
            save(entries);
        }
    }

    private static String normalizeScope(String scope) {
        if (GLOBAL.equals(scope) || USER.equals(scope) || WORKSPACE.equals(scope)) {
            return scope;
        }
        throw new IllegalArgumentException("flag scope is invalid");
    }

    private static String validateTarget(String scope, String target) {
        if (GLOBAL.equals(scope)) {
            return "-";
        }
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("flag target is required");
        }
        try {
            return UUID.fromString(target).toString();
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("flag target must be a user or workspace id");
        }
    }

    private static void validateName(String name) {
        if (name == null || name.isEmpty() || name.length() > 128) {
            throw new IllegalArgumentException("flag name must be 1 to 128 characters");
        }
        if (!(name.charAt(0) >= 'A' && name.charAt(0) <= 'Z')
                && !(name.charAt(0) >= 'a' && name.charAt(0) <= 'z')) {
            throw new IllegalArgumentException("flag name must start with a letter");
        }
        for (int index = 1; index < name.length(); index++) {
            char c = name.charAt(index);
            boolean valid =
                    (c >= 'A' && c <= 'Z')
                            || (c >= 'a' && c <= 'z')
                            || (c >= '0' && c <= '9')
                            || c == '_'
                            || c == '.'
                            || c == '-';
            if (!valid) {
                throw new IllegalArgumentException("flag name contains invalid characters");
            }
        }
    }

    private static Entry find(List<Entry> entries, String name, String scope, String target) {
        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            if (entry.name.equals(name)
                    && entry.scope.equals(scope)
                    && entry.target.equals(target)) {
                return entry;
            }
        }
        return null;
    }

    private List<Entry> load() {
        List<Entry> entries = new ArrayList<Entry>();
        String[] lines = SafetyPaths.readText(file()).split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split(" ", -1);
            if (parts.length < 4) {
                continue;
            }
            entries.add(new Entry(parts[0], parts[1], parts[2], Boolean.parseBoolean(parts[3])));
        }
        return entries;
    }

    private void save(List<Entry> entries) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            text.append(entry.name)
                    .append(' ')
                    .append(entry.scope)
                    .append(' ')
                    .append(entry.target)
                    .append(' ')
                    .append(entry.enabled)
                    .append('\n');
        }
        SafetyPaths.writeText(file(), text.toString());
    }

    private java.nio.file.Path file() {
        return SafetyPaths.flags(storageRoot).resolve("flags.dat");
    }

    private static final class Entry {
        private final String name;
        private final String scope;
        private final String target;
        private boolean enabled;

        private Entry(String name, String scope, String target, boolean enabled) {
            this.name = name;
            this.scope = scope;
            this.target = target;
            this.enabled = enabled;
        }
    }
}
