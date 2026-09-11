/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.api;

import dev.jahaziel.platform.domain.DomainEvent;
import java.util.function.Consumer;

/** In-process domain event bus. Cross-node delivery may arrive behind this abstraction later. */
public interface DomainEventBus {
  /**
   * Publishes one event to all matching subscribers.
   *
   * @param event the event to publish
   */
  void publish(DomainEvent event);

  /**
   * Subscribes to one event type, including its subtypes.
   *
   * @param eventType the event type to receive
   * @param handler the handler to invoke
   * @param <T> the event type
   */
  <T extends DomainEvent> void subscribe(Class<T> eventType, Consumer<T> handler);
}
