/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.dist;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Seeds {@code data/templates/} from the jar on first run. Bundled {@code template.json} files
 * copy over only when the target is absent, so user edits and custom templates always win and
 * reruns are idempotent.
 */
final class TemplateSeeds {
  /** Templates shipped inside the jar, matching {@code src/main/resources/templates/}. */
  static final List<String> SEEDS = List.of("empty", "node", "python", "java", "static-site");

  private TemplateSeeds() {}

  /**
   * Copies missing seed templates into the data directory.
   *
   * @param templatesDir the runtime {@code templates/} directory
   * @throws IOException when the directory cannot be prepared
   */
  static void copyIfAbsent(Path templatesDir) throws IOException {
    Files.createDirectories(templatesDir);
    for (String name : SEEDS) {
      Path target = templatesDir.resolve(name).resolve("template.json");
      if (Files.isRegularFile(target)) {
        continue;
      }
      try (InputStream bundled =
          TemplateSeeds.class.getResourceAsStream("/templates/" + name + "/template.json")) {
        if (bundled == null) {
          continue;
        }
        Files.createDirectories(target.getParent());
        Files.write(target, bundled.readAllBytes());
      }
    }
  }
}
