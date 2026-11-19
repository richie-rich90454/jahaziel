/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.club.moderation;

import dev.jahaziel.club.api.ClubAccessDeniedException;
import dev.jahaziel.club.api.ClubLimitException;
import dev.jahaziel.club.domain.ClubRoles;
import dev.jahaziel.club.domain.ClubSession;
import dev.jahaziel.identity.domain.UserId;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Session moderation, chat persistence, and the help queue.
 *
 * <p>Chat is appended to {@code data/club/sessions/{id}/chat.db} as bounded delimiter-separated
 * records. Mute, pin, lock, removal, and help-queue actions all require session staff. Observers
 * are read-only and cannot post or join the help queue.
 */
public final class ModerationService {
    private static final char FIELD = '\u001f';
    private static final int MAX_MESSAGE_LENGTH = 2000;

    private final Path clubRoot;
    private final Map<String, List<ChatMessage>> messages =
            new HashMap<String, List<ChatMessage>>();
    private final Map<String, List<UserId>> helpQueues = new HashMap<String, List<UserId>>();

    /**
     * Creates the moderation service.
     *
     * @param clubRoot the club data root, usually {@code data/club}
     */
    public ModerationService(Path clubRoot) {
        this.clubRoot = Objects.requireNonNull(clubRoot, "clubRoot").toAbsolutePath().normalize();
    }

