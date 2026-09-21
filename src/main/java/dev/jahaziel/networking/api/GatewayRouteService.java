/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.networking.api;

/**
 * Keeps the gateway's routing table in step with the workspaces and ports the control plane knows.
 * Reconciliation is idempotent, so it is safe to run on every change and on a timer.
 */
public interface GatewayRouteService {
  /** Reconciles the gateway routes with the current running workspaces and their ports. */
  void reconcile();
}
