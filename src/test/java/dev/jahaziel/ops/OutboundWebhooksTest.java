/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Behavior of {@link OutboundWebhooks}: HMAC signing, bounded retries and dead-lettering. */
class OutboundWebhooksTest {
    @TempDir Path temp;

    private static final class RecordingTransport extends OutboundWebhooks.WebhookTransport {
        private final int[] statuses;
        private final List<String> urls = new ArrayList<String>();
        private final List<String> bodies = new ArrayList<String>();
        private final List<String> signatures = new ArrayList<String>();
        private int index;

        RecordingTransport(int[] statuses) {
            this.statuses = statuses;
        }

        @Override
        public int send(String url, String body, String signatureHeader) {
            urls.add(url);
            bodies.add(body);
            signatures.add(signatureHeader);
            int status = statuses[index];
            if (index < statuses.length - 1) {
                index++;
            }
            return status;
        }
    }

    private OutboundWebhooks.WebhookEvent event() {
        return new OutboundWebhooks.WebhookEvent(
                "evt-1",
                "https://hooks.example.test/emit",
                "workspace.created",
                "payload-1",
                "shared-secret");
    }

    private String expectedSignature(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("shared-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + OpsFiles.hex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void signatureIsValidHmacSha256() throws Exception {
        String body = OutboundWebhooks.body(event());
        assertEquals(expectedSignature(body), OutboundWebhooks.sign("shared-secret", body));
    }

    @Test
    void retriesThenDeliversAndClearsOutbox() throws Exception {
        OutboundWebhooks hooks = new OutboundWebhooks(temp.resolve("data"));
        hooks.enqueue(event());
        RecordingTransport transport = new RecordingTransport(new int[] {500, 500, 200});
        for (int i = 0; i < 3; i++) {
            OutboundWebhooks.DeliveryReport report = hooks.deliverAll(transport, 5, Duration.ZERO);
            if (i < 2) {
                assertEquals(1, report.pending());
            } else {
                assertEquals(1, report.delivered());
            }
        }
        assertEquals(3, transport.urls.size());
        assertEquals("https://hooks.example.test/emit", transport.urls.get(0));
        for (int i = 0; i < transport.signatures.size(); i++) {
            assertEquals(expectedSignature(transport.bodies.get(i)), transport.signatures.get(i));
        }
        assertEquals(0, hooks.pendingCount());
    }

    @Test
    void boundedRetryDeadLettersAfterMaxAttempts() {
        OutboundWebhooks hooks = new OutboundWebhooks(temp.resolve("data"));
        hooks.enqueue(event());
        RecordingTransport transport = new RecordingTransport(new int[] {500, 500, 500});
        hooks.deliverAll(transport, 2, Duration.ZERO);
        OutboundWebhooks.DeliveryReport report = hooks.deliverAll(transport, 2, Duration.ZERO);
        assertEquals(1, report.failed());
        assertEquals(2, transport.urls.size());
        assertEquals(0, hooks.pendingCount());
        assertEquals(1, OpsFiles.listFiles(hooks.deadDir()).size());
    }

    @Test
    void backoffDefersDeliveryUntilDue() {
        TestClock clock = new TestClock("2026-01-01T00:00:00Z");
        OutboundWebhooks hooks = new OutboundWebhooks(temp.resolve("data"), clock);
        hooks.enqueue(event());
        RecordingTransport transport = new RecordingTransport(new int[] {500, 200});
        hooks.deliverAll(transport, 5, Duration.ofMinutes(5));
        OutboundWebhooks.DeliveryReport deferred =
                hooks.deliverAll(transport, 5, Duration.ofMinutes(5));
        assertEquals(1, deferred.pending());
        assertEquals(1, transport.urls.size());
        clock.advanceSeconds(300L);
        assertEquals(1, hooks.deliverAll(transport, 5, Duration.ofMinutes(5)).delivered());
        assertEquals(2, transport.urls.size());
        assertTrue(hooks.outboxDir().toAbsolutePath().normalize().startsWith(temp));
    }
}
