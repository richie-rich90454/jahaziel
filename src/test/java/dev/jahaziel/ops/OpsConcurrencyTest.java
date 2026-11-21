/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/** Concurrent callers must not bypass limits, double-approve, or double-deliver. */
class OpsConcurrencyTest {
    private static final int THREADS = 8;
    private static final int PER_THREAD = 100;

    @TempDir Path temp;

    private void run(final Runnable work) throws Exception {
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(THREADS);
        Thread[] threads = new Thread[THREADS];
        for (int i = 0; i < THREADS; i++) {
            threads[i] =
                    new Thread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        start.await();
                                        work.run();
                                    } catch (InterruptedException interrupted) {
                                        Thread.currentThread().interrupt();
                                    } finally {
                                        done.countDown();
                                    }
                                }
                            });
            threads[i].start();
        }
        start.countDown();
        done.await();
        for (int i = 0; i < THREADS; i++) {
            threads[i].join();
        }
    }

    @Test
    void rateLimiterNeverExceedsItsLimit() throws Exception {
        final RateLimiter limiter = new RateLimiter(new TestClock("2026-01-01T00:00:00Z"));
        final AtomicInteger allowed = new AtomicInteger();
        run(
                new Runnable() {
                    @Override
                    public void run() {
                        for (int i = 0; i < PER_THREAD; i++) {
                            if (limiter.tryAcquire("key", 500, Duration.ofMinutes(1)).allowed()) {
                                allowed.incrementAndGet();
                            }
                        }
                    }
                });
        assertEquals(500, allowed.get());
        assertEquals(500, limiter.count("key"));
    }

    @Test
    void quotaNeverExceedsItsLimit() throws Exception {
        final QuotaService quotas = new QuotaService();
        final AtomicInteger allowed = new AtomicInteger();
        run(
                new Runnable() {
                    @Override
                    public void run() {
                        for (int i = 0; i < PER_THREAD; i++) {
                            if (quotas.reserve("user", "alice", QuotaService.SESSION, 1L, 500L)
                                    .allowed()) {
                                allowed.incrementAndGet();
                            }
                        }
                    }
                });
        assertEquals(500, allowed.get());
        assertEquals(500L, quotas.usage("user", "alice", QuotaService.SESSION));
    }

    @Test
    void onlyOneConcurrentApprovalWins() throws Exception {
        final ApprovalQueue queue = new ApprovalQueue(temp.resolve("data"));
        final String id =
                queue.submit("catalog.addition", "alice", "python", new HashMap<String, String>());
        final AtomicInteger won = new AtomicInteger();
        final AtomicInteger lost = new AtomicInteger();
        run(
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            queue.approve(id, "bob");
                            won.incrementAndGet();
                        } catch (IllegalStateException conflict) {
                            lost.incrementAndGet();
                        }
                    }
                });
        assertEquals(1, won.get());
        assertEquals(THREADS - 1, lost.get());
        assertEquals(1, queue.decisions().size());
    }

    @Test
    void concurrentDeliveriesDoNotResendAnEvent() throws Exception {
        final OutboundWebhooks hooks = new OutboundWebhooks(temp.resolve("data2"));
        hooks.enqueue(
                new OutboundWebhooks.WebhookEvent(
                        "evt-1", "https://hooks.example.test", "type", "payload", "secret"));
        final AtomicInteger sends = new AtomicInteger();
        final OutboundWebhooks.WebhookTransport transport =
                new OutboundWebhooks.WebhookTransport() {
                    @Override
                    public int send(String url, String body, String signatureHeader) {
                        sends.incrementAndGet();
                        return 200;
                    }
                };
        run(
                new Runnable() {
                    @Override
                    public void run() {
                        hooks.deliverAll(transport, 3, Duration.ZERO);
                    }
                });
        assertEquals(1, sends.get());
        assertEquals(0, hooks.pendingCount());
    }
}
