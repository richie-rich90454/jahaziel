/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.domain;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Who may do what on a workspace. The owner holds every permission implicitly.
 *
 * @param grants grantee user identifiers to permission names
 */
public final class AccessControlList {
    private final Map<UUID, Set<String>> grants;

    public AccessControlList(Map<UUID, Set<String>> grants) {
        Objects.requireNonNull(grants, "grants");
        Map<UUID, Set<String>> copy = new HashMap<>();
        for (Map.Entry<UUID, Set<String>> entry : grants.entrySet()) {
            copy.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        grants = Map.copyOf(copy);

        this.grants = grants;
    }

    public Map<UUID, Set<String>> grants() {
        return grants;
    }

    public Map<UUID, Set<String>> getGrants() {
        return grants;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccessControlList other = (AccessControlList) o;
        return Objects.equals(grants, other.grants);
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + Objects.hashCode(grants);
        return result;
    }

    @Override
    public String toString() {
        return "AccessControlList{" + "grants=" + grants + '}';
    }

    /** Returns an empty access control list. */
    public static AccessControlList empty() {
        return new AccessControlList(Map.of());
    }

    /**
     * Checks a grant.
     *
     * @param user the grantee
     * @param permission the permission name
     * @return true when granted
     */
    public boolean grants(UUID user, String permission) {
        return grants.getOrDefault(user, Set.of()).contains(permission);
    }

    /**
     * Returns a copy with one grant added.
     *
     * @param user the grantee
     * @param permission the permission name
     * @return the updated list
     */
    public AccessControlList withGrant(UUID user, String permission) {
        Map<UUID, Set<String>> copy = new HashMap<>(grants);
        Set<String> permissions = new HashSet<>(copy.getOrDefault(user, Set.of()));
        permissions.add(permission);
        copy.put(user, Set.copyOf(permissions));
        return new AccessControlList(copy);
    }

    /**
     * Returns a copy with one grant removed.
     *
     * @param user the grantee
     * @param permission the permission name
     * @return the updated list
     */
    public AccessControlList withoutGrant(UUID user, String permission) {
        Map<UUID, Set<String>> copy = new HashMap<>(grants);
        Set<String> permissions = new HashSet<>(copy.getOrDefault(user, Set.of()));
        permissions.remove(permission);
        if (permissions.isEmpty()) {
            copy.remove(user);
        } else {
            copy.put(user, Set.copyOf(permissions));
        }
        return new AccessControlList(copy);
    }
}
