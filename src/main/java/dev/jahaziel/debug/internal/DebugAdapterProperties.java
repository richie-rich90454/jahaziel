/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.debug.internal;

import dev.jahaziel.debug.api.DebugService;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Server-owned DAP adapter commands. Browser requests cannot modify this catalog. */
@ConfigurationProperties(prefix = "jahaziel.debug")
public final class DebugAdapterProperties {
    private final Map<String, Adapter> adapters;

    /**
     * Creates the adapter catalog.
     *
     * @param adapters the configured adapters by language
     */
    public DebugAdapterProperties(Map<String, Adapter> adapters) {
        adapters = adapters == null ? Map.of() : Map.copyOf(adapters);
        if (!DebugService.LANGUAGES.containsAll(adapters.keySet())) {
            throw new IllegalArgumentException(
                    "debug adapter catalog contains an unsupported language");
        }
        this.adapters = adapters;
    }

    /** Returns the configured adapters by language. */
    public Map<String, Adapter> adapters() {
        return adapters;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof DebugAdapterProperties
                && adapters.equals(((DebugAdapterProperties) other).adapters);
    }

    @Override
    public int hashCode() {
        return adapters.hashCode();
    }

    @Override
    public String toString() {
        return "DebugAdapterProperties[adapters=" + adapters + "]";
    }

    /** Fixed command and transport configured by the server administrator. */
    public static final class Adapter {
        private final String version;
        private final List<String> command;
        private final String transport;

        /**
         * Creates an adapter definition.
         *
         * @param version the adapter version
         * @param command the fixed command and arguments
         * @param transport the DAP transport
         */
        public Adapter(String version, List<String> command, String transport) {
            if (version == null || version.isBlank() || version.length() > 128) {
                throw new IllegalArgumentException("debug adapter version must be specified");
            }
            command = List.copyOf(Objects.requireNonNull(command, "command"));
            if (command.isEmpty() || command.size() > 32 || !Path.of(command.get(0)).isAbsolute()) {
                throw new IllegalArgumentException(
                        "debug adapter command must use a fixed absolute executable");
            }
            for (String value : command) {
                if (value == null || value.isBlank() || value.length() > 4096) {
                    throw new IllegalArgumentException(
                            "debug adapter command must use a fixed absolute executable");
                }
            }
            transport =
                    transport == null || transport.trim().isEmpty()
                            ? "STDIO"
                            : transport.trim().toUpperCase(java.util.Locale.ROOT);
            if (!"STDIO".equals(transport) && !"TCP".equals(transport)) {
                throw new IllegalArgumentException("debug transport must be STDIO or TCP");
            }
            boolean hasPort = false;
            for (String argument : command) {
                if (argument.contains("{port}")) {
                    hasPort = true;
                    break;
                }
            }
            if ("TCP".equals(transport) && !hasPort || "STDIO".equals(transport) && hasPort) {
                throw new IllegalArgumentException(
                        "debug adapter command does not match its transport");
            }
            this.version = version;
            this.command = command;
            this.transport = transport;
        }

        /** Returns the adapter version. */
        public String version() {
            return version;
        }

        /** Returns the fixed command and arguments. */
        public List<String> command() {
            return command;
        }

        /** Returns the DAP transport name. */
        public String transport() {
            return transport;
        }

        /** True when the adapter uses a loopback TCP listener. */
        public boolean isTcp() {
            return "TCP".equals(transport);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Adapter)) {
                return false;
            }
            Adapter that = (Adapter) other;
            return version.equals(that.version)
                    && command.equals(that.command)
                    && transport.equals(that.transport);
        }

        @Override
        public int hashCode() {
            return Objects.hash(version, command, transport);
        }

        @Override
        public String toString() {
            return "Adapter[version="
                    + version
                    + ", command="
                    + command
                    + ", transport="
                    + transport
                    + "]";
        }
    }

    /** DAP messages may use stdio or an adapter's loopback-only TCP listener. */
    public static final class Transport {
        /** Standard input and output transport. */
        public static final Transport STDIO = new Transport("STDIO");

        /** Loopback TCP listener transport. */
        public static final Transport TCP = new Transport("TCP");

        private final String name;

        private Transport(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
