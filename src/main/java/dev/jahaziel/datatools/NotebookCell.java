/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.datatools;

/** A notebook cell identified by a stable, filesystem-safe id. */
public final class NotebookCell {
  private final String id;
  private final String source;

  public NotebookCell(String id, String source) {
    if (id == null || !id.matches("[A-Za-z0-9_-]+")) {
      throw new IllegalArgumentException("cell id must be filesystem-safe");
    }
    this.id = id;
    this.source = source == null ? "" : source;
  }

  public String id() {
    return id;
  }

  public String source() {
    return source;
  }
}
