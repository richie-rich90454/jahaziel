/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.language.internal;

import java.io.IOException;
import java.nio.file.Path;

/** Builds the pinned Go language server using only the SDK being installed in its pack. */
abstract class GoPackBuilder {
    /** Builds the language server in the staging directory. */
    public abstract void build(Path goExecutable, Path stagingDirectory) throws IOException;
}
