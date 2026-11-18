/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.agent.support;

import dev.jahaziel.agenttools.api.AgentToolService;
import dev.jahaziel.identity.domain.UserId;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Test double for the managed OpenCode tool. It keeps every directory under its data root. */
public final class FakeAgentToolService implements AgentToolService {
    private final Path dataRoot;
    private String status = "INSTALLED";
    private int prepared;

    public FakeAgentToolService(Path dataRoot) {
        this.dataRoot = dataRoot.toAbsolutePath().normalize();
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int preparedCount() {
        return prepared;
    }

    @Override
    public List<ToolStatus> catalog(UserId userId) {
        List<ToolStatus> result = new ArrayList<ToolStatus>();
        result.add(new ToolStatus("opencode", "OpenCode", "1.19.5", "x64", status, 0, 0, 0, ""));
        return result;
    }

    @Override
    public ToolStatus installOpenCode(UserId userId) {
        status = "INSTALLED";
        return catalog(userId).get(0);
    }

    @Override
    public ToolStatus updateOpenCode(UserId userId) {
        return installOpenCode(userId);
    }

    @Override
    public ToolStatus removeOpenCode(UserId userId) {
        status = "NOT_INSTALLED";
        return catalog(userId).get(0);
    }

    @Override
    public Path executableDirectory(UserId userId) {
        return userRoot(userId).resolve("bin");
    }

    @Override
    public Path profileDirectory(UserId userId) {
        return userRoot(userId).resolve("profile");
    }

    @Override
    public void prepareProfile(UserId userId) {
        prepared++;
        try {
            Files.createDirectories(executableDirectory(userId));
            Files.createDirectories(profileDirectory(userId));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private Path userRoot(UserId userId) {
        return dataRoot.resolve("users").resolve(userId.value().toString());
    }
}
