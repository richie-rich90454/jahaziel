/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

/** Verifies the basis permission keys reach the map the resource filter reads. */
class PluginSettingsTest {
    @Test
    void permissionKeysAreCollected() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("basis.permissions", "projects.read projects.write");
        environment.setProperty("BASIS_AUTH_PERMISSIONS", "auth.value");
        environment.setProperty("BASIS_PERMISSIONS", "legacy.value");
        environment.setProperty("basis.refresh-window-seconds", "60");
        environment.setProperty("BASIS_AUTH_REFRESH_WINDOW_SECONDS", "45");

        Map<String, String> settings = PluginSettings.from(environment);

        assertEquals("projects.read projects.write", settings.get("basis.permissions"));
        assertEquals("auth.value", settings.get("BASIS_AUTH_PERMISSIONS"));
        assertEquals("legacy.value", settings.get("BASIS_PERMISSIONS"));
        assertEquals("60", settings.get("basis.refresh-window-seconds"));
        assertEquals("45", settings.get("BASIS_AUTH_REFRESH_WINDOW_SECONDS"));
    }
}
