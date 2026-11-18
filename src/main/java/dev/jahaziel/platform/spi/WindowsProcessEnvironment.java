/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.spi;

import dev.jahaziel.plugin.PluginDescriptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Windows process strategy: ConPTY-compatible shells and Windows-only environment variables. */
public final class WindowsProcessEnvironment extends ProcessEnvironment {
    public WindowsProcessEnvironment() {
        super(
                new PluginDescriptor(
                        "process-env-windows",
                        "1.0",
                        new ArrayList<String>(),
                        new ArrayList<String>()));
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public String homeVariable() {
        return "USERPROFILE";
    }

    @Override
    public String pathVariable() {
        return "Path";
    }

    @Override
    public String pathValue() {
        return "C:\\Windows\\System32;C:\\Windows;C:\\Windows\\System32\\Wbem";
    }

    @Override
    public boolean supportsPosixEnvironment() {
        return false;
    }

    @Override
    public List<String> shellCommand(List<String> command) {
        Objects.requireNonNull(command, "command");
        List<String> result = new ArrayList<String>();
        if (command.isEmpty()) {
            result.add("powershell.exe");
            result.add("-NoLogo");
            result.add("-NoExit");
            return result;
        }
        result.add("cmd.exe");
        result.add("/C");
        result.addAll(command);
        return result;
    }

    @Override
    protected List<String> shellPrefix() {
        List<String> prefix = new ArrayList<String>();
        prefix.add("cmd.exe");
        prefix.add("/C");
        return prefix;
    }

    @Override
    protected void addDefaults(Map<String, String> environment) {
        environment.put("ComSpec", "C:\\Windows\\System32\\cmd.exe");
        environment.put("SystemRoot", "C:\\Windows");
    }
}
