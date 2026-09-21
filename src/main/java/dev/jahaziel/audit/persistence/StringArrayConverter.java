/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.audit.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stores string arrays as JSON text. Postgres and SQLite both hold plain TEXT; no array column
 * type, so one mapping serves both databases. Dependency-free JSON codec: only the string array
 * shape is supported.
 */
@Converter
public final class StringArrayConverter implements AttributeConverter<String[], String> {
  @Override
  public String convertToDatabaseColumn(String[] attribute) {
    if (attribute == null) {
      return null;
    }
    StringBuilder json = new StringBuilder("[");
    for (int i = 0; i < attribute.length; i++) {
      if (i > 0) {
        json.append(',');
      }
      json.append('"');
      for (char c : attribute[i].toCharArray()) {
        switch (c) {
          case '"' -> json.append("\\\"");
          case '\\' -> json.append("\\\\");
          case '\n' -> json.append("\\n");
          case '\r' -> json.append("\\r");
          case '\t' -> json.append("\\t");
          default -> {
            if (c < 0x20) {
              json.append(String.format("\\u%04x", (int) c));
            } else {
              json.append(c);
            }
          }
        }
      }
      json.append('"');
    }
    return json.append(']').toString();
  }

  @Override
  public String[] convertToEntityAttribute(String row) {
    if (row == null) {
      return null;
    }
    String text = row.trim();
    if (!text.startsWith("[") || !text.endsWith("]")) {
      throw new IllegalArgumentException("not a JSON string array: " + row);
    }
    java.util.ArrayList<String> values = new java.util.ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inString = false;
    boolean escape = false;
    boolean hadElement = false;
    for (int i = 1; i < text.length() - 1; i++) {
      char c = text.charAt(i);
      if (escape) {
        switch (c) {
          case '"' -> current.append('"');
          case '\\' -> current.append('\\');
          case 'n' -> current.append('\n');
          case 'r' -> current.append('\r');
          case 't' -> current.append('\t');
          case 'u' -> {
            if (i + 4 >= text.length() - 1) {
              throw new IllegalArgumentException("bad unicode escape: " + row);
            }
            current.append((char) Integer.parseInt(text.substring(i + 1, i + 5), 16));
            i += 4;
          }
          default -> throw new IllegalArgumentException("bad escape: " + row);
        }
        escape = false;
        continue;
      }
      if (inString) {
        if (c == '\\') {
          escape = true;
        } else if (c == '"') {
          inString = false;
          values.add(current.toString());
          current.setLength(0);
          hadElement = true;
        } else {
          current.append(c);
        }
        continue;
      }
      if (c == '"') {
        inString = true;
      } else if (c == ',' || Character.isWhitespace(c)) {
        if (c == ',' && !hadElement) {
          throw new IllegalArgumentException("bad array separator: " + row);
        }
      } else {
        throw new IllegalArgumentException("unexpected character: " + row);
      }
    }
    if (inString || escape) {
      throw new IllegalArgumentException("unterminated string: " + row);
    }
    return values.toArray(new String[0]);
  }
}
