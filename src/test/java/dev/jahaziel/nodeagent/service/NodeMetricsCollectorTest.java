/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

/** {@link NodeMetricsCollector} samples on every OS, with or without /proc. */
class NodeMetricsCollectorTest {
    @Test
    void sampleStaysInRange(@TempDir Path root) {
        HostSample sample = new NodeMetricsCollector(root).sample();
        assertTrue(sample.cpuUsagePercent() >= 0 && sample.cpuUsagePercent() <= 100);
        assertTrue(sample.memoryUsedBytes() >= 0);
        assertTrue(sample.memoryTotalBytes() >= 0);
        assertTrue(sample.diskUsedBytes() >= 0);
        assertTrue(sample.diskTotalBytes() > 0);
        if (sample.memoryTotalBytes() > 0) {
            assertTrue(sample.memoryUsedBytes() <= sample.memoryTotalBytes());
        }
    }
}
