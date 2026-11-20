/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.node.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.jahaziel.node.persistence.NodeEntity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

class NodeDomainCoverageGapTest {
    private Node fresh() {
        return new Node(
                new NodeId("node-a"),
                "Primary node",
                "default",
                new NodeCapacity(4, 8192, 100),
                "sha256:abc");
    }

    @Test
    void nullFieldsRejected() {
        NodeCapacity capacity = new NodeCapacity(4, 8192, 100);
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        new Node(new NodeId("n"), null, "default", capacity, "fp");
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        new Node(new NodeId("n"), "n", null, capacity, "fp");
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        new Node(new NodeId("n"), "n", "default", capacity, null);
                    }
                });
    }

    @Test
    void assignRefusesUnhealthy() {
        Node node = fresh();
        node.markUnhealthy("gone");
        assertThrows(
                IllegalStateException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        node.assign(UUID.randomUUID());
                    }
                });
    }

    @Test
    void degradeGuards() {
        Node draining = fresh();
        draining.drain();
        assertThrows(
                IllegalStateException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        draining.degrade("slow");
                    }
                });
        Node unhealthy = fresh();
        unhealthy.markUnhealthy("gone");
        assertThrows(
                IllegalStateException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        unhealthy.degrade("slow");
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        fresh().degrade(null);
                    }
                });
    }

    @Test
    void doubleDrainThrows() {
        Node node = fresh();
        node.drain();
        assertThrows(
                IllegalStateException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        node.drain();
                    }
                });
    }

    @Test
    void markUnhealthyNullRejected() {
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        fresh().markUnhealthy(null);
                    }
                });
    }

    @Test
    void entityHeartbeatGetter() {
        NodeEntity row = new NodeEntity();
        Instant at = Instant.now();
        row.setLastHeartbeat(at);
        assertEquals(at, row.getLastHeartbeat());
    }
}
