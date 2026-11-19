/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Verifies that the backup runner actually schedules backups and reports each outcome. */
class BackupSchedulerTest {
    @TempDir Path temp;

    private static final class CapturingTransport extends OutboundWebhooks.WebhookTransport {
        private final List<String> types = new ArrayList<String>();

        @Override
        public int send(String url, String body, String signatureHeader) {
            int start = body.indexOf("\"type\":\"") + 8;
            types.add(body.substring(start, body.indexOf('"', start)));
            return 204;
        }
    }

    @Test
    void scheduledInvocationCreatesArchiveAndSuccessEvent() throws Exception {
        Path data = temp.resolve("data");
        Path source = data.resolve("workspaces");
        OpsFiles.ensureDir(source);
        OpsFiles.writeText(source.resolve("note.txt"), "hello");
        ScheduledBackupService backups = new ScheduledBackupService(data);
        OutboundWebhooks hooks = new OutboundWebhooks(data);
        OpsWebhookListener listener =
                new OpsWebhookListener(hooks, "https://hooks.example.test/ops", "secret");
        ScheduledBackupRunner runner = new ScheduledBackupRunner(backups, source, listener, 100L);

        runner.start();
        try {
            long deadline = System.currentTimeMillis() + 3000L;
            while (hooks.pendingCount() == 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(20L);
            }
            assertTrue(backups.listBackups().size() >= 1);
            assertTrue(hooks.pendingCount() >= 1);
            CapturingTransport transport = new CapturingTransport();
            assertTrue(hooks.deliverAll(transport, 3, Duration.ZERO).delivered() >= 1);
            assertEquals("backup.succeeded", transport.types.get(0));
        } finally {
            runner.stop();
        }
    }

    @Test
    void failedBackupEmitsFailureEvent() throws Exception {
        Path data = temp.resolve("data");
        ScheduledBackupService backups = new ScheduledBackupService(data);
        OutboundWebhooks hooks = new OutboundWebhooks(data);
        OpsWebhookListener listener =
                new OpsWebhookListener(hooks, "https://hooks.example.test/ops", "secret");
        ScheduledBackupRunner runner =
                new ScheduledBackupRunner(backups, data.resolve("missing"), listener, 60000L);

        runner.runOnce();
        assertEquals(1, hooks.pendingCount());
        CapturingTransport transport = new CapturingTransport();
        assertEquals(1, hooks.deliverAll(transport, 3, Duration.ZERO).delivered());
        assertEquals("backup.failed", transport.types.get(0));
    }

    @Test
    void stoppingTwiceWithoutStartingIsSafe() {
        ScheduledBackupService backups = new ScheduledBackupService(temp.resolve("data"));
        ScheduledBackupRunner runner =
                new ScheduledBackupRunner(backups, temp.resolve("source"), null, 60000L);
        runner.stop();
        runner.stop();
    }
}
