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
public final class Notification {

    private final String recipient;

    private final String subject;

    private final String body;

    public Notification(String recipient, String subject, String body) {
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("recipient must not be blank");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("body must not be blank");
        }
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
    }

    public String recipient() {
        return this.recipient;
    }

    public String getRecipient() {
        return recipient;
    }

    public String subject() {
        return this.subject;
    }

    public String getSubject() {
        return subject;
    }

    public String body() {
        return this.body;
    }

    public String getBody() {
        return body;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Notification)) return false;
        Notification other = (Notification) o;
        return (java.util.Objects.equals(this.recipient, other.recipient))
                && java.util.Objects.equals(this.subject, other.subject)
                && java.util.Objects.equals(this.body, other.body);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(this.recipient, this.subject, this.body);
    }

    @Override
    public String toString() {
        return "Notification["
                + "recipient="
                + this.recipient
                + ", "
                + "subject="
                + this.subject
                + ", "
                + "body="
                + this.body
                + "]";
    }
}
