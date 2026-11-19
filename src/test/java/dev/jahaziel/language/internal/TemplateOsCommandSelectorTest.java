/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.language.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

class TemplateOsCommandSelectorTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void pythonTemplateKeepsLinuxCommandsAndAddsWindowsSelectors() throws Exception {
        JsonNode python = manifest("python");
        JsonNode venv = python.path("setupSteps").get(0);
        JsonNode install = python.path("setupSteps").get(1);
        JsonNode example = python.path("runTasks").get(0);
        JsonNode preview = python.path("runTasks").get(1);

        assertEquals("python3", command(venv).get(0));
        assertEquals("python", command(venv.path("os").path("windows")).get(0));
        assertEquals("python3", command(venv.path("os").path("darwin")).get(0));
        assertEquals(".venv/bin/python", command(install).get(0));
        assertEquals(
                ".venv\\Scripts\\python.exe", command(install.path("os").path("windows")).get(0));

        assertEquals(".venv/bin/python", command(example).get(0));
        assertEquals(
                ".venv\\Scripts\\python.exe", command(example.path("os").path("windows")).get(0));
        assertEquals(".venv/bin/python", command(preview).get(0));
        assertEquals(
                ".venv\\Scripts\\python.exe", command(preview.path("os").path("windows")).get(0));
        assertEquals(
                "python",
                preview.path("os").path("windows").path("debug").path("executable").asString());
        assertEquals("python3", preview.path("debug").path("executable").asString());
    }

    @Test
    void staticSiteTemplateKeepsLinuxCommandsAndAddsWindowsAndMacSelectors() throws Exception {
        JsonNode site = manifest("static-site");
        JsonNode preview = site.path("runTasks").get(0);

        assertEquals("python3", command(preview).get(0));
        assertEquals("python", command(preview.path("os").path("windows")).get(0));
        assertEquals("python3", command(preview.path("os").path("darwin")).get(0));
        assertEquals(8080, preview.path("port").asInt());
    }

    private JsonNode manifest(String name) throws Exception {
        try (InputStream input =
                getClass().getResourceAsStream("/templates/" + name + "/template.json")) {
            assertNotNull(input, name + " template is on the classpath");
            return json.readTree(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static java.util.List<String> command(JsonNode step) {
        java.util.List<String> result = new java.util.ArrayList<String>();
        JsonNode command = step.path("command");
        for (int index = 0; index < command.size(); index++) {
            result.add(command.get(index).asString());
        }
        return result;
    }
}
