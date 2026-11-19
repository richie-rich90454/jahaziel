/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.perf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * In-memory workspace-to-pid index. Pure data structure: it never inspects or signals an operating
 * system process, so callers can adopt it without changing process lifecycle behavior.
 */
public final class ProcessIndex {
  private final Map<String, Set<Long>> byWorkspace = new HashMap<String, Set<Long>>();

  public void add(String workspaceId, long pid) {
    requireWorkspace(workspaceId);
    if (pid <= 0) {
      throw new IllegalArgumentException("pid must be positive");
    }
    Set<Long> pids = byWorkspace.get(workspaceId);
    if (pids == null) {
      pids = new HashSet<Long>();
      byWorkspace.put(workspaceId, pids);
    }
    pids.add(Long.valueOf(pid));
  }

  public boolean remove(String workspaceId, long pid) {
    requireWorkspace(workspaceId);
    Set<Long> pids = byWorkspace.get(workspaceId);
    if (pids == null) {
      return false;
    }
    boolean removed = pids.remove(Long.valueOf(pid));
    if (pids.isEmpty()) {
      byWorkspace.remove(workspaceId);
    }
    return removed;
  }

  public void removeWorkspace(String workspaceId) {
    requireWorkspace(workspaceId);
    byWorkspace.remove(workspaceId);
  }

  public Set<Long> pids(String workspaceId) {
    requireWorkspace(workspaceId);
    Set<Long> stored = byWorkspace.get(workspaceId);
    if (stored == null) {
      return new HashSet<Long>();
    }
    return new HashSet<Long>(stored);
  }

  public List<Long> list(String workspaceId) {
    List<Long> result = new ArrayList<Long>(pids(workspaceId));
    Collections.sort(result);
    return result;
  }

  public boolean contains(String workspaceId, long pid) {
    requireWorkspace(workspaceId);
    Set<Long> pids = byWorkspace.get(workspaceId);
    return pids != null && pids.contains(Long.valueOf(pid));
  }

  public int workspaceCount() {
    return byWorkspace.size();
  }

  private static void requireWorkspace(String workspaceId) {
    if (workspaceId == null || workspaceId.isEmpty()) {
      throw new IllegalArgumentException("workspaceId must not be empty");
    }
  }
}
