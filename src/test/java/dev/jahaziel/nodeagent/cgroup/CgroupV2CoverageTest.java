/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.cgroup;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Failure branches of {@link CgroupV2}. */
class CgroupV2CoverageTest {
  @Test
  void constructorRejectsNull(@TempDir Path root) {
    assertThrows(NullPointerException.class, () -> new CgroupV2(null));
  }

  @Test
  void applyFailsWhenRootIsFile(@TempDir Path root) throws Exception {
    Path file = root.resolve("block");
    Files.writeString(file, "x");
    CgroupV2 groups = new CgroupV2(file);
    assertThrows(IllegalStateException.class, () -> groups.apply(UUID.randomUUID(), 1, 128, 100));
  }
}
