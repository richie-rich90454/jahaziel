/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.agent.chat;

import dev.jahaziel.agent.AgentException;
import dev.jahaziel.agent.AgentStore;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Workspace-scoped chat history with active-file and selection context. No provider credential is
 * accepted or persisted by this service.
 */
public final class AgentChatService {
    private static final String USER_ROLE = "USER";
    private static final String AGENT_ROLE = "AGENT";
    private static final int MAX_TEXT_LENGTH = 8000;
    private static final int MAX_SELECTION_LENGTH = 20000;

    private final AgentStore store;
    private final Map<String, Boolean> cancelled = new HashMap<String, Boolean>();

    public AgentChatService(AgentStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /**
     * Appends a user message and clears any previous cancellation request.
     *
     * @param activeFile a workspace-relative path, or null when no file is open
     * @param selection the selected text, or null when nothing is selected
     * @return the updated history
     */
    public List<ChatMessage> send(
            UserId user,
            WorkspaceId workspace,
            String text,
            String activeFile,
            String selection) {
        requireOwner(user, workspace);
        requireText(text);
        if (selection != null && selection.length() > MAX_SELECTION_LENGTH) {
            throw new AgentException("selection exceeds the size limit");
        }
        String file = null;
        if (activeFile != null && !activeFile.isEmpty()) {
            store.resolveWorkspacePath(workspace.value(), activeFile);
            file = activeFile;
        }
        cancelFlag(user, workspace, false);
        synchronized (cancelled) {
            cancelled.remove(scopeId(user, workspace));
        }
        ChatMessage message = message(USER_ROLE, text, file, selection);
        HistoryIndex history = load(user, workspace);
        history.messages.add(message);
        save(user, workspace, history);
        return new ArrayList<ChatMessage>(history.messages);
    }

    /** Appends an agent reply to the same history. */
    public List<ChatMessage> appendAgent(UserId user, WorkspaceId workspace, String text) {
        requireOwner(user, workspace);
        requireText(text);
        HistoryIndex history = load(user, workspace);
        history.messages.add(message(AGENT_ROLE, text, null, null));
        save(user, workspace, history);
        return new ArrayList<ChatMessage>(history.messages);
    }

    /** Returns the full history for one workspace. */
    public List<ChatMessage> history(UserId user, WorkspaceId workspace) {
        requireOwner(user, workspace);
        return new ArrayList<ChatMessage>(load(user, workspace).messages);
    }

    /** Requests cancellation for the current operation in one workspace. */
    public void cancel(UserId user, WorkspaceId workspace) {
        requireOwner(user, workspace);
        cancelFlag(user, workspace, true);
        synchronized (cancelled) {
            cancelled.put(scopeId(user, workspace), Boolean.TRUE);
        }
    }

    /** True when cancellation has been requested and no new message has been sent since. */
    public boolean isCancelled(UserId user, WorkspaceId workspace) {
        requireOwner(user, workspace);
        synchronized (cancelled) {
            if (cancelled.containsKey(scopeId(user, workspace))) {
                return true;
            }
        }
        return store.exists(cancelPath(user, workspace));
    }

    private void cancelFlag(UserId user, WorkspaceId workspace, boolean requested) {
        Path flag = cancelPath(user, workspace);
        if (requested) {
            store.write(flag, Instant.now().toString());
        } else {
            store.delete(flag);
        }
    }

    private static ChatMessage message(
            String role, String text, String activeFile, String selection) {
        ChatMessage message = new ChatMessage();
        message.setId(UUID.randomUUID().toString());
        message.setRole(role);
        message.setText(text);
        message.setActiveFile(activeFile);
        message.setSelection(selection);
        message.setCreatedAt(Instant.now().toString());
        return message;
    }

    private static void requireText(String text) {
        if (text == null || text.isEmpty() || text.length() > MAX_TEXT_LENGTH) {
            throw new AgentException("message must be 1 to 8000 characters");
        }
    }

    private HistoryIndex load(UserId user, WorkspaceId workspace) {
        HistoryIndex history = store.read(historyPath(user, workspace), HistoryIndex.class);
        if (history == null) {
            history = new HistoryIndex();
        }
        if (history.messages == null) {
            history.messages = new ArrayList<ChatMessage>();
        }
        return history;
    }

    private void save(UserId user, WorkspaceId workspace, HistoryIndex history) {
        store.write(historyPath(user, workspace), history);
    }

    private Path scopePath(UserId user, WorkspaceId workspace) {
        return store.containedChild(
            store.chatRoot(), user.value().toString(), workspace.value().toString());
    }

    private Path historyPath(UserId user, WorkspaceId workspace) {
        return store.containedChild(scopePath(user, workspace), "history.json");
    }

    private Path cancelPath(UserId user, WorkspaceId workspace) {
        return store.containedChild(scopePath(user, workspace), "cancel.flag");
    }

    private static String scopeId(UserId user, WorkspaceId workspace) {
        return user.value().toString() + "|" + workspace.value().toString();
    }

    private static void requireOwner(UserId user, WorkspaceId workspace) {
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(workspace, "workspace");
    }

    /** Persisted chat history. */
    public static final class HistoryIndex {
        private List<ChatMessage> messages = new ArrayList<ChatMessage>();

        public List<ChatMessage> getMessages() {
            return messages;
        }

        public void setMessages(List<ChatMessage> messages) {
            this.messages = messages;
        }
    }
}
