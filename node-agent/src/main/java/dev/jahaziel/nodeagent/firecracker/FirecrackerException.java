/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.firecracker;

/** Thrown when the Firecracker API refuses a call. Carries path, status, and body. */
public final class FirecrackerException extends RuntimeException {
  private final String path;
  private final int status;
  private final String body;

  /**
   * Creates the exception for an error status.
   *
   * @param path the request path
   * @param status the response status, or -1 when unreadable
   * @param body the response body
   */
  public FirecrackerException(String path, int status, String body) {
    super("Firecracker " + path + " failed with status " + status + ": " + body);
    this.path = path;
    this.status = status;
    this.body = body;
  }

  /**
   * Creates the exception for a transport failure.
   *
   * @param path the request path
   * @param cause the transport failure
   */
  public FirecrackerException(String path, Throwable cause) {
    super("Firecracker " + path + " unreachable", cause);
    this.path = path;
    this.status = -1;
    this.body = "";
  }

  /** Returns the request path. */
  public String path() {
    return path;
  }

  /** Returns the response status, or -1 when unreadable. */
  public int status() {
    return status;
  }

  /** Returns the response body. */
  public String body() {
    return body;
  }
}
