/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.spi.JobContext;
import dev.jahaziel.spi.JobScope;
import dev.jahaziel.spi.PlatformJob;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Behavior of {@link InProcessScheduler} with a counting job. */
class InProcessSchedulerTest {
    @Test
    void runsRegisteredJobs() throws Exception {
        InProcessScheduler scheduler = new InProcessScheduler();
        CountDownLatch fired = new CountDownLatch(1);
        scheduler.register(
                new PlatformJob() {
                    @Override
                    public void run(JobContext context) {
                        fired.countDown();
                    }

                    @Override
                    public Duration period() {
                        return Duration.ofMillis(20);
                    }

                    @Override
                    public JobScope scope() {
                        return JobScope.CLUSTER;
                    }
                });
        assertEquals(1, scheduler.registered());
        scheduler.start();
        try {
            assertTrue(fired.await(5, TimeUnit.SECONDS));
        } finally {
            scheduler.stop();
        }
    }

    @Test
    void doubleStartThrows() {
        InProcessScheduler scheduler = new InProcessScheduler();
        scheduler.start();
        try {
            assertThrows(
                    IllegalStateException.class,
                    new org.junit.jupiter.api.function.Executable() {
                        @Override
                        public void execute() {
                            scheduler.start();
                        }
                    });
        } finally {
            scheduler.stop();
        }
    }

    @Test
    void throwingJobsStayScheduled() throws Exception {
        InProcessScheduler scheduler = new InProcessScheduler();
        CountDownLatch second = new CountDownLatch(1);
        int[] runs = {0};
        scheduler.register(
                new PlatformJob() {
                    @Override
                    public void run(JobContext context) {
                        runs[0]++;
                        if (runs[0] == 1) {
                            throw new IllegalStateException("boom");
                        }
                        second.countDown();
                    }

                    @Override
                    public Duration period() {
                        return Duration.ofMillis(20);
                    }

                    @Override
                    public JobScope scope() {
                        return JobScope.CLUSTER;
                    }
                });
        scheduler.start();
        try {
            assertTrue(second.await(5, TimeUnit.SECONDS));
        } finally {
            scheduler.stop();
        }
    }
}
