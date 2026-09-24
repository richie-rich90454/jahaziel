/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal {@code .env} parser. Handles CRLF/LF, {@code #} comments, single and double quotes, and
 * an optional {@code export} prefix. Real environment variables always win over file values.
 */
public final class DotEnv {
  /** The five basis-auth variables. Login stays off until all are present. */
  public static final List<String> BASIS_VARS =
      List.of(
          "BASIS_AUTH_ISSUER",
          "BASIS_REDIRECT_URI",
          "BASIS_CLIENT_ID",
          "BASIS_CLIENT_SECRET",
          "BASIS_RESOURCE");

  private DotEnv() {}

  /**
   * Parses a {@code .env} file. Missing files yield an empty map.
   *
   * @param file the file to parse
   * @return the entries, insertion ordered
   */
  public static Map<String, String> parse(Path file) {
    if (file == null || !Files.isRegularFile(file)) {
      return Map.of();
    }
    try {
      String text = Files.readString(file, StandardCharsets.UTF_8);
      Map<String, String> values = new LinkedHashMap<>();
      for (String raw : text.split("\r?\n")) {
        String line = raw.trim();
        if (line.isEmpty() || line.startsWith("#")) {
          continue;
        }
        if (line.startsWith("export ")) {
          line = line.substring("export ".length()).trim();
        }
        int equals = line.indexOf('=');
        if (equals <= 0) {
          continue;
        }
        String key = line.substring(0, equals).trim();
        // NOTE: key is never empty here: equals > 0 plus the trimmed line means the
        // substring holds a non-blank first character, so no isEmpty check is needed.
        if (key.contains(" ") || key.contains("\t")) {
          continue;
        }
        String value = stripInlineComment(line.substring(equals + 1).trim());
        values.put(key, unquote(value));
      }
      return Collections.unmodifiableMap(values);
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot read " + file, failure);
    }
  }

  /**
   * Merges file values under the real environment: real env wins, file fills the gaps.
   *
   * @param fileValues the parsed file
   * @param environment the real environment, usually {@link System#getenv()}
   * @return the merged map
   */
  public static Map<String, String> withRealEnvWins(
      Map<String, String> fileValues, Map<String, String> environment) {
    Map<String, String> merged = new LinkedHashMap<>(fileValues);
    merged.putAll(environment);
    return Collections.unmodifiableMap(merged);
  }

  /**
   * Returns the missing BASIS_* names from an effective environment.
   *
   * @param effectiveEnv the merged env
   * @return the missing names, empty when login can run
   */
  public static List<String> missingBasisVars(Map<String, String> effectiveEnv) {
    return BASIS_VARS.stream()
        .filter(
            key -> {
              String value = effectiveEnv.get(key);
              return value == null || value.isBlank();
            })
        .toList();
  }

  private static String stripInlineComment(String value) {
    boolean single = false;
    boolean doubled = false;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '\'' && !doubled) {
        single = !single;
      } else if (c == '"' && !single) {
        doubled = !doubled;
      } else if (c == '#' && !single && !doubled) {
        if (i > 0 && Character.isWhitespace(value.charAt(i - 1))) {
          return value.substring(0, i).trim();
        }
      }
    }
    return value;
  }

  private static String unquote(String value) {
    if (value.length() >= 2) {
      char first = value.charAt(0);
      char last = value.charAt(value.length() - 1);
      if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
        return value.substring(1, value.length() - 1);
      }
    }
    return value;
  }
}
