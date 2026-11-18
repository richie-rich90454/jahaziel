/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.token;

/** Optional audit sink, registered as a PluginContext service before initialization. */
public abstract class AuditListener {
    public abstract void onEvent(String event, String detail);
}
