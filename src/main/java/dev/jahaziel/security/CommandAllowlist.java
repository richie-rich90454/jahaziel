/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.security;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

/**
 * Maps browser-facing command names onto server-configured executables. The browser never supplies
 * an executable path or a debug adapter outside the configured sets.
 */
public final class CommandAllowlist {

    private final HashMap<String, String> executables = new HashMap<String, String>();
    private final ArrayList<String> debugAdapters = new ArrayList<String>();

    /** Registers a logical command name and the only executable it may resolve to. */
    public void allow(String name, String executable) {
        if (!validName(name)) throw new IllegalArgumentException("invalid command name: " + name);
        if (executable == null || executable.trim().isEmpty()) {
            throw new IllegalArgumentException("executable required for " + name);
        }
        executables.put(name.toLowerCase(Locale.ROOT), executable);
    }

    /** Registers a debug adapter the browser may request. */
    public void allowDebugAdapter(String name) {
        if (!validName(name)) throw new IllegalArgumentException("invalid adapter name: " + name);
        String key = name.toLowerCase(Locale.ROOT);
        if (!debugAdapters.contains(key)) debugAdapters.add(key);
    }

    /** Returns whether a logical command name is configured. */
    public boolean allows(String name) {
        return validName(name) && executables.containsKey(name.toLowerCase(Locale.ROOT));
    }

    /** Returns whether a debug adapter is configured. */
    public boolean allowsDebugAdapter(String name) {
        return validName(name) && debugAdapters.contains(name.toLowerCase(Locale.ROOT));
    }

    /**
     * Builds an argument vector whose executable comes only from configuration.
     *
     * @param name the logical command name
     * @param args browser-supplied arguments, or null for none
     * @return argv with the configured executable at index zero
     */
    public ArrayList<String> argv(String name, ArrayList<String> args) {
        if (!allows(name)) throw new SecurityException("command not allowed: " + name);
        ArrayList<String> argv = new ArrayList<String>();
        argv.add(executables.get(name.toLowerCase(Locale.ROOT)));
        if (args == null) return argv;
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if (arg == null || arg.indexOf('\0') >= 0) {
                throw new SecurityException("invalid argument for " + name);
            }
            argv.add(arg);
        }
        return argv;
    }

    /** Returns an argument vector with no browser-supplied arguments. */
    public ArrayList<String> argv(String name) {
        return argv(name, null);
    }

    /** Returns a ready-made allowlist for the common workbench commands and debug adapters. */
    public static CommandAllowlist browserDefaults() {
        CommandAllowlist list = new CommandAllowlist();
        list.allow("git", "git");
        list.allow("node", "node");
        list.allow("npm", "npm");
        list.allowDebugAdapter("node");
        list.allowDebugAdapter("python");
        return list;
    }

    private static boolean validName(String name) {
        if (name == null || name.isEmpty()) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean alpha = c >= 'a' && c <= 'z';
            boolean upper = c >= 'A' && c <= 'Z';
            boolean digit = c >= '0' && c <= '9';
            if (!alpha && !upper && !digit && c != '.' && c != '_' && c != '-') return false;
        }
        return true;
    }
}
