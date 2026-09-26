/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ide.internal;

import java.nio.file.Path;

/**
 * The REH tarball for the active variant is not installed. Callers that can proceed without an IDE
 * (workspace boot in an environment where the artifact was never staged) catch this and degrade;
 * hard requirements like {@code doctor} fail closed on it instead.
 */
public final class RehNotInstalledException extends IllegalStateException {
  /**
   * Creates the exception.
   *
   * @param tarball the expected tarball path
   */
  public RehNotInstalledException(Path tarball) {
    super(
        "REH tarball missing: " + tarball + " (build it with ide/build.sh or fetch the manifest)");
  }
}
