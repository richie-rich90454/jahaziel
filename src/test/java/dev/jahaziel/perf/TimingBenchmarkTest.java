/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/** Deterministic invocation counts and bounded configuration for the timing harness. */
class TimingBenchmarkTest {
    @Test
    void warmupAndMeasuredCountsAreExact() {
        final int[] calls = new int[1];
        TimingBenchmark.Task task =
                new TimingBenchmark.Task() {
                    @Override
                    public void run() {
                        calls[0]++;
                    }
                };
        TimingBenchmark.Result result = new TimingBenchmark(2, 5).run(task);
        assertEquals(7, calls[0]);
        assertEquals(5, result.iterations());
        assertTrue(result.meanNanos() >= 0);
        assertTrue(result.p95Nanos() >= 0);
    }

    @Test
    void p95IsAtLeastMeanForFiveFixedSamples() {
        TimingBenchmark.Task task =
                new TimingBenchmark.Task() {
                    @Override
                    public void run() {
                        int sum = 0;
                        for (int i = 0; i < 64; i++) {
                            sum += i;
                        }
                        if (sum < 0) {
                            throw new IllegalStateException("unreachable");
                        }
                    }
                };
        TimingBenchmark.Result result = new TimingBenchmark(1, 5).run(task);
        assertTrue(result.p95Nanos() >= result.meanNanos());
    }

    @Test
    void invalidIterationCountsThrow() {
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        new TimingBenchmark(-1, 5);
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        new TimingBenchmark(1, 0);
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        new TimingBenchmark(1, TimingBenchmark.MAX_ITERATIONS + 1);
                    }
                });
    }

    @Test
    void nullTaskThrows() {
        assertThrows(
                NullPointerException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        new TimingBenchmark(0, 1).run(null);
                    }
                });
    }
}
