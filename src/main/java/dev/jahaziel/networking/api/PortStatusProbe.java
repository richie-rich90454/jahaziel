/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.networking.api;

/** Checks whether a registered local workspace port accepts a TCP connection. */
public abstract class PortStatusProbe {
    /**
     * Checks whether the loopback endpoint is accepting connections on a port.
     *
     * @param port the registered TCP port
     * @return true when a connection can be established
     */
    public abstract boolean isListening(int port);
}
