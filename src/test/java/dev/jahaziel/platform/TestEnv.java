/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Sets real process environment variables inside tests. Needed for the {@code System.getenv}
 * branches production reads directly; Mockito refuses {@code java.lang.System} and surefire cannot
 * scope env vars per test. Requires {@code --add-opens java.base/java.lang=ALL-UNNAMED}, wired in
 * the surefire {@code argLine}. Always {@link #clear} in a finally block.
 */
public final class TestEnv {
  private TestEnv() {}

  /**
   * Sets an environment variable for the current process.
   *
   * @param key the variable name
   * @param value the value, or null to remove the variable
   */
  public static void set(String key, String value) {
    try {
      Map<String, String> env = writableEnv();
      if (value == null) {
        env.remove(key);
      } else {
        env.put(key, value);
      }
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException("cannot tweak process env on this JVM", failure);
    }
  }

  /**
   * Removes an environment variable for the current process.
   *
   * @param key the variable name
   */
  public static void clear(String key) {
    set(key, null);
  }

  private static Map<String, String> writableEnv() throws ReflectiveOperationException {
    Class<?> environment = Class.forName("java.lang.ProcessEnvironment");
    try {
      Field windows = environment.getDeclaredField("theCaseInsensitiveEnvironment");
      windows.setAccessible(true);
      @SuppressWarnings("unchecked")
      Map<String, String> map = (Map<String, String>) windows.get(null);
      return map;
    } catch (NoSuchFieldException notWindows) {
      Field unix = environment.getDeclaredField("theEnvironment");
      unix.setAccessible(true);
      @SuppressWarnings("unchecked")
      Map<String, String> map = (Map<String, String>) unix.get(null);
      return map;
    }
  }
}
