/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.perf;

import java.util.Arrays;
import java.util.Objects;

/**
 * Wall-clock benchmark harness with a warmup phase and a bounded measured phase. No external
 * dependency and no background threads: the caller supplies a task as an abstract class.
 */
public final class TimingBenchmark {
    public static final int MAX_ITERATIONS = 1000000;

    /** Unit of work; callers supply a subclass or an anonymous inner class. */
    public abstract static class Task {
        public abstract void run();
    }

    /** Nanosecond summary of one measured run. */
    public static final class Result {
        private final int iterations;
        private final long meanNanos;
        private final long p95Nanos;

        public Result(int iterations, long meanNanos, long p95Nanos) {
            this.iterations = iterations;
            this.meanNanos = meanNanos;
            this.p95Nanos = p95Nanos;
        }

        public int iterations() {
            return iterations;
        }

        public long meanNanos() {
            return meanNanos;
        }

        public long p95Nanos() {
            return p95Nanos;
        }
    }

    private final int warmup;
    private final int measured;

    public TimingBenchmark(int warmup, int measured) {
        check(warmup, "warmup");
        check(measured, "measured");
        if (measured == 0) {
            throw new IllegalArgumentException("measured must be positive");
        }
        this.warmup = warmup;
        this.measured = measured;
    }

    public Result run(Task task) {
        Objects.requireNonNull(task, "task");
        for (int i = 0; i < warmup; i++) {
            task.run();
        }
        long[] samples = new long[measured];
        for (int i = 0; i < measured; i++) {
            long start = System.nanoTime();
            task.run();
            samples[i] = System.nanoTime() - start;
        }
        Arrays.sort(samples);
        long total = 0;
        for (int i = 0; i < samples.length; i++) {
            total += samples[i];
        }
        int p95Index = (int) Math.ceil(0.95d * measured) - 1;
        if (p95Index < 0) {
            p95Index = 0;
        }
        return new Result(measured, total / measured, samples[p95Index]);
    }

    public int warmupIterations() {
        return warmup;
    }

    public int measuredIterations() {
        return measured;
    }

    private static void check(int value, String name) {
        if (value < 0 || value > MAX_ITERATIONS) {
            throw new IllegalArgumentException(name + " out of range: " + value);
        }
    }
}
