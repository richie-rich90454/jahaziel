/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.dto;

/**
 * Quota view of a workspace. Used bytes stay zero until the metrics slice supplies live values.
 *
 * @param logicalBytesUsed logical bytes used
 * @param logicalBytesLimit logical byte limit
 * @param inodeCountUsed inodes used
 * @param inodeCountLimit inode limit
 */
public record WorkspaceQuotaDto(
    long logicalBytesUsed, long logicalBytesLimit, long inodeCountUsed, long inodeCountLimit) {}
