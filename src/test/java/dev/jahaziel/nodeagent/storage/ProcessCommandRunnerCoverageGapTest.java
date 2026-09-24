/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.storage;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The spawn-failure branch of {@link ProcessCommandRunner}. */
class ProcessCommandRunnerCoverageGapTest {
  private final ProcessCommandRunner runner = new ProcessCommandRunner();

  @Test
  void existsFailsWhenSpawnFails(@TempDir Path root) {
    // ponytail: an overlong argument breaks process creation on every OS.
    Path huge = root.resolve("x".repeat(40000));
    assertFalse(runner.exists(huge));
    assertFalse(runner.exists(root.resolve("missing")));
  }
}
