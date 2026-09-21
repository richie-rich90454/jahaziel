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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Behavior of {@link InMemoryDomainEventBus}. */
class InMemoryDomainEventBusTest {
  private final Instant at = Instant.now();

  @Test
  void deliversToExactTypeSubscribers() {
    InMemoryDomainEventBus bus = new InMemoryDomainEventBus();
    List<WorkspaceDeletedEvent> received = new ArrayList<>();
    bus.subscribe(WorkspaceDeletedEvent.class, received::add);
    WorkspaceDeletedEvent event = new WorkspaceDeletedEvent(UUID.randomUUID(), at);
    bus.publish(event);
    assertEquals(List.of(event), received);
  }

  @Test
  void deliversSubtypesToBaseSubscribers() {
    InMemoryDomainEventBus bus = new InMemoryDomainEventBus();
    List<DomainEvent> received = new ArrayList<>();
    bus.subscribe(DomainEvent.class, received::add);
    NodeRegisteredEvent event = new NodeRegisteredEvent("node-a", at);
    bus.publish(event);
    assertEquals(List.of(event), received);
  }

  @Test
  void ignoresUnrelatedSubscribers() {
    InMemoryDomainEventBus bus = new InMemoryDomainEventBus();
    List<NodeRegisteredEvent> received = new ArrayList<>();
    bus.subscribe(NodeRegisteredEvent.class, received::add);
    bus.publish(new WorkspaceDeletedEvent(UUID.randomUUID(), at));
    assertEquals(List.of(), received);
  }

  @Test
  void nullsAreRejected() {
    InMemoryDomainEventBus bus = new InMemoryDomainEventBus();
    assertThrows(NullPointerException.class, () -> bus.publish(null));
    assertThrows(
        NullPointerException.class, () -> bus.subscribe(WorkspaceDeletedEvent.class, null));
  }
}
