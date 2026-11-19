/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.agent.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.jahaziel.agent.AgentException;
import dev.jahaziel.agent.AgentStore;
import dev.jahaziel.agent.support.FakeAgentToolService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.workspace.domain.WorkspaceId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

class AgentSessionServiceTest {
    @TempDir Path temporary;

    private final UserId user = new UserId(UUID.randomUUID());
    private final WorkspaceId workspace = new WorkspaceId(UUID.randomUUID());

    @Test
    void sessionLifecycleIsPersistedPerUserAndWorkspace() {
        AgentSessionService service = service(new FakeAgentToolService(temporary.resolve("data")));

        AgentSession created = service.create(user, workspace, "main");
        assertEquals("CREATED", created.getStatus());
        assertEquals(1, service.list(user, workspace).size());
        assertEquals("opencode", created.getToolId());

        assertEquals("RUNNING", service.resume(user, workspace, created.getId()).getStatus());
        assertEquals("RUNNING", service.restart(user, workspace, created.getId()).getStatus());
        assertEquals("KILLED", service.kill(user, workspace, created.getId()).getStatus());
        assertEquals("KILLED", service.status(user, workspace, created.getId()).getStatus());

        List<String> transcript = service.transcript(user, workspace, created.getId());
        assertEquals(4, transcript.size());
        assertTrue(transcript.get(0).contains("session created"));
    }

    @Test
    void sessionStateStaysInsideTheDataDirectory() {
        Path data = temporary.resolve("data");
        FakeAgentToolService tools = new FakeAgentToolService(data);
        AgentSessionService service = service(tools);

        AgentSession session = service.create(user, workspace, "main");

        Path index =
                data.resolve("agent")
                        .resolve("sessions")
                        .resolve(user.value().toString())
                        .resolve(workspace.value().toString())
                        .resolve("index.json");
        Path transcript = index.getParent().resolve(session.getId()).resolve("transcript.log");
        assertTrue(java.nio.file.Files.isRegularFile(index));
        assertTrue(java.nio.file.Files.isRegularFile(transcript));
        assertTrue(index.toAbsolutePath().startsWith(data.toAbsolutePath()));
        assertTrue(tools.preparedCount() == 1);
    }

    @Test
    void invalidNamesAndMissingInstallationAreRejected() {
        final AgentSessionService service =
                service(new FakeAgentToolService(temporary.resolve("data")));
        assertThrowsAgent(
                new Executable() {
                    @Override
                    public void execute() {
                        service.create(user, workspace, "../escape");
                    }
                });

        final FakeAgentToolService missing = new FakeAgentToolService(temporary.resolve("data"));
        missing.setStatus("NOT_INSTALLED");
        final AgentSessionService blocked = service(missing);
        assertThrowsAgent(
                new Executable() {
                    @Override
                    public void execute() {
                        blocked.create(user, workspace, "main");
                    }
                });
    }

    @Test
    void noPublicMethodAcceptsAnExecutablePathOrCommand() {
        Method[] methods = AgentSessionService.class.getDeclaredMethods();
        for (Method method : methods) {
            Class<?>[] parameters = method.getParameterTypes();
            for (Class<?> parameter : parameters) {
                assertFalse(Path.class.equals(parameter));
                assertFalse(String[].class.equals(parameter));
            }
        }
    }

    private AgentSessionService service(FakeAgentToolService tools) {
        return new AgentSessionService(
                new AgentStore(temporary.resolve("data"), new ObjectMapper()), tools);
    }

    private static void assertThrowsAgent(Executable executable) {
        try {
            executable.execute();
            fail("expected an AgentException");
        } catch (Throwable failure) {
            assertTrue(failure instanceof AgentException);
        }
    }
}
