/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.agenttools.api;

/** Raised when a managed-tool operation conflicts with an install already in progress. */
public final class AgentToolBusyException extends RuntimeException {
  public AgentToolBusyException() {
    super("managed tool installation is still running");
  }
}
