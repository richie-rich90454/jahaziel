/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * HMAC-signed outbound events with a bounded retry outbox under {@code data/outbox}. Failed
 * deliveries are retried a limited number of times, then moved to a dead-letter directory.
 */
public final class OutboundWebhooks {
    /** One event to deliver. */
    public static final class WebhookEvent {
        private final String id;
        private final String targetUrl;
        private final String eventType;
        private final String payload;
        private final String secret;

        /** Creates an event with a generated id. */
        public WebhookEvent(String targetUrl, String eventType, String payload, String secret) {
            this(UUID.randomUUID().toString(), targetUrl, eventType, payload, secret);
        }

        /** Creates an event with an explicit id. */
        public WebhookEvent(
                String id, String targetUrl, String eventType, String payload, String secret) {
            this.id = id;
            this.targetUrl = targetUrl;
            this.eventType = eventType;
            this.payload = payload;
            this.secret = secret;
        }

        /** Returns the event id. */
        public String id() {
            return id;
        }

        /** Returns the target URL. */
        public String targetUrl() {
            return targetUrl;
        }

        /** Returns the event type. */
        public String eventType() {
            return eventType;
        }

        /** Returns the payload. */
        public String payload() {
            return payload;
        }

        /** Returns the signing secret. */
        public String secret() {
            return secret;
        }
    }

    /** Abstraction over the HTTP transport, replaced by a fake in tests. */
    public abstract static class WebhookTransport {
        /** Sends a signed body, returning the HTTP status. */
        public abstract int send(String url, String body, String signatureHeader);
    }

    /** Delivery counts for one pass. */
    public static final class DeliveryReport {
        private final int delivered;
        private final int failed;
        private final int pending;

        /** Creates a report. */
        public DeliveryReport(int delivered, int failed, int pending) {
            this.delivered = delivered;
            this.failed = failed;
            this.pending = pending;
        }

        /** Returns successfully delivered events. */
        public int delivered() {
            return delivered;
        }

        /** Returns events moved to the dead-letter directory. */
        public int failed() {
            return failed;
        }

        /** Returns events still awaiting retry. */
        public int pending() {
            return pending;
        }
    }

    private final Path outboxDir;
    private final Path deadDir;
    private final Clock clock;

    /** Creates an outbox using the system clock. */
    public OutboundWebhooks(Path dataDir) {
        this(dataDir, Clock.systemUTC());
    }

    /** Creates an outbox with an explicit clock. */
    public OutboundWebhooks(Path dataDir, Clock clock) {
        if (dataDir == null) {
            throw new IllegalArgumentException("dataDir must not be null");
        }
        this.outboxDir = dataDir.toAbsolutePath().normalize().resolve("outbox");
        this.deadDir = this.outboxDir.resolve("dead");
        this.clock = clock;
        OpsFiles.ensureDir(this.outboxDir);
        OpsFiles.ensureDir(this.deadDir);
    }

    /** Returns the outbox directory. */
    public Path outboxDir() {
        return outboxDir;
    }

    /** Returns the dead-letter directory. */
    public Path deadDir() {
        return deadDir;
    }

    /** Persists an event in the outbox for later delivery. */
    public synchronized Path enqueue(WebhookEvent event) {
        if (event == null || event.id() == null || !event.id().matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("event id must be a simple identifier");
        }
        Path file = outboxDir.resolve(safeName(event.id()) + ".evt");
        if (!OpsFiles.contained(outboxDir, file)) {
            throw new IllegalArgumentException("outbox destination escapes data/outbox");
        }
        write(file, event, 0, 0L);
        return file;
    }

    /** Returns the HMAC-SHA256 signature header for a body. */
    public static String sign(String secret, String body) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(
                    new javax.crypto.spec.SecretKeySpec(
                            secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + OpsFiles.hex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException("cannot sign webhook", failure);
        }
    }

    /** Returns the canonical signed body for an event. */
    public static String body(WebhookEvent event) {
        StringBuilder out = new StringBuilder("{\"id\":\"");
        out.append(OpsFiles.jsonEscape(event.id())).append("\"");
        out.append(",\"type\":\"").append(OpsFiles.jsonEscape(event.eventType())).append('"');
        out.append(",\"payload\":\"").append(OpsFiles.jsonEscape(event.payload())).append('"');
        return out.append('}').toString();
    }

