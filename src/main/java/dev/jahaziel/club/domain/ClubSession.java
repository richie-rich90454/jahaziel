/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.club.domain;

import dev.jahaziel.club.api.ClubAccessDeniedException;
import dev.jahaziel.club.api.ClubLimitException;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.workspace.domain.WorkspaceId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One scheduled Developer Club session.
 *
 * <p>The session owns its roster, capacity, lock state, join code, demo controller, and per-action
 * rate counters. It never owns workspace internals: workspaces are created through the workspace
 * service and only their identifiers are stored here.
 */
public final class ClubSession {
    /** Largest roster the platform allows for one session. */
    public static final int MAX_CAPACITY = 500;

    private static final int JOIN_CODE_LENGTH = 12;

    private final String id;
    private final UserId host;
    private final String template;
    private final Instant startsAt;
    private final Instant endsAt;
    private String joinCode;
    private final int capacity;
    private final List<Participant> participants = new ArrayList<Participant>();
    private final Map<String, Integer> actions = new HashMap<String, Integer>();

    private boolean locked;
    private boolean leaderboardEnabled;
    private boolean demoEnabled;
    private UserId demoController;
    private WorkspaceId demoWorkspace;

    /**
     * Creates a session.
     *
     * @param id the stable session identifier
     * @param host the instructor who owns the session
     * @param template the workspace template name to fork
     * @param startsAt session start
     * @param endsAt session end, strictly after the start
     * @param capacity maximum number of participants
     */
    public ClubSession(
            String id,
            UserId host,
            String template,
            Instant startsAt,
            Instant endsAt,
            int capacity) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        this.host = Objects.requireNonNull(host, "host");
        if (template == null || template.trim().isEmpty()) {
            throw new IllegalArgumentException("template must not be blank");
        }
        this.startsAt = Objects.requireNonNull(startsAt, "startsAt");
        this.endsAt = Objects.requireNonNull(endsAt, "endsAt");
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("endsAt must be after startsAt");
        }
        if (capacity < 1 || capacity > MAX_CAPACITY) {
            throw new IllegalArgumentException("capacity must be between 1 and " + MAX_CAPACITY);
        }
        this.id = id;
        this.template = template;
        this.capacity = capacity;
        this.joinCode =
                UUID.randomUUID().toString().replace("-", "").substring(0, JOIN_CODE_LENGTH);
        this.demoController = host;
    }

    /** Returns the session identifier. */
    public String id() {
        return id;
    }

    /** Returns the instructor who owns the session. */
    public UserId host() {
        return host;
    }

    /** Returns the template name forked for every attendee. */
    public String template() {
        return template;
    }

    /** Returns the scheduled start. */
    public Instant startsAt() {
        return startsAt;
    }

    /** Returns the scheduled end. */
    public Instant endsAt() {
        return endsAt;
    }

    /** Returns the QR join code. */
    public String joinCode() {
        return joinCode;
    }

    /** Returns the roster capacity. */
    public int capacity() {
        return capacity;
    }

    /** Returns whether the session is locked. */
    public boolean isLocked() {
        return locked;
    }

    /** Returns whether a per-person leaderboard is explicitly enabled. */
    public boolean isLeaderboardEnabled() {
        return leaderboardEnabled;
    }

    /** Returns whether a shared demo workspace is configured. */
    public boolean isDemoEnabled() {
        return demoEnabled;
    }

    /** Returns the demo workspace, or null when no demo is configured. */
    public WorkspaceId demoWorkspace() {
        return demoWorkspace;
    }

    /** Returns the single user allowed to drive the demo workspace. */
    public UserId demoController() {
        return demoController;
    }

    /** Enables or disables the optional per-person leaderboard. */
    public void setLeaderboardEnabled(boolean enabled) {
        this.leaderboardEnabled = enabled;
    }

    /** Rotates the QR join code, invalidating previously printed codes. */
    public String rotateJoinCode() {
        this.joinCode =
                UUID.randomUUID().toString().replace("-", "").substring(0, JOIN_CODE_LENGTH);
        return joinCode;
    }

    /**
     * Configures the shared demo workspace.
     *
     * @param workspace the demo workspace identifier
     * @param controller the single user who may drive it
     */
    public void enableDemo(WorkspaceId workspace, UserId controller) {
        this.demoWorkspace = Objects.requireNonNull(workspace, "workspace");
        this.demoController = Objects.requireNonNull(controller, "controller");
        this.demoEnabled = true;
    }

    /** Locks the session against joins, posts, and submissions. */
    public void lock() {
        this.locked = true;
    }

    /** Unlocks the session. */
    public void unlock() {
        this.locked = false;
    }

    /**
     * Adds a participant after role, capacity, and lock checks.
     *
     * @param user the joining user
     * @param role one of the {@link ClubRoles} constants
     * @return the stored participant
     * @throws ClubLimitException when the session is locked or full
     */
    public Participant join(UserId user, String role) {
        Objects.requireNonNull(user, "user");
        ClubRoles.requireKnown(role);
        if (locked) {
            throw new ClubLimitException("session is locked");
        }
        if (participant(user) != null) {
            throw new IllegalArgumentException("user already joined this session");
        }
        if (participants.size() >= capacity) {
            throw new ClubLimitException("session is at capacity");
        }
        Participant participant = new Participant(user, role);
        participants.add(participant);
        return participant;
    }

    /**
     * Removes a participant. The host cannot be removed from their own session.
     *
     * @param user the participant to remove
     * @return true when a participant was removed
     */
    public boolean remove(UserId user) {
        if (user != null && user.equals(host)) {
            throw new ClubAccessDeniedException("the host cannot be removed");
        }
        for (int index = 0; index < participants.size(); index++) {
            if (participants.get(index).user().equals(user)) {
                participants.remove(index);
                return true;
            }
        }
        return false;
    }

    /**
     * Returns one participant, or null when the user has not joined.
     *
     * @param user the user to find
     * @return the participant or null
     */
    public Participant participant(UserId user) {
        if (user == null) {
            return null;
        }
        for (int index = 0; index < participants.size(); index++) {
            if (participants.get(index).user().equals(user)) {
                return participants.get(index);
            }
        }
        return null;
    }

    /** Returns an unmodifiable snapshot of the roster. */
    public List<Participant> participants() {
        return Collections.unmodifiableList(new ArrayList<Participant>(participants));
    }

    /** Returns the current roster size, including the host when they joined. */
    public int size() {
        return participants.size();
    }

    /**
     * Returns the effective role of a user. The host is always an instructor.
     *
     * @param user the user
     * @return the role, or null when the user has not joined
     */
    public String roleOf(UserId user) {
        if (user != null && user.equals(host)) {
            return ClubRoles.INSTRUCTOR;
        }
        Participant participant = participant(user);
        return participant == null ? null : participant.role();
    }

    /**
     * Checks whether a user may moderate the session.
     *
     * @param user the user
     * @return true for the host and staff participants
     */
    public boolean isStaff(UserId user) {
        return ClubRoles.isStaff(roleOf(user));
    }

    /**
     * Checks whether a user may change content in this session.
     *
     * @param user the user
     * @return false for observers, non-members, and while locked
     */
    public boolean mayWrite(UserId user) {
        return !locked && ClubRoles.mayWrite(roleOf(user));
    }

    /**
     * Records a workspace identifier for a participant.
     *
     * @param user the participant
     * @param workspace the isolated workspace
     */
    public void setWorkspace(UserId user, WorkspaceId workspace) {
        Participant participant = participant(user);
        if (participant == null) {
            throw new ClubAccessDeniedException("user has not joined this session");
        }
        participant.setWorkspace(workspace);
    }

    /**
     * Returns the isolated workspace of a participant, or null when none was forked.
     *
     * @param user the participant
     * @return the workspace identifier or null
     */
    public WorkspaceId workspaceOf(UserId user) {
        Participant participant = participant(user);
        return participant == null ? null : participant.workspace();
    }

    /**
     * Applies a per-user rate limit to one action.
     *
     * @param user the acting user
     * @param action the action name
     * @param limit the maximum number of calls kept for this session
     * @return true when the call is within the limit
     */
    public boolean tryAction(UserId user, String action, int limit) {
        Objects.requireNonNull(user, "user");
        if (action == null || action.trim().isEmpty() || limit < 1) {
            throw new IllegalArgumentException("action and positive limit are required");
        }
        String key = user.toString() + ":" + action;
        int count = 1;
        if (actions.containsKey(key)) {
            count = actions.get(key).intValue() + 1;
        }
        if (count > limit) {
            return false;
        }
        actions.put(key, Integer.valueOf(count));
        return true;
    }

    /** Clears all per-action counters. Used when a session is extended. */
    public void resetRateLimits() {
        actions.clear();
    }

    /** One enrolled user of a club session. */
    public static final class Participant {
        private final UserId user;
        private final String role;
        private WorkspaceId workspace;
        private boolean muted;

        /**
         * Creates a participant.
         *
         * @param user the user
         * @param role the club role
         */
        public Participant(UserId user, String role) {
            this.user = Objects.requireNonNull(user, "user");
            this.role = ClubRoles.requireKnown(role);
        }

        /** Returns the user. */
        public UserId user() {
            return user;
        }

        /** Returns the club role. */
        public String role() {
            return role;
        }

        /** Returns the isolated workspace, or null before a fork. */
        public WorkspaceId workspace() {
            return workspace;
        }

        /** Returns whether chat posts are muted. */
        public boolean isMuted() {
            return muted;
        }

        /** Sets the isolated workspace. */
        public void setWorkspace(WorkspaceId workspace) {
            this.workspace = workspace;
        }

        /** Sets the chat mute flag. */
        public void setMuted(boolean muted) {
            this.muted = muted;
        }
    }
}
