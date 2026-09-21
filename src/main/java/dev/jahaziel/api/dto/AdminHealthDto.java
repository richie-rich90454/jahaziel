/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.dto;

/**
 * Control plane health for reads.
 *
 * @param status the status, ok when serving
 * @param nodes the registered node count
 * @param workspaces the workspace count
 */
public record AdminHealthDto(String status, int nodes, int workspaces) {}