    /**
     * Delivers due events with bounded retries.
     *
     * @param transport the HTTP sender
     * @param maxAttempts the maximum attempts before dead-lettering
     * @param backoff the base backoff between attempts
     * @return the delivery counts
     */
    public synchronized DeliveryReport deliverAll(
            WebhookTransport transport, int maxAttempts, Duration backoff) {
        if (transport == null || maxAttempts < 1 || backoff == null) {
            throw new IllegalArgumentException("invalid delivery parameters");
        }
        int delivered = 0;
        int failed = 0;
        int pending = 0;
        List<Path> files = OpsFiles.listFiles(outboxDir);
        for (int i = 0; i < files.size(); i++) {
            Path file = files.get(i);
            if (!file.getFileName().toString().endsWith(".evt")) {
                continue;
            }
            Stored stored = read(file);
            long now = clock.millis();
            if (now < stored.nextAttemptAt) {
                pending++;
                continue;
            }
            String body = body(stored.event);
            int status =
                    transport.send(
                            stored.event.targetUrl(), body, sign(stored.event.secret(), body));
            if (status >= 200 && status < 300) {
                OpsFiles.deleteIfPresent(file);
                delivered++;
            } else {
                int attempts = stored.attempts + 1;
                if (attempts >= maxAttempts) {
                    deadLetter(file);
                    failed++;
                } else {
                    long next = now + backoff.toMillis() * attempts;
                    write(file, stored.event, attempts, next);
                    pending++;
                }
            }
        }
        return new DeliveryReport(delivered, failed, pending);
    }

    /** Returns the number of events currently awaiting delivery. */
    public synchronized int pendingCount() {
        int count = 0;
        List<Path> files = OpsFiles.listFiles(outboxDir);
        for (int i = 0; i < files.size(); i++) {
            if (files.get(i).getFileName().toString().endsWith(".evt")) {
                count++;
            }
        }
        return count;
    }

    private static final class Stored {
        private final WebhookEvent event;
        private final int attempts;
        private final long nextAttemptAt;

        Stored(WebhookEvent event, int attempts, long nextAttemptAt) {
            this.event = event;
            this.attempts = attempts;
            this.nextAttemptAt = nextAttemptAt;
        }
    }

    private void write(Path file, WebhookEvent event, int attempts, long nextAttemptAt) {
        StringBuilder out = new StringBuilder();
        out.append(safeName(event.id())).append('|');
        out.append(encode(event.targetUrl())).append('|');
        out.append(encode(event.eventType())).append('|');
        out.append(encode(event.payload())).append('|');
        out.append(encode(event.secret())).append('|');
        out.append(attempts).append('|');
        out.append(nextAttemptAt).append('\n');
        OpsFiles.writeText(file, out.toString());
    }

    private Stored read(Path file) {
        String text = OpsFiles.readText(file);
        String[] parts = text.split("\\|", -1);
        if (parts.length < 7) {
            throw new IllegalStateException("corrupt outbox event: " + file);
        }
        WebhookEvent event =
                new WebhookEvent(
                        parts[0],
                        decode(parts[1]),
                        decode(parts[2]),
                        decode(parts[3]),
                        decode(parts[4]));
        return new Stored(event, Integer.parseInt(parts[5]), Long.parseLong(parts[6].trim()));
    }

    private void deadLetter(Path file) {
        Path target = deadDir.resolve(file.getFileName().toString());
        if (!OpsFiles.contained(deadDir, target)) {
            throw new IllegalArgumentException("dead-letter destination escapes data/outbox/dead");
        }
        try {
            Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) {
            throw new IllegalStateException("cannot dead-letter event", failure);
        }
    }

    private static String safeName(String id) {
        return id.replace(".", "_").replace("-", "_");
    }

    private static String encode(String value) {
        return Base64.getEncoder()
                .encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value.trim()), StandardCharsets.UTF_8);
    }
}
