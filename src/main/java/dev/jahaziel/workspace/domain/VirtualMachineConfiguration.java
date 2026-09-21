/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.domain;

/**
 * Sizing of a virtual machine. Memory is reserved at start.
 *
 * @param vcpuCount virtual CPU count
 * @param memoryMib guest memory in MiB
 */
public record VirtualMachineConfiguration(int vcpuCount, long memoryMib) {
  public VirtualMachineConfiguration {
    if (vcpuCount < 1) {
      throw new IllegalArgumentException("vcpuCount must be positive");
    }
    if (memoryMib < 1) {
      throw new IllegalArgumentException("memoryMib must be positive");
    }
  }
}
