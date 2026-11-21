/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/** Runs automatic backups on a fixed interval with a single daemon thread. */
public final class ScheduledBackupRunner implements Runnable {
    /** Number of most recent automatic backups retained after each successful run. */
    private static final int RETAINED_BACKUPS = 7;

    private final ScheduledBackupService backups;
    private final Path sourceDir;
    private final OpsWebhookListener webhooks;
    private final long intervalMillis;
    private ScheduledExecutorService executor;

    /** Creates a runner; start() and stop() bind and release its thread. */
    public ScheduledBackupRunner(
            ScheduledBackupService backups,
            Path sourceDir,
            OpsWebhookListener webhooks,
            long intervalMillis) {
        if (backups == null || sourceDir == null || intervalMillis <= 0L) {
            throw new IllegalArgumentException("invalid backup runner configuration");
        }
        this.backups = backups;
        this.sourceDir = sourceDir;
        this.webhooks = webhooks;
        this.intervalMillis = intervalMillis;
    }

    /** Starts the fixed-rate schedule; repeated calls are ignored. */
    public synchronized void start() {
        if (executor != null) {
            return;
        }
        ScheduledExecutorService created =
                Executors.newSingleThreadScheduledExecutor(
                        new ThreadFactory() {
                            private int number;

                            @Override
                            public synchronized Thread newThread(Runnable task) {
                                Thread thread = new Thread(task, "jahaziel-backup-" + (++number));
                                thread.setDaemon(true);
                                return thread;
                            }
                        });
        executor = created;
        created.scheduleAtFixedRate(this, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    /** Performs one backup attempt and reports success or failure to the webhook listener. */
    @Override
    public void run() {
        runOnce();
    }

    /** Runs one attempt directly, without starting the executor. */
    public void runOnce() {
        try {
            Path archive = backups.backup(sourceDir);
            backups.applyRetention(RETAINED_BACKUPS);
            if (webhooks != null) {
                webhooks.backupSucceeded(archive);
            }
        } catch (RuntimeException failure) {
            if (webhooks != null) {
                webhooks.backupFailed(failure.getMessage());
            }
        }
    }

    /** Stops the schedule without waiting for an in-flight backup. */
    public synchronized void stop() {
        ScheduledExecutorService running = executor;
        executor = null;
        if (running != null) {
            running.shutdown();
        }
    }
}
