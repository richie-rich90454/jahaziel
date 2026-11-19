/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.systemconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.api.AdminConfigController;
import dev.jahaziel.api.ApiFailureException;
import dev.jahaziel.authorization.api.AuthorizationService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Admin endpoints require the admin role and expose validated config and flags. */
final class AdminConfigControllerTest {
    @Test
    void controllerIsAnnotatedRestController() {
        assertTrue(AdminConfigController.class.isAnnotationPresent(RestController.class));
        RequestMapping mapping = AdminConfigController.class.getAnnotation(RequestMapping.class);
        assertTrue(mapping != null && mapping.value().length == 1);
        assertEquals("/api/v1/admin", mapping.value()[0]);
    }

    @Test
    void adminCanReadAndUpdateConfig(@TempDir Path temp) {
        final AdminConfigController controller = controller(temp, new ConfigFixtures.AllowAll());
        final Map<String, String> body = new HashMap<String, String>();
        body.put("JAHAZIEL_DEMO", "false");
        controller.updateConfig(ConfigFixtures.principal(), body);
        assertEquals("false", controller.config(ConfigFixtures.principal()).get("JAHAZIEL_DEMO"));
    }

    @Test
    void adminCanManageFlags(@TempDir Path temp) {
        final AdminConfigController controller = controller(temp, new ConfigFixtures.AllowAll());
        final Map<String, Object> body = new HashMap<String, Object>();
        body.put("name", "new.editor");
        body.put("scope", FeatureFlagService.GLOBAL);
        body.put("enabled", Boolean.TRUE);
        controller.updateFlag(ConfigFixtures.principal(), body);
        Map<String, Object> view = controller.flags(ConfigFixtures.principal());
        @SuppressWarnings("unchecked")
        Map<String, Boolean> flags = (Map<String, Boolean>) view.get("flags");
        assertEquals(Boolean.TRUE, flags.get("new.editor"));
    }

    @Test
    void nonAdminIsDenied(@TempDir Path temp) {
        final AdminConfigController controller = controller(temp, new ConfigFixtures.DenyAll());
        assertThrows(
                ApiFailureException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        controller.config(ConfigFixtures.principal());
                    }
                });
    }

    private static AdminConfigController controller(Path temp, AuthorizationService auth) {
        String data = temp.resolve("data").toString();
        return new AdminConfigController(
                new SystemConfigService(data), new FeatureFlagService(data), auth);
    }
}
