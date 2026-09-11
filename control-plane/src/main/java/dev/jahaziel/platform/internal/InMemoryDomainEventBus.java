/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.internal;

import dev.jahaziel.platform.api.DomainEventBus;
import dev.jahaziel.platform.domain.DomainEvent;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Synchronous in-process event bus. A handler for a type receives all its subtypes. */
public final class InMemoryDomainEventBus implements DomainEventBus {
  private final ConcurrentMap<Class<?>, List<Consumer<?>>> handlers = new ConcurrentHashMap<>();

  @Override
  public void publish(DomainEvent event) {
    Objects.requireNonNull(event, "event");
    handlers.forEach(
        (type, subscribed) -> {
          if (type.isInstance(event)) {
            for (Consumer<?> handler : subscribed) {
              // Safe: the handler registered for type, and type matches the event.
              @SuppressWarnings("unchecked")
              Consumer<DomainEvent> typed = (Consumer<DomainEvent>) handler;
              typed.accept(event);
            }
          }
        });
  }

  @Override
  public <T extends DomainEvent> void subscribe(Class<T> eventType, Consumer<T> handler) {
    Objects.requireNonNull(eventType, "eventType");
    Objects.requireNonNull(handler, "handler");
    handlers.computeIfAbsent(eventType, key -> new CopyOnWriteArrayList<>()).add(handler);
  }
}