    /**
     * Posts a chat message.
     *
     * @param session the session
     * @param user the posting user
     * @param text the message text
     * @return the stored message
     */
    public ChatMessage post(ClubSession session, UserId user, String text) {
        Objects.requireNonNull(session, "session");
        if (session.isLocked()) {
            throw new ClubLimitException("session is locked");
        }
        if (!session.mayWrite(user)) {
            throw new ClubAccessDeniedException("user may not post in this session");
        }
        if (session.participant(user).isMuted()) {
            throw new ClubAccessDeniedException("user is muted");
        }
        if (text == null || text.trim().isEmpty() || text.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException(
                    "message must be 1 to " + MAX_MESSAGE_LENGTH + " characters");
        }
        ChatMessage message =
                new ChatMessage(
                        UUID.randomUUID().toString(),
                        session.id(),
                        user,
                        text,
                        Instant.now(),
                        false);
        Path file = chatFile(session.id());
        String record =
                message.id()
                        + FIELD
                        + message.sessionId()
                        + FIELD
                        + message.user().value().toString()
                        + FIELD
                        + message.submittedAt().toEpochMilli()
                        + FIELD
                        + "0"
                        + FIELD
                        + message.text()
                        + "\n";
        try {
            Files.createDirectories(file.getParent());
            Files.write(
                    file,
                    record.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot persist club chat", failure);
        }
        messagesFor(session.id()).add(message);
        return message;
    }

    /**
     * Returns the persisted chat history for a participant.
     *
     * @param session the session
     * @param user the reading participant
     * @return an unmodifiable list of messages
     */
    public List<ChatMessage> history(ClubSession session, UserId user) {
        requireMember(session, user);
        List<ChatMessage> result = new ArrayList<ChatMessage>();
        Path file = chatFile(session.id());
        if (!Files.isRegularFile(file)) {
            return Collections.unmodifiableList(result);
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int index = 0; index < lines.size(); index++) {
                ChatMessage parsed = parse(lines.get(index));
                if (parsed != null) {
                    result.add(parsed);
                }
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot read club chat", failure);
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Mutes a participant.
     *
     * @param session the session
     * @param actor the acting staff member
     * @param target the participant to mute
     */
    public void mute(ClubSession session, UserId actor, UserId target) {
        requireStaff(session, actor);
        ClubSession.Participant participant = session.participant(target);
        if (participant == null) {
            throw new IllegalArgumentException("target has not joined this session");
        }
        participant.setMuted(true);
    }

    /**
     * Unmutes a participant.
     *
     * @param session the session
     * @param actor the acting staff member
     * @param target the participant to unmute
     */
    public void unmute(ClubSession session, UserId actor, UserId target) {
        requireStaff(session, actor);
        ClubSession.Participant participant = session.participant(target);
        if (participant == null) {
            throw new IllegalArgumentException("target has not joined this session");
        }
        participant.setMuted(false);
    }

    /**
     * Pins a message so it appears in the pinned view.
     *
     * @param session the session
     * @param actor the acting staff member
     * @param messageId the message identifier
     */
    public void pin(ClubSession session, UserId actor, String messageId) {
        requireStaff(session, actor);
        ChatMessage found = findMessage(session.id(), messageId);
        if (found == null) {
            throw new IllegalArgumentException("unknown chat message: " + messageId);
        }
        found.setPinned(true);
    }

    /**
     * Returns pinned messages for a participant.
     *
     * @param session the session
     * @param user the reading participant
     * @return an unmodifiable list of pinned messages
     */
    public List<ChatMessage> pinned(ClubSession session, UserId user) {
        requireMember(session, user);
        List<ChatMessage> result = new ArrayList<ChatMessage>();
        List<ChatMessage> current = messagesFor(session.id());
        for (int index = 0; index < current.size(); index++) {
            if (current.get(index).isPinned()) {
                result.add(current.get(index));
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Locks a session. No joins, posts, forks, or submissions are allowed afterwards.
     *
     * @param session the session
     * @param actor the acting staff member
     */
    public void lock(ClubSession session, UserId actor) {
        requireStaff(session, actor);
        session.lock();
    }

    /**
     * Unlocks a session.
     *
     * @param session the session
     * @param actor the acting staff member
     */
    public void unlock(ClubSession session, UserId actor) {
        requireStaff(session, actor);
        session.unlock();
    }

    /**
     * Removes a participant.
     *
     * @param session the session
     * @param actor the acting staff member
     * @param target the participant to remove
     */
    public void remove(ClubSession session, UserId actor, UserId target) {
        requireStaff(session, actor);
        if (!session.remove(target)) {
            throw new IllegalArgumentException("target has not joined this session");
        }
    }

    /**
     * Adds a user to the help queue.
     *
     * @param session the session
     * @param user the participant asking for help
     */
    public void joinHelpQueue(ClubSession session, UserId user) {
        requireMember(session, user);
        if (ClubRoles.OBSERVER.equals(session.roleOf(user))) {
            throw new ClubAccessDeniedException("observers cannot join the help queue");
        }
        if (session.isLocked()) {
            throw new ClubAccessDeniedException("session is locked");
        }
        List<UserId> queue = helpQueue(session.id());
        if (!queue.contains(user)) {
            queue.add(user);
        }
    }

    /**
     * Removes a user from the help queue.
     *
     * @param session the session
     * @param user the participant
     */
    public void leaveHelpQueue(ClubSession session, UserId user) {
        helpQueue(session.id()).remove(user);
    }

    /**
     * Serves the next user in the help queue.
     *
     * @param session the session
     * @param actor the acting staff member
     * @return the next user
     * @throws IllegalStateException when the queue is empty
     */
    public UserId nextHelp(ClubSession session, UserId actor) {
        requireStaff(session, actor);
        List<UserId> queue = helpQueue(session.id());
        if (queue.isEmpty()) {
            throw new IllegalStateException("help queue is empty");
        }
        return queue.remove(0);
    }

    /**
     * Returns the help queue size.
     *
     * @param session the session
     * @return the number of waiting users
     */
    public int helpQueueSize(ClubSession session) {
        return helpQueue(session.id()).size();
    }

    /** Returns the chat file path for a session. */
    public Path chatFile(String sessionId) {
        if (sessionId == null || !sessionId.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("invalid session id");
        }
        return clubRoot.resolve("sessions").resolve(sessionId).resolve("chat.db");
    }

    private List<ChatMessage> messagesFor(String sessionId) {
        if (!messages.containsKey(sessionId)) {
            messages.put(sessionId, new ArrayList<ChatMessage>());
        }
        return messages.get(sessionId);
    }

    private List<UserId> helpQueue(String sessionId) {
        if (!helpQueues.containsKey(sessionId)) {
            helpQueues.put(sessionId, new ArrayList<UserId>());
        }
        return helpQueues.get(sessionId);
    }

    private ChatMessage findMessage(String sessionId, String messageId) {
        List<ChatMessage> current = messagesFor(sessionId);
        for (int index = 0; index < current.size(); index++) {
            if (current.get(index).id().equals(messageId)) {
                return current.get(index);
            }
        }
        for (ChatMessage message : historyById(sessionId)) {
            if (message.id().equals(messageId)) {
                current.add(message);
                return message;
            }
        }
        return null;
    }

    private List<ChatMessage> historyById(String sessionId) {
        List<ChatMessage> result = new ArrayList<ChatMessage>();
        Path file = chatFile(sessionId);
        if (!Files.isRegularFile(file)) {
            return result;
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int index = 0; index < lines.size(); index++) {
                ChatMessage parsed = parse(lines.get(index));
                if (parsed != null) {
                    result.add(parsed);
                }
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot read club chat", failure);
        }
        return result;
    }

    private static ChatMessage parse(String line) {
        String[] parts = line.split(String.valueOf(FIELD), -1);
        if (parts.length < 6) {
            return null;
        }
        try {
            UserId user = new UserId(UUID.fromString(parts[2]));
            Instant instant = Instant.ofEpochMilli(Long.parseLong(parts[3]));
            boolean pinned = "1".equals(parts[4]);
            return new ChatMessage(parts[0], parts[1], user, parts[5], instant, pinned);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    private static void requireStaff(ClubSession session, UserId actor) {
        if (!session.isStaff(actor)) {
            throw new ClubAccessDeniedException("only session staff may moderate");
        }
    }

    private static void requireMember(ClubSession session, UserId user) {
        if (session.roleOf(user) == null) {
            throw new ClubAccessDeniedException("user has not joined this session");
        }
    }

    /** One immutable chat message, except for the staff-controlled pinned flag. */
    public static final class ChatMessage {
        private final String id;
        private final String sessionId;
        private final UserId user;
        private final String text;
        private final Instant submittedAt;
        private boolean pinned;

        /**
         * Creates a message.
         *
         * @param id the message identifier
         * @param sessionId the session identifier
         * @param user the author
         * @param text the bounded text
         * @param submittedAt the timestamp
         * @param pinned whether the message is pinned
         */
        public ChatMessage(
                String id,
                String sessionId,
                UserId user,
                String text,
                Instant submittedAt,
                boolean pinned) {
            this.id = Objects.requireNonNull(id, "id");
            this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
            this.user = Objects.requireNonNull(user, "user");
            this.text = Objects.requireNonNull(text, "text");
            this.submittedAt = Objects.requireNonNull(submittedAt, "submittedAt");
            this.pinned = pinned;
        }

        /** Returns the message identifier. */
        public String id() {
            return id;
        }

        /** Returns the session identifier. */
        public String sessionId() {
            return sessionId;
        }

        /** Returns the author. */
        public UserId user() {
            return user;
        }

        /** Returns the message text. */
        public String text() {
            return text;
        }

        /** Returns the timestamp. */
        public Instant submittedAt() {
            return submittedAt;
        }

        /** Returns whether the message is pinned. */
        public boolean isPinned() {
            return pinned;
        }

        /** Sets the pinned flag. */
        public void setPinned(boolean pinned) {
            this.pinned = pinned;
        }
    }
}
