/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.storage;

/** Thrown when a host storage command fails. Carries the command, exit, and output. */
public final class StorageException extends RuntimeException {
  private final String command;
  private final int exitCode;
  private final String output;

  /**
   * Creates the exception for a nonzero exit.
   *
   * @param command the command line
   * @param exitCode the exit code
   * @param output the combined output
   */
  public StorageException(String command, int exitCode, String output) {
    super("Command failed with exit " + exitCode + ": " + command + ": " + output);
    this.command = command;
    this.exitCode = exitCode;
    this.output = output;
  }

  /**
   * Creates the exception for a launch failure.
   *
   * @param command the command line
   * @param cause the launch failure
   */
  public StorageException(String command, Throwable cause) {
    super("Command failed to start: " + command, cause);
    this.command = command;
    this.exitCode = -1;
    this.output = "";
  }

  /** Returns the command line. */
  public String command() {
    return command;
  }

  /** Returns the exit code, or -1 when the command never started. */
  public int exitCode() {
    return exitCode;
  }

  /** Returns the combined output. */
  public String output() {
    return output;
  }
}
