/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

/**
 * A message to a user through a notification channel.
 *
 * @param recipient who receives the message
 * @param subject the message subject
 * @param body the message body
 */
public record Notification(String recipient, String subject, String body) {
  public Notification {
    if (recipient == null || recipient.isBlank()) {
      throw new IllegalArgumentException("recipient must not be blank");
    }
    if (subject == null || subject.isBlank()) {
      throw new IllegalArgumentException("subject must not be blank");
    }
    if (body == null || body.isBlank()) {
      throw new IllegalArgumentException("body must not be blank");
    }
  }
}
