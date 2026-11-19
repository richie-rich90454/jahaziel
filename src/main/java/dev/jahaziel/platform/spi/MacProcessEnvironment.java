/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.spi;

import dev.jahaziel.plugin.PluginDescriptor;

import java.util.ArrayList;
import java.util.List;

/** macOS process strategy: POSIX shell and path, with no Linux-only session or proc support. */
public final class MacProcessEnvironment extends ProcessEnvironment {
    public MacProcessEnvironment() {
        super(
                new PluginDescriptor(
                        "process-env-mac",
                        "1.0",
                        new ArrayList<String>(),
                        new ArrayList<String>()));
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public String pathValue() {
        return "/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin";
    }

    @Override
    protected List<String> shellPrefix() {
        List<String> prefix = new ArrayList<String>();
        prefix.add("/bin/bash");
        prefix.add("-i");
        return prefix;
    }
}
