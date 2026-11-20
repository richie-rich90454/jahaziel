/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.jahaziel.platform.domain.DomainEvent;
import dev.jahaziel.platform.domain.NodeRegisteredEvent;
import dev.jahaziel.platform.domain.WorkspaceDeletedEvent;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Behavior of {@link InMemoryDomainEventBus}. */
class InMemoryDomainEventBusTest {

    private final Instant at = Instant.now();

    @Test
    void deliversToExactTypeSubscribers() {
        InMemoryDomainEventBus bus = new InMemoryDomainEventBus();
        List<WorkspaceDeletedEvent> received = new ArrayList<>();
        bus.subscribe(
                WorkspaceDeletedEvent.class,
                new Consumer<WorkspaceDeletedEvent>() {
                    @Override
                    public void accept(WorkspaceDeletedEvent event) {
                        received.add(event);
                    }
                });
        WorkspaceDeletedEvent event = new WorkspaceDeletedEvent(UUID.randomUUID(), at);
        bus.publish(event);
        assertEquals(List.of(event), received);
    }

    @Test
    void deliversSubtypesToBaseSubscribers() {
        InMemoryDomainEventBus bus = new InMemoryDomainEventBus();
        List<DomainEvent> received = new ArrayList<>();
        bus.subscribe(
                DomainEvent.class,
                new Consumer<DomainEvent>() {
                    @Override
                    public void accept(DomainEvent event) {
                        received.add(event);
                    }
                });
        NodeRegisteredEvent event = new NodeRegisteredEvent("node-a", at);
        bus.publish(event);
        assertEquals(List.of(event), received);
    }

    @Test
    void ignoresUnrelatedSubscribers() {
        InMemoryDomainEventBus bus = new InMemoryDomainEventBus();
        List<NodeRegisteredEvent> received = new ArrayList<>();
        bus.subscribe(
                NodeRegisteredEvent.class,
                new Consumer<NodeRegisteredEvent>() {
                    @Override
                    public void accept(NodeRegisteredEvent event) {
                        received.add(event);
                    }
                });
        bus.publish(new WorkspaceDeletedEvent(UUID.randomUUID(), at));
        assertEquals(List.of(), received);
    }

    @Test
    void nullsAreRejected() {
        InMemoryDomainEventBus bus = new InMemoryDomainEventBus();
        assertThrows(
                NullPointerException.class,
                new org.junit.jupiter.api.function.Executable() {

                    public void execute() {
                        bus.publish(null);
                    }
                });
        assertThrows(
                NullPointerException.class,
                new org.junit.jupiter.api.function.Executable() {

                    public void execute() {
                        bus.subscribe(WorkspaceDeletedEvent.class, null);
                    }
                });
    }
}
