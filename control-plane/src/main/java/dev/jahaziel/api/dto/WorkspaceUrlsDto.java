/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.dto;

/**
 * Entry points of a workspace.
 *
 * @param ide the IDE websocket endpoint
 * @param terminal the terminal websocket endpoint
 */
public record WorkspaceUrlsDto(String ide, String terminal) {}
