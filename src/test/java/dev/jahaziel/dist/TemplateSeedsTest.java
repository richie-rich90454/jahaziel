/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Behavior of {@link TemplateSeeds} against a temporary directory. */
class TemplateSeedsTest {
  @Test
  void seedsAllBundledTemplates(@TempDir Path tmp) throws Exception {
    Path templates = tmp.resolve("templates");
    TemplateSeeds.copyIfAbsent(templates);
    for (String name : TemplateSeeds.SEEDS) {
      Path file = templates.resolve(name).resolve("template.json");
      assertTrue(Files.isRegularFile(file), "seed missing: " + file);
      assertTrue(
          Files.readString(file, StandardCharsets.UTF_8).contains("\"name\""),
          "seed must define a name: " + file);
    }
  }

  @Test
  void neverOverwritesUserTemplates(@TempDir Path tmp) throws Exception {
    Path templates = tmp.resolve("templates");
    TemplateSeeds.copyIfAbsent(templates);
    Path custom = templates.resolve("empty").resolve("template.json");
    Files.writeString(custom, "{\"name\":\"mine\"}", StandardCharsets.UTF_8);
    TemplateSeeds.copyIfAbsent(templates);
    assertEquals("{\"name\":\"mine\"}", Files.readString(custom, StandardCharsets.UTF_8));
  }

  @Test
  void nodeTemplateProvidesRunnableTypeScriptAndPreviewTasks(@TempDir Path tmp) throws Exception {
    Path templates = tmp.resolve("templates");
    TemplateSeeds.copyIfAbsent(templates);
    JsonNode manifest =
        new ObjectMapper()
            .readTree(
                Files.readString(templates.resolve("node/template.json"), StandardCharsets.UTF_8));

    assertTrue(manifest.path("starterFiles").has("src/index.ts"));
    assertTrue(manifest.path("starterFiles").has("src/server.js"));
    assertTrue(
        manifest.path("starterFiles").path("README.md").asString().contains("does not type-check"));
    assertTrue(
        manifest
            .path("starterFiles")
            .path("README.md")
            .asString()
            .contains("language packs download only after you select one"));
    assertEquals(2, manifest.path("runTasks").size());
    assertEquals("start", manifest.path("runTasks").path(0).path("id").asString());
    assertEquals(
        "src/server.js", manifest.path("runTasks").path(0).path("command").path(1).asString());
    assertEquals("typescript-example", manifest.path("runTasks").path(1).path("id").asString());
    assertEquals(
        "src/index.ts", manifest.path("runTasks").path(1).path("command").path(1).asString());
  }
}
