/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.club.domain;

/** Club session roles, kept as string constants so they map directly to the club tables. */
public final class ClubRoles {
    /** Session owner and primary teacher. */
    public static final String INSTRUCTOR = "INSTRUCTOR";

    /** Teaching helper: may moderate and grade but may not curate the showcase. */
    public static final String ASSISTANT = "ASSISTANT";

    /** Regular learner with an isolated workspace. */
    public static final String ATTENDEE = "ATTENDEE";

    /** Read-only observer: no workspace fork, no chat posts, no submissions. */
    public static final String OBSERVER = "OBSERVER";

    private ClubRoles() {}

    /**
     * Checks whether a role is one of the four club roles.
     *
     * @param role the stored role
     * @return true when the role is known
     */
    public static boolean isKnown(String role) {
        return INSTRUCTOR.equals(role)
                || ASSISTANT.equals(role)
                || ATTENDEE.equals(role)
                || OBSERVER.equals(role);
    }

    /**
     * Checks whether a role may moderate a session.
     *
     * @param role the stored role
     * @return true for instructors and assistants
     */
    public static boolean isStaff(String role) {
        return INSTRUCTOR.equals(role) || ASSISTANT.equals(role);
    }

    /**
     * Checks whether a role may change workspace or submission content.
     *
     * @param role the stored role
     * @return false for observers
     */
    public static boolean mayWrite(String role) {
        return isKnown(role) && !OBSERVER.equals(role);
    }

    /**
     * Validates and returns a role.
     *
     * @param role the stored role
     * @return the same role
     * @throws IllegalArgumentException when the role is unknown
     */
    public static String requireKnown(String role) {
        if (!isKnown(role)) {
            throw new IllegalArgumentException("unknown club role: " + role);
        }
        return role;
    }
}
