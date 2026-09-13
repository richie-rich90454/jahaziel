/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.internal;

import dev.jahaziel.platform.api.AdmissionDecision;

/**
 * Admission control. Capacity counts reserved memory, never actual RSS: a started VM's full
 * allocation is gone whether the guest uses it or not. Never kills a running VM to admit a new one.
 */
public final class AdmissionControl {
  private AdmissionControl() {}

  /**
   * Decides a start.
   *
   * @param totalMib node memory in MiB
   * @param startedMib sum of running VM memory in MiB
   * @param requestedMib the requested memory in MiB
   * @param systemReservedMib host OS reservation
   * @param controlPlaneReservedMib control plane reservation
   * @param headroomReservedMib page cache and buffer headroom
   * @param softFloorMib the degraded start size
   * @param softLimitRatio fraction of total memory where pressure starts
   * @return admit with a size, or refuse
   */
  public static AdmissionDecision evaluate(
      long totalMib,
      long startedMib,
      long requestedMib,
      long systemReservedMib,
      long controlPlaneReservedMib,
      long headroomReservedMib,
      long softFloorMib,
      double softLimitRatio) {
    long available =
        totalMib - systemReservedMib - controlPlaneReservedMib - headroomReservedMib - startedMib;
    if (requestedMib <= available) {
      return new AdmissionDecision.Admit(requestedMib);
    }
    if (requestedMib >= softFloorMib
        && softFloorMib <= available
        && (startedMib + softFloorMib) < totalMib * softLimitRatio) {
      return new AdmissionDecision.Admit(softFloorMib);
    }
    return new AdmissionDecision.Refuse("Insufficient memory");
  }
}
