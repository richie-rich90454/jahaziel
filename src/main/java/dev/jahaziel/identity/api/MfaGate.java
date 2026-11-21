/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.api;

import dev.jahaziel.identity.domain.UserId;

/** Shared MFA lookup installed by the identity layer for administrator entry points. */
public interface MfaGate {
    /** Returns whether multi-factor authentication is enabled for the user. */
    boolean enabled(UserId user);

    /** Returns the installed gate, or null when no application context is present. */
    static MfaGate current() {
        return Holder.current;
    }

    /** Installs the gate (null is allowed for direct unit construction). */
    static void install(MfaGate gate) {
        Holder.current = gate;
    }

    /** Holder keeps the mutable reference out of the interface constant pool. */
    final class Holder {
        private static volatile MfaGate current;

        private Holder() {}
    }
}
