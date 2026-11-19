/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.spi;

import dev.jahaziel.plugin.PluginDescriptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Linux process strategy: bash shell, setsid isolation, POSIX path and {@code /proc}. */
public final class LinuxProcessEnvironment extends ProcessEnvironment {
    private final String shell;
    private final String setsid;

    public LinuxProcessEnvironment() {
        this(Files.isExecutable(Paths.get("/bin/bash")) ? "/bin/bash" : "/bin/sh", findSetsid());
    }

    LinuxProcessEnvironment(String shell, String setsid) {
        super(
                new PluginDescriptor(
                        "process-env-linux",
                        "1.0",
                        new ArrayList<String>(),
                        new ArrayList<String>()));
        this.shell = shell;
        this.setsid = setsid;
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public String pathValue() {
        return "/usr/local/bin:/usr/bin:/bin";
    }

    @Override
    public Path processRoot() {
        return Paths.get("/proc");
    }

    @Override
    protected List<String> shellPrefix() {
        List<String> prefix = new ArrayList<String>();
        prefix.add(shell);
        prefix.add("-i");
        return prefix;
    }

    @Override
    protected List<String> sessionPrefix() {
        if (setsid == null) {
            throw new IllegalStateException("workspace processes require the Linux setsid utility");
        }
        List<String> prefix = new ArrayList<String>();
        prefix.add(setsid);
        prefix.add("--");
        return prefix;
    }

    private static String findSetsid() {
        List<Path> candidates =
                Arrays.asList(Paths.get("/usr/bin/setsid"), Paths.get("/bin/setsid"));
        for (Path candidate : candidates) {
            if (Files.isExecutable(candidate)) {
                return candidate.toString();
            }
        }
        return null;
    }
}
