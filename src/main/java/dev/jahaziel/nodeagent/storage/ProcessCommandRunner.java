/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Runs host commands with ProcessBuilder. */
public final class ProcessCommandRunner extends CommandRunner {
    private static final long COMMAND_TIMEOUT_SECONDS = 30L;

    @Override
    public String run(List<String> command) {
        Objects.requireNonNull(command, "command");
        String commandLine = String.join(" ", command);
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor();
                throw new StorageException(
                        commandLine,
                        -1,
                        "command timed out after " + COMMAND_TIMEOUT_SECONDS + "s");
            }
            int exit = process.exitValue();
            if (exit != 0) {
                throw new StorageException(commandLine, exit, output);
            }
            return output;
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new StorageException(commandLine, failure);
        } catch (IOException failure) {
            throw new StorageException(commandLine, failure);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    @Override
    public boolean exists(Path path) {
        Objects.requireNonNull(path, "path");
        // `test -e` is a shell builtin, not an executable, so ProcessBuilder cannot run it
        // portably; on Windows it always failed. Files.exists is the same check natively.
        return Files.exists(path);
    }
}
