/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.debug.internal;

import dev.jahaziel.debug.api.DebugService;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Server-owned DAP adapter commands. Browser requests cannot modify this catalog. */
@ConfigurationProperties(prefix = "jahaziel.debug")
public record DebugAdapterProperties(Map<String, Adapter> adapters) {
  public DebugAdapterProperties {
    adapters = adapters == null ? Map.of() : Map.copyOf(adapters);
    if (!DebugService.LANGUAGES.containsAll(adapters.keySet())) {
      throw new IllegalArgumentException("debug adapter catalog contains an unsupported language");
    }
  }

  /** Fixed command and transport configured by the server administrator. */
  public record Adapter(String version, List<String> command, Transport transport) {
    public Adapter {
      if (version == null || version.isBlank() || version.length() > 128) {
        throw new IllegalArgumentException("debug adapter version must be specified");
      }
      command = List.copyOf(Objects.requireNonNull(command, "command"));
      if (command.isEmpty()
          || command.size() > 32
          || command.stream()
              .anyMatch(value -> value == null || value.isBlank() || value.length() > 4096)
          || !Path.of(command.getFirst()).isAbsolute()) {
        throw new IllegalArgumentException(
            "debug adapter command must use a fixed absolute executable");
      }
      transport = transport == null ? Transport.STDIO : transport;
      boolean hasPort = command.stream().anyMatch(argument -> argument.contains("{port}"));
      if (transport == Transport.TCP && !hasPort || transport == Transport.STDIO && hasPort) {
        throw new IllegalArgumentException("debug adapter command does not match its transport");
      }
    }
  }

  /** DAP messages may use stdio or an adapter's loopback-only TCP listener. */
  public enum Transport {
    STDIO,
    TCP
  }
}
