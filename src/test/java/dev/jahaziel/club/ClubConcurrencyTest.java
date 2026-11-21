/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.club;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.jahaziel.club.domain.ClubRoles;
import dev.jahaziel.club.domain.ClubSession;
import dev.jahaziel.club.moderation.ModerationService;
import dev.jahaziel.club.showcase.ShowcaseGallery;
import dev.jahaziel.club.support.Fakes;
import dev.jahaziel.identity.domain.UserId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/** Concurrent chat, help queue, roster, and showcase mutations must not lose updates. */
public final class ClubConcurrencyTest {
    private static final int THREADS = 16;

    @Test
    void concurrentPostsAndHelpJoinsAreAllCounted(@TempDir Path temp) throws Exception {
        final ModerationService moderation = new ModerationService(temp.resolve("club"));
        final ClubSession session = session(THREADS + 1);
        final List<UserId> users = new ArrayList<UserId>();
        for (int index = 0; index < THREADS; index++) {
            UserId user = new UserId(UUID.randomUUID());
            users.add(user);
            session.join(user, ClubRoles.ATTENDEE);
        }

        runEach(
                new Work() {
                    @Override
                    public void perform(int index) {
                        UserId user = users.get(index);
                        moderation.post(session, user, "hello");
                        moderation.joinHelpQueue(session, user);
                    }
                });

        assertEquals(THREADS, moderation.helpQueueSize(session));
        assertEquals(THREADS, moderation.history(session, users.get(0)).size());
    }

    @Test
    void concurrentJoinsRespectCapacity() throws Exception {
        final ClubSession session = session(THREADS / 2);
        final AtomicInteger joined = new AtomicInteger();
        runEach(
                new Work() {
                    @Override
                    public void perform(int index) {
                        UserId user = new UserId(UUID.randomUUID());
                        try {
                            session.join(user, ClubRoles.ATTENDEE);
                            joined.incrementAndGet();
                        } catch (dev.jahaziel.club.api.ClubLimitException full) {
                            // Expected for callers beyond capacity.
                        }
                    }
                });
        assertEquals(THREADS / 2, joined.get());
        assertEquals(THREADS / 2, session.size());
    }

    @Test
    void rateLimitIsExactUnderConcurrency() throws Exception {
        final ClubSession session = session(2);
        UserId user = new UserId(UUID.randomUUID());
        session.join(user, ClubRoles.ATTENDEE);
        final UserId actor = user;
        final AtomicInteger allowed = new AtomicInteger();
        runEach(
                new Work() {
                    @Override
                    public void perform(int index) {
                        if (session.tryAction(actor, "post", 5)) {
                            allowed.incrementAndGet();
                        }
                    }
                });
        assertEquals(5, allowed.get());
    }

    @Test
    void concurrentCurationKeepsEveryProject() throws Exception {
        final Fakes.Authorization authorization = new Fakes.Authorization();
        final UserId admin = new UserId(UUID.randomUUID());
        authorization.addAdmin(admin);
        final ShowcaseGallery gallery = new ShowcaseGallery(authorization);
        runEach(
                new Work() {
                    @Override
                    public void perform(int index) {
                        gallery.curate(
                                admin, "project", "", List.of("tag"), List.of("shot"), "git ref");
                    }
                });
        assertEquals(THREADS, gallery.size());
        assertEquals(THREADS, gallery.list().size());
    }

    private interface Work {
        void perform(int index);
    }

    private static void runEach(Work work) throws Exception {
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Future<?>> futures = new ArrayList<Future<?>>();
        for (int index = 0; index < THREADS; index++) {
            final int current = index;
            futures.add(
                    pool.submit(
                            new Runnable() {
                                @Override
                                public void run() {
                                    ready.countDown();
                                    try {
                                        start.await();
                                    } catch (InterruptedException interrupted) {
                                        Thread.currentThread().interrupt();
                                        return;
                                    }
                                    work.perform(current);
                                }
                            }));
        }
        ready.await();
        start.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdownNow();
    }

    private static ClubSession session(int capacity) {
        return new ClubSession(
                UUID.randomUUID().toString(),
                new UserId(UUID.randomUUID()),
                "java",
                Instant.parse("2026-11-19T10:00:00Z"),
                Instant.parse("2026-11-19T12:00:00Z"),
                capacity);
    }
}
