/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.datatools;

/** One rendered cell output: plain text, JSON or an inline image. */
public final class CellOutput {
  public static final String TEXT = "text";
  public static final String JSON = "json";
  public static final String IMAGE = "image";

  private final String type;
  private final String data;
  private final String mime;

  public CellOutput(String type, String data, String mime) {
    if (type == null || type.trim().isEmpty()) {
      throw new IllegalArgumentException("type is required");
    }
    this.type = type;
    this.data = data == null ? "" : data;
    this.mime = mime == null ? "application/octet-stream" : mime;
  }

  public static CellOutput text(String data) {
    return new CellOutput(TEXT, data, "text/plain");
  }

  public static CellOutput json(String data) {
    return new CellOutput(JSON, data, "application/json");
  }

  public static CellOutput image(String data, String mime) {
    return new CellOutput(IMAGE, data, mime);
  }

  public String type() {
    return type;
  }

  public String data() {
    return data;
  }

  public String mime() {
    return mime;
  }
}
