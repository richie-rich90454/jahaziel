/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform;

import java.nio.file.Path;
import java.util.Map;

/**
 * OS abstraction. All {@code os.name}/{@code os.arch} reads live in {@link Platforms}; every other
 * module talks to this abstraction.
 */
public abstract class Platform {
    /** Supported operating systems. */
    public static final class Os {
        /** Windows and Windows Server. */
        public static final Os WINDOWS = new Os("WINDOWS");

        /** macOS. */
        public static final Os MACOS = new Os("MACOS");

        /** Linux, the production target. */
        public static final Os LINUX = new Os("LINUX");

        private static final Os[] VALUES = {WINDOWS, MACOS, LINUX};

        private final String name;

        private Os(String name) {
            this.name = name;
        }

        /** Returns the constant name. */
        public String name() {
            return name;
        }

        /** Returns all constants. */
        public static Os[] values() {
            return VALUES.clone();
        }
    }

    /** Supported architectures. */
    public static final class Arch {
        /** 64-bit x86. */
        public static final Arch X64 = new Arch("X64");

        /** 64-bit ARM. */
        public static final Arch ARM64 = new Arch("ARM64");

        private static final Arch[] VALUES = {X64, ARM64};

        private final String name;

        private Arch(String name) {
            this.name = name;
        }

        /** Returns the constant name. */
        public String name() {
            return name;
        }

        /** Returns all constants. */
        public static Arch[] values() {
            return VALUES.clone();
        }
    }

    /** Returns the OS. */
    public abstract Os os();

    /** Returns the architecture. */
    public abstract Arch arch();

    /** Returns the directory holding the running jar. */
    public abstract Path jarDir();

    /** Returns the data root ({@code jarDir/data} or {@code --data-dir}). */
    public abstract Path dataDir();

    /** Returns {@code data/tmp}. */
    public abstract Path tempDir();

    /** Returns the PATH separator ({@code ;} on Windows, {@code :} elsewhere). */
    public abstract String pathSeparator();

    /**
     * Converts a path to native separators.
     *
     * @param path the path
     * @return the native string
     */
    public abstract String toNative(Path path);

    /**
     * Converts a path to POSIX separators (forward slashes). Used for JDBC URLs and env values that
     * must survive backslash hosts.
     *
     * @param path the path
     * @return the forward-slash string
     */
    public abstract String toPosix(Path path);

    /**
     * Returns the subprocess environment: the current process env with TEMP/TMP/TMPDIR all pointing
     * at {@code data/tmp}.
     *
     * @return an unmodifiable map
     */
    public abstract Map<String, String> subprocessEnv();
}
