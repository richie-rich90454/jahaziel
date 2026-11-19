/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.spi;

import dev.jahaziel.platform.Platform;

/** Selects the process strategy for a detected operating system. */
public final class ProcessEnvironments {
    private ProcessEnvironments() {}

    /**
     * Returns the strategy for {@code os}.
     *
     * @param os the detected operating system
     * @return the matching process environment
     * @throws IllegalStateException when no strategy exists for the host
     */
    public static ProcessEnvironment forOs(Platform.Os os) {
        if (os == Platform.Os.LINUX) {
            return new LinuxProcessEnvironment();
        }
        if (os == Platform.Os.MACOS) {
            return new MacProcessEnvironment();
        }
        if (os == Platform.Os.WINDOWS) {
            return new WindowsProcessEnvironment();
        }
        throw new IllegalStateException("unsupported process environment: os=" + os);
    }
}
