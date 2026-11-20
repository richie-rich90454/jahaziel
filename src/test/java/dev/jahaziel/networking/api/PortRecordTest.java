/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.networking.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

/** Validation of {@link PortBinding}. */
class PortRecordTest {
    @Test
    void storesFields() {
        UUID user = UUID.randomUUID();
        UUID workspace = UUID.randomUUID();
        Instant at = Instant.now();
        PortBinding binding = new PortBinding(user, 8080, workspace, "http", at);
        assertEquals(user, binding.userId());
        assertEquals(8080, binding.port());
        assertEquals(workspace, binding.workspaceId());
        assertEquals("http", binding.protocol());
        assertEquals(at, binding.registeredAt());
    }

    @Test
    void guardsInputs() {
        UUID user = UUID.randomUUID();
        UUID workspace = UUID.randomUUID();
        Instant at = Instant.now();
        assertThrows(
                NullPointerException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        new PortBinding(null, 8080, workspace, "http", at);
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        new PortBinding(user, 0, workspace, "http", at);
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        new PortBinding(user, 65536, workspace, "http", at);
                    }
                });
        assertThrows(
                NullPointerException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        new PortBinding(user, 8080, null, "http", at);
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        new PortBinding(user, 8080, workspace, null, at);
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        new PortBinding(user, 8080, workspace, "udp", at);
                    }
                });
        assertThrows(
                NullPointerException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        new PortBinding(user, 8080, workspace, "http", null);
                    }
                });
    }
}
