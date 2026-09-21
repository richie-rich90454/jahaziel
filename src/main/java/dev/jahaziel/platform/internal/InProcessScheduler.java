/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.internal;

import dev.jahaziel.spi.JobContext;
import dev.jahaziel.spi.PlatformJob;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Runs platform jobs on one shared thread. A throwing job is logged, never unscheduled. */
public final class InProcessScheduler {
  private static final Logger log = LoggerFactory.getLogger(InProcessScheduler.class);

  private final List<PlatformJob> jobs = new CopyOnWriteArrayList<>();
  private final ScheduledExecutorService executor =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "jahaziel-scheduler");
            thread.setDaemon(true);
            return thread;
          });
  private boolean started;

  /**
   * Registers a job. Jobs registered after start never run.
   *
   * @param job the job to register
   */
  public synchronized void register(PlatformJob job) {
    jobs.add(Objects.requireNonNull(job, "job"));
  }

  /** Starts every registered job at its period. */
  public synchronized void start() {
    if (started) {
      throw new IllegalStateException("scheduler already started");
    }
    started = true;
    for (PlatformJob job : jobs) {
      long period = job.period().toMillis();
      executor.scheduleAtFixedRate(() -> run(job), period, period, TimeUnit.MILLISECONDS);
    }
  }

  /** Stops the scheduler. */
  public void stop() {
    executor.shutdown();
  }

  /**
   * Returns the registered job count.
   *
   * @return the count
   */
  public int registered() {
    return jobs.size();
  }

  private static void run(PlatformJob job) {
    try {
      job.run(JobContext.now());
    } catch (RuntimeException failure) {
      log.error("Scheduled job failed", failure);
    }
  }
}
