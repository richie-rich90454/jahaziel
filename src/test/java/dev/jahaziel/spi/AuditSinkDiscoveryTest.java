/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingSupplier;

import java.util.ServiceLoader;

/** Discovery of {@link AuditSink} with no providers registered. */
class AuditSinkDiscoveryTest {
    @Test
    void loadsWithoutProviders() {
        int count =
                assertDoesNotThrow(
                        new ThrowingSupplier<Integer>() {
                            @Override
                            public Integer get() {
                                int n = 0;
                                for (AuditSink provider : ServiceLoader.load(AuditSink.class)) {
                                    n++;
                                }
                                return n;
                            }
                        });
        assertEquals(0, count);
    }
}
