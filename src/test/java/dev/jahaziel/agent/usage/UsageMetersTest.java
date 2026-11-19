/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.agent.usage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.jahaziel.agent.AgentException;
import dev.jahaziel.agent.AgentStore;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.workspace.domain.WorkspaceId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

class UsageMetersTest {
    @TempDir Path temporary;

    private final UserId user = new UserId(UUID.randomUUID());
    private final UserId other = new UserId(UUID.randomUUID());
    private final WorkspaceId workspace = new WorkspaceId(UUID.randomUUID());

    private UsageMeters meters() {
        return new UsageMeters(new AgentStore(temporary.resolve("data"), new ObjectMapper()));
    }

    @Test
    void countsAccumulatePerUserAndWorkspace() {
        UsageMeters meters = meters();

        meters.record(user, workspace, 10, 5);
        UsageMeters.UsageSnapshot snapshot = meters.record(user, workspace, 20, 7);

        assertEquals(30, snapshot.getTokensIn());
        assertEquals(12, snapshot.getTokensOut());
        assertEquals(2, snapshot.getMessages());
        assertEquals(0, meters.get(other, workspace).getMessages());
        assertEquals(2, meters.getByUser(user).getMessages());
        assertEquals(2, meters.getByWorkspace(workspace).getMessages());
    }

    @Test
    void countersCannotBeNegative() {
        final UsageMeters meters = meters();
        try {
            meters.record(user, workspace, -1, 0);
            fail("expected an AgentException");
        } catch (Throwable failure) {
            assertTrue(failure instanceof AgentException);
        }
    }

    @Test
    void snapshotsStayUnderTheDataDirectory() {
        Path data = temporary.resolve("data");
        UsageMeters meters = meters();
        meters.record(user, workspace, 1, 1);

        Path snapshot =
                data.resolve("agent")
                        .resolve("usage")
                        .resolve(user.value().toString())
                        .resolve(workspace.value().toString() + ".json");
        assertTrue(Files.isRegularFile(snapshot));
        assertTrue(snapshot.toAbsolutePath().startsWith(data.toAbsolutePath()));
    }
}
