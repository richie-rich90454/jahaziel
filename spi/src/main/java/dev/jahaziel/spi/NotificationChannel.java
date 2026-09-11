/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

/** Sends messages to users through one channel. */
@JahazielSpi(version = 1)
public interface NotificationChannel {
  /**
   * Sends one notification.
   *
   * @param notification the message to send
   */
  void send(Notification notification);
}
