/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.systemconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Config is read and written only inside the jar directory, with validated keys and values. */
final class SystemConfigServiceTest {
    @Test
    void writesAndMasksConfig(@TempDir Path temp) {
        final SystemConfigService service =
                new SystemConfigService(temp.resolve("data").toString());
        final Map<String, String> changes = new HashMap<String, String>();
        changes.put("BASIS_CLIENT_ID", "jahaziel");
        changes.put("BASIS_CLIENT_SECRET", "topsecret");
        changes.put("BASIS_AUTH_CLIENT_ID", "alias");

        Map<String, String> view = service.update(changes, null);
        assertEquals("jahaziel", view.get("BASIS_CLIENT_ID"));
        assertEquals(SystemConfigService.MASK, view.get("BASIS_CLIENT_SECRET"));
        assertEquals("alias", view.get("BASIS_AUTH_CLIENT_ID"));
        assertTrue(service.envFile().startsWith(temp.toAbsolutePath()));
        assertTrue(service.isInsideJarDir(service.envFile()));
        assertTrue(!service.isInsideJarDir(temp.resolve("data").resolve("other.env")));
    }

    @Test
    void rejectsBadKeysAndValues(@TempDir Path temp) {
        final SystemConfigService service =
                new SystemConfigService(temp.resolve("data").toString());
        final Map<String, String> badKey = new HashMap<String, String>();
        badKey.put("BAD KEY", "x");
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        service.update(badKey, null);
                    }
                });
        final Map<String, String> deniedKey = new HashMap<String, String>();
        deniedKey.put("PATH", "x");
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        service.update(deniedKey, null);
                    }
                });
        final Map<String, String> badValue = new HashMap<String, String>();
        badValue.put("GOOD_KEY", "line\nbreak");
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        service.update(badValue, null);
                    }
                });
    }

    @Test
    void rejectsPathsOutsideJarDir(@TempDir Path temp) {
        final SystemConfigService service =
                new SystemConfigService(temp.resolve("data").toString());
        final Map<String, String> changes = new HashMap<String, String>();
        changes.put("GOOD_KEY", "value");
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        service.update(changes, "../evil.env");
                    }
                });
    }

    @Test
    void maskKeepsExistingSecretOnUpdate(@TempDir Path temp) {
        final SystemConfigService service =
                new SystemConfigService(temp.resolve("data").toString());
        Map<String, String> first = new HashMap<String, String>();
        first.put("BASIS_CLIENT_SECRET", "secret");
        service.update(first, null);
        Map<String, String> second = new HashMap<String, String>();
        second.put("BASIS_CLIENT_SECRET", SystemConfigService.MASK);
        service.update(second, null);
        assertTrue(service.view().get("BASIS_CLIENT_SECRET").equals(SystemConfigService.MASK));
    }
}
