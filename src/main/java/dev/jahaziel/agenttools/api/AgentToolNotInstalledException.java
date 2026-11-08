/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.agenttools.api;

/** Raised when an operation requires a managed coding tool that is not installed. */
public final class AgentToolNotInstalledException extends RuntimeException {
  public AgentToolNotInstalledException() {
    super("OpenCode is not installed for this user");
  }
}
