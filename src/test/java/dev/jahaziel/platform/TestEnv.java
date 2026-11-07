/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
      Class<?> environment = Class.forName("java.lang.ProcessEnvironment");
      try {
        Field windows = environment.getDeclaredField("theCaseInsensitiveEnvironment");
        windows.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String> map = (Map<String, String>) windows.get(null);
        if (value == null) {
          map.remove(key);
        } else {
          map.put(key, value);
        }
      } catch (NoSuchFieldException notWindows) {
        Field unix = environment.getDeclaredField("theEnvironment");
        unix.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Object, Object> map = (Map<Object, Object>) unix.get(null);
        Class<?> variableType = Class.forName("java.lang.ProcessEnvironment$Variable");
        Object variable = stringValue(variableType, key);
        if (value == null) {
          map.remove(variable);
        } else {
          Class<?> valueType = Class.forName("java.lang.ProcessEnvironment$Value");
          map.put(variable, stringValue(valueType, value));
        }
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

  private static Object stringValue(Class<?> type, String value)
      throws ReflectiveOperationException {
    Method factory = type.getDeclaredMethod("valueOf", String.class);
    factory.setAccessible(true);
    return factory.invoke(null, value);
  }
}
