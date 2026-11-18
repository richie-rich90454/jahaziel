/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.agent.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.jahaziel.agent.AgentException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class ModelCatalogTest {

    @Test
    void onlyConfiguredIdsAreAllowed() {
        ModelCatalog catalog = new ModelCatalog(allowed("model-a", "model-b"), null);

        assertTrue(catalog.isAllowed("model-a"));
        assertFalse(catalog.isAllowed("https://evil.example/v1"));
        assertEquals(ModelCatalog.UNAVAILABLE, catalog.capability("not-configured"));
        assertEquals(2, catalog.catalog().size());
    }

    @Test
    void remoteEndpointsAreNeverAcceptedAsConfiguration() {
        ModelCatalog catalog = new ModelCatalog(allowed("local"), "https://evil.example/v1");

        assertNull(catalog.localEndpoint());
        assertEquals(ModelCatalog.UNAVAILABLE, catalog.capability("local"));
    }

    @Test
    void loopbackEndpointEnablesTheLocalModelOnlyWhenConfigured() {
        ModelCatalog absent = new ModelCatalog(allowed("local"), null);
        ModelCatalog present = new ModelCatalog(allowed("local"), "http://127.0.0.1:11434");

        assertEquals(ModelCatalog.UNAVAILABLE, absent.capability("local"));
        assertEquals(ModelCatalog.AVAILABLE, present.capability("local"));
        assertEquals("http://127.0.0.1:11434", present.localEndpoint());
    }

    @Test
    void invalidIdsCannotEnterTheCatalog() {
        final List<String> bad = allowed("../../etc/passwd");
        try {
            new ModelCatalog(bad, null);
            fail("expected an AgentException");
        } catch (Throwable failure) {
            assertTrue(failure instanceof AgentException);
        }
    }

    private static List<String> allowed(String... ids) {
        List<String> result = new ArrayList<String>();
        for (String id : ids) {
            result.add(id);
        }
        return result;
    }
}
