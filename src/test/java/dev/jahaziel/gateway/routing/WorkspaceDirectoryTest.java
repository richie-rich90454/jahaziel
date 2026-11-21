/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.gateway.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.UUID;

/** Behavior of {@link WorkspaceDirectory}. */
class WorkspaceDirectoryTest {
    @Test
    void registersLooksUpAndRemoves() {
        WorkspaceDirectory directory = new WorkspaceDirectory();
        UUID workspace = UUID.randomUUID();
        directory.register("richard", 8080, workspace);
        assertEquals(workspace, directory.lookup("richard", 8080).orElseThrow());
        directory.unregister("richard", 8080);
        assertTrue(directory.lookup("richard", 8080).isEmpty());
    }

    @Test
    void twoWorkspacesOfOneUserCanShareAPortForPathPreviews() {
        WorkspaceDirectory directory = new WorkspaceDirectory();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        directory.register("richard", 8080, first);
        directory.register("richard", 8080, second);

        assertEquals("richard", directory.lookupSlug(first, 8080).orElseThrow());
        assertEquals("richard", directory.lookupSlug(second, 8080).orElseThrow());

        directory.unregister("richard", 8080, first);
        assertTrue(directory.lookupSlug(first, 8080).isEmpty());
        assertEquals("richard", directory.lookupSlug(second, 8080).orElseThrow());
        assertTrue(directory.lookup("richard", 8080).isPresent());

        directory.unregister("richard", 8080, second);
        assertTrue(directory.lookupSlug(second, 8080).isEmpty());
        assertTrue(directory.lookup("richard", 8080).isEmpty());
    }

    @Test
    void resolvesSlugOnlyForTheMatchingWorkspacePort() {
        WorkspaceDirectory directory = new WorkspaceDirectory();
        UUID workspace = UUID.randomUUID();
        directory.register("richard", 8080, workspace);

        assertEquals("richard", directory.lookupSlug(workspace, 8080).orElseThrow());
        assertTrue(directory.lookupSlug(UUID.randomUUID(), 8080).isEmpty());
        assertTrue(directory.lookupSlug(workspace, 8081).isEmpty());
        assertTrue(directory.lookupSlug(workspace, 0).isEmpty());
    }
}
