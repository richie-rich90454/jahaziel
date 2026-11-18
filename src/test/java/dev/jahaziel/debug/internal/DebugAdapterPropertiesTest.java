/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.debug.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DebugAdapterPropertiesTest {
  @Test
  void acceptsFixedAbsoluteStdioAdapterCommand() {
    var adapter = new DebugAdapterProperties.Adapter("1.0", List.of(executable()), null);

    assertEquals(DebugAdapterProperties.Transport.STDIO, adapter.transport());
  }

  @Test
  void acceptsTcpAdapterWithServerOwnedPortArgument() {
    var adapter =
        new DebugAdapterProperties.Adapter(
            "1.2.3",
            List.of(executable(), "dap", "--listen=127.0.0.1:{port}"),
            DebugAdapterProperties.Transport.TCP);

    assertEquals(DebugAdapterProperties.Transport.TCP, adapter.transport());
  }

  @Test
  void rejectsRelativeExecutablesAndMismatchedTransports() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new DebugAdapterProperties.Adapter("1.0", List.of("debugpy"), null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new DebugAdapterProperties.Adapter(
                "1.0", List.of(executable()), DebugAdapterProperties.Transport.TCP));
  }

  @Test
  void rejectsLanguageOutsideTheFixedCatalog() {
    var adapter = new DebugAdapterProperties.Adapter("1.0", List.of(executable()), null);

    assertThrows(
        IllegalArgumentException.class, () -> new DebugAdapterProperties(Map.of("shell", adapter)));
  }

  private static String executable() {
    return Path.of(System.getProperty("user.dir"), ".cache", "debug-adapter").toString();
  }
}
