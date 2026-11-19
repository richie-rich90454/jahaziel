/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.agent.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.jahaziel.agent.AgentException;
import dev.jahaziel.agent.AgentStore;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.workspace.domain.WorkspaceId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

class AgentChatServiceTest {
    @TempDir Path temporary;

    private final UserId user = new UserId(UUID.randomUUID());
    private final WorkspaceId workspace = new WorkspaceId(UUID.randomUUID());

    @Test
    void chatHistoryCapturesEditorContextAndCancelAction() {
        AgentChatService service = service();

        List<ChatMessage> history =
                service.send(user, workspace, "explain this", "src/App.java", "selected line");
        assertEquals(1, history.size());
        assertEquals("USER", history.get(0).getRole());
        assertEquals("src/App.java", history.get(0).getActiveFile());
        assertEquals("selected line", history.get(0).getSelection());

        service.appendAgent(user, workspace, "it renders the app");
        assertEquals(2, service.history(user, workspace).size());
        assertEquals("AGENT", service.history(user, workspace).get(1).getRole());

        service.cancel(user, workspace);
        assertTrue(service.isCancelled(user, workspace));
        service.send(user, workspace, "try again", null, null);
        assertFalse(service.isCancelled(user, workspace));
    }

    @Test
    void activeFileMustStayInsideTheWorkspace() {
        final AgentChatService service = service();
        assertThrowsAgent(
                new Executable() {
                    @Override
                    public void execute() {
                        service.send(user, workspace, "read this", "../secrets.txt", null);
                    }
                });
    }

    @Test
    void historyAndCancelFlagStayUnderTheDataDirectory() {
        Path data = dataPath();
        AgentChatService service = service();
        service.send(user, workspace, "hi", null, null);
        service.cancel(user, workspace);

        Path scope =
                data.resolve("agent")
                        .resolve("chat")
                        .resolve(user.value().toString())
                        .resolve(workspace.value().toString());
        assertTrue(Files.isRegularFile(scope.resolve("history.json")));
        assertTrue(Files.isRegularFile(scope.resolve("cancel.flag")));
        assertTrue(scope.toAbsolutePath().startsWith(data.toAbsolutePath()));
    }

    @Test
    void noProviderCredentialIsAcceptedOrStored() {
        Field[] fields = AgentChatService.class.getDeclaredFields();
        for (Field field : fields) {
            assertFalse(mentionsCredential(field.getName()));
        }
        Method[] methods = AgentChatService.class.getDeclaredMethods();
        for (Method method : methods) {
            assertFalse(mentionsCredential(method.getName()));
        }
    }

    private AgentChatService service() {
        return new AgentChatService(new AgentStore(dataPath(), new ObjectMapper()));
    }

    private Path dataPath() {
        try {
            return temporary.toRealPath().resolve("data");
        } catch (IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private static boolean mentionsCredential(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("key")
                || lower.contains("token")
                || lower.contains("secret")
                || lower.contains("password")
                || lower.contains("credential");
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
