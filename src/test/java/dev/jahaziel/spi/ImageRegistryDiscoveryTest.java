/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;

/** Discovery of {@link ImageRegistry} with no providers registered. */
class ImageRegistryDiscoveryTest {
    @Test
    void loadsWithoutProviders() {
        int count =
                assertDoesNotThrow(
                        () ->
                                ServiceLoader.load(ImageRegistry.class).stream()
                                        .mapToInt(p -> 1)
                                        .sum());
        assertEquals(0, count);
    }
}
