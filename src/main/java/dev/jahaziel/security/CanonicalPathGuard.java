/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Realpath-based containment check applied before any browser-supplied relative path reaches the
 * filesystem. Rejects traversal, absolute paths, Windows device names, and links that escape root.
 */
public final class CanonicalPathGuard {

    private static final String[] WINDOWS_DEVICES = {
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    };

    private CanonicalPathGuard() {}

    /**
     * Returns whether a candidate stays inside the root after full symlink and junction resolution.
     *
     * @param root the existing containment root
     * @param candidate the relative path supplied by the caller
     * @return true only when the resolved candidate is contained by the resolved root
     */
    public static boolean check(Path root, Path candidate) {
        if (root == null || candidate == null) return false;
        if (candidate.isAbsolute()) return false;

        for (int i = 0; i < candidate.getNameCount(); i++) {
            String name = candidate.getName(i).toString();
            if ("..".equals(name)) return false;
            if (".".equals(name)) continue;
            if (unsafeSegment(name)) return false;
        }

        try {
            Path realRoot = root.toRealPath();
            Path rawCandidate = realRoot.resolve(candidate).normalize();
            Path realCandidate = realpathAllowingMissing(rawCandidate);
            if (realCandidate == null) return false;
            return contained(realRoot, realCandidate);
        } catch (IOException invalidPath) {
            return false;
        } catch (SecurityException denied) {
            return false;
        }
    }

    private static Path realpathAllowingMissing(Path path) throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return path.toRealPath();

        ArrayList<String> missing = new ArrayList<String>();
        Path cursor = path;
        while (cursor != null && !Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)) {
            Path name = cursor.getFileName();
            if (name == null) return null;
            missing.add(0, name.toString());
            cursor = cursor.getParent();
        }
        if (cursor == null) return null;

        Path real = cursor.toRealPath();
        for (int i = 0; i < missing.size(); i++) {
            real = real.resolve(missing.get(i));
        }
        return real;
    }

    private static boolean contained(Path root, Path candidate) {
        try {
            Path relative = root.relativize(candidate);
            if (relative.isAbsolute()) return false;
            for (int i = 0; i < relative.getNameCount(); i++) {
                if ("..".equals(relative.getName(i).toString())) return false;
            }
            return true;
        } catch (IllegalArgumentException differentRoots) {
            return false;
        }
    }

    private static boolean unsafeSegment(String name) {
        if (name == null || name.isEmpty()) return true;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 32) return true;
            if (c == '<' || c == '>' || c == ':' || c == '"' || c == '/'
                    || c == '\\' || c == '|' || c == '?' || c == '*') return true;
        }
        if (name.endsWith(" ") || name.endsWith(".")) return true;

        String upper = name.toUpperCase(Locale.ROOT);
        int dot = upper.indexOf('.');
        String base = dot >= 0 ? upper.substring(0, dot) : upper;
        for (int i = 0; i < WINDOWS_DEVICES.length; i++) {
            if (WINDOWS_DEVICES[i].equals(base)) return true;
        }
        return false;
    }
}
