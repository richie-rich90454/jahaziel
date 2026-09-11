/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.dto;

import dev.jahaziel.node.domain.Node;
import java.util.Locale;

/**
 * A node for reads.
 *
 * @param nodeId the node identifier
 * @param name the human-readable name
 * @param region the hosting region
 * @param state the lowercase health state
 * @param cpuCores CPU core count
 * @param memoryMib memory in MiB
 * @param diskGib disk in GiB
 */
public record NodeDto(
    String nodeId,
    String name,
    String region,
    String state,
    int cpuCores,
    long memoryMib,
    long diskGib) {
  /**
   * Maps a node to a DTO.
   *
   * @param node the node
   * @return the DTO
   */
  public static NodeDto from(Node node) {
    return new NodeDto(
        node.id().value(),
        node.name(),
        node.region(),
        node.state().getClass().getSimpleName().toLowerCase(Locale.ROOT),
        node.capacity().cpuCores(),
        node.capacity().memoryMib(),
        node.capacity().diskGib());
  }
}
