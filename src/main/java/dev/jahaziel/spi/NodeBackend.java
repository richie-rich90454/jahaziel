/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

/** Runs workspace runtimes on a node. Firecracker is one implementation. */
@JahazielSpi(version = 1)
public interface NodeBackend {
  /**
   * Returns the backend name.
   *
   * @return the name, for example {@code firecracker}
   */
  String name();

  /**
   * Prepares the runtime shell for a workspace.
   *
   * @param request the prepare request
   * @return the storage handle
   */
  WorkspaceHandle prepare(PrepareRequest request);

  /**
   * Starts a workspace runtime.
   *
   * @param request the start request with resource limits
   * @return the runtime handle
   */
  RuntimeHandle start(RuntimeRequest request);

  /**
   * Stops a workspace runtime.
   *
   * @param handle the runtime handle
   * @param graceful true for SIGTERM, false for SIGKILL
   */
  void stop(RuntimeHandle handle, boolean graceful);

  /**
   * Destroys a workspace runtime shell.
   *
   * @param handle the storage handle
   */
  void destroy(WorkspaceHandle handle);
}
