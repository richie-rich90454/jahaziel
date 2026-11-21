/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingSupplier;

import java.util.ServiceLoader;

/** Discovery of {@link IdentityProvider} with no providers registered. */
class IdentityProviderDiscoveryTest {
    @Test
    void loadsWithoutProviders() {
        int count =
                assertDoesNotThrow(
                        new ThrowingSupplier<Integer>() {
                            @Override
                            public Integer get() {
                                int n = 0;
                                for (IdentityProvider provider :
                                        ServiceLoader.load(IdentityProvider.class)) {
                                    assertNotNull(provider);
                                    n++;
                                }
                                return n;
                            }
                        });
        assertEquals(0, count);
    }
}
