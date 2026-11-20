/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.storage;

import java.nio.file.Path;
import java.util.List;

/** Runs host commands. The seam that lets storage logic be tested without touching the host. */
public abstract class CommandRunner {
    /**
     * Runs one command.
     *
     * @param command the argv
     * @return the trimmed stdout
     * @throws StorageException when the command exits nonzero
     */
    public abstract String run(List<String> command);

    /**
     * Checks a path exists.
     *
     * @param path the path
     * @return true when the path exists
     */
    public abstract boolean exists(Path path);
}
