/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.systemconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/** Global, user, and workspace overrides have a fixed precedence and persist under data. */
final class FeatureFlagServiceTest {
    @Test
    void overridesFollowWorkspaceThenUserThenGlobal(@TempDir Path temp) {
        final String data = temp.resolve("data").toString();
        final FeatureFlagService service = new FeatureFlagService(data);
        final dev.jahaziel.identity.domain.UserId user =
                new dev.jahaziel.identity.domain.UserId(UUID.randomUUID());
        final dev.jahaziel.workspace.domain.WorkspaceId workspace =
                new dev.jahaziel.workspace.domain.WorkspaceId(UUID.randomUUID());

        service.set("dark.mode", FeatureFlagService.GLOBAL, null, true);
        assertTrue(service.isEnabled("dark.mode", user, workspace));
        service.set("dark.mode", FeatureFlagService.USER, user.value().toString(), false);
        assertFalse(service.isEnabled("dark.mode", user, workspace));
        service.set("dark.mode", FeatureFlagService.WORKSPACE, workspace.value().toString(), true);
        assertTrue(service.isEnabled("dark.mode", user, workspace));
        service.clear("dark.mode", FeatureFlagService.WORKSPACE, workspace.value().toString());
        assertFalse(service.isEnabled("dark.mode", user, workspace));

        FeatureFlagService reloaded = new FeatureFlagService(data);
        assertFalse(reloaded.isEnabled("dark.mode", user, workspace));
        Map<String, Object> view = reloaded.view();
        assertTrue(view.containsKey("flags"));
        assertTrue(view.containsKey("overrides"));
    }

    @Test
    void invalidNamesAndTargetsRejected(@TempDir Path temp) {
        final FeatureFlagService service = new FeatureFlagService(temp.resolve("data").toString());
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        service.set("bad name", FeatureFlagService.GLOBAL, null, true);
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        service.set("good.flag", FeatureFlagService.USER, "not-a-uuid", true);
                    }
                });
        assertEquals(false, service.isEnabled("unknown", null, null));
    }
}
