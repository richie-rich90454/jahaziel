/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
/**
 * Public plugin interfaces of the platform.
 *
 * <p>Every boundary that could be a plugin interface is one: identity, authorization, storage, node
 * backends, port routing, audit, metrics, notifications, extensions, images and secrets.
 * Implementations are discovered with {@link java.util.ServiceLoader}.
 */
package dev.jahaziel.spi;
