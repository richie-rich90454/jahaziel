/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Coverage of the workspace persistence entities and their composite keys. */
class WorkspaceEntityTest {
  @Test
  void workspaceStoresAllFields() {
    WorkspaceEntity entity = new WorkspaceEntity();
    UUID workspaceId = UUID.randomUUID();
    UUID owner = UUID.randomUUID();
    entity.setWorkspaceId(workspaceId);
    entity.setOwnerUserId(owner);
    entity.setName("dev");
    entity.setState("READY");
    entity.setStateChangedAt(Instant.EPOCH);
    entity.setLogicalQuotaBytes(1024L);
    entity.setInodeQuota(100L);
    entity.setCreatedAt(Instant.EPOCH);
    entity.setDeletedAt(Instant.now());
    assertEquals(workspaceId, entity.getWorkspaceId());
    assertEquals(owner, entity.getOwnerUserId());
    assertEquals("dev", entity.getName());
    assertEquals("READY", entity.getState());
    assertEquals(Instant.EPOCH, entity.getStateChangedAt());
    assertEquals(1024L, entity.getLogicalQuotaBytes());
    assertEquals(100L, entity.getInodeQuota());
    assertEquals(Instant.EPOCH, entity.getCreatedAt());
  }

  @Test
  void workspaceEqualsById() {
    UUID workspaceId = UUID.randomUUID();
    WorkspaceEntity first = new WorkspaceEntity();
    first.setWorkspaceId(workspaceId);
    first.setName("a");
    WorkspaceEntity second = new WorkspaceEntity();
    second.setWorkspaceId(workspaceId);
    second.setName("b");
    assertTrue(first.equals(first));
    assertTrue(first.equals(second));
    assertEquals(first.hashCode(), second.hashCode());
    assertFalse(first.equals(null));
    assertFalse(first.equals((Object) "other"));
    second.setWorkspaceId(UUID.randomUUID());
    assertFalse(first.equals(second));
  }

  @Test
  void aclStoresFieldsAndCompares() {
    WorkspaceAclEntity entity = new WorkspaceAclEntity();
    WorkspaceAclEntity.Id id =
        new WorkspaceAclEntity.Id(UUID.randomUUID(), UUID.randomUUID(), "read");
    entity.setId(id);
    entity.setGrantedAt(Instant.EPOCH);
    entity.setGrantedBy("owner");
    assertEquals(id, entity.getId());
    assertEquals(Instant.EPOCH, entity.getGrantedAt());
    assertEquals("owner", entity.getGrantedBy());
    WorkspaceAclEntity same = new WorkspaceAclEntity();
    same.setId(id);
    assertTrue(entity.equals(entity));
    assertTrue(entity.equals(same));
    assertEquals(entity.hashCode(), same.hashCode());
    assertFalse(entity.equals(null));
    assertFalse(entity.equals((Object) "other"));
    WorkspaceAclEntity different = new WorkspaceAclEntity();
    different.setId(new WorkspaceAclEntity.Id(id.getWorkspaceId(), id.getGranteeUserId(), "x"));
    assertFalse(entity.equals(different));
  }

  @Test
  void aclIdStoresFieldsAndCompares() {
    UUID workspaceId = UUID.randomUUID();
    UUID grantee = UUID.randomUUID();
    WorkspaceAclEntity.Id id = new WorkspaceAclEntity.Id();
    id.setWorkspaceId(workspaceId);
    id.setGranteeUserId(grantee);
    id.setPermission("read");
    assertEquals(workspaceId, id.getWorkspaceId());
    assertEquals(grantee, id.getGranteeUserId());
    assertEquals("read", id.getPermission());
    WorkspaceAclEntity.Id same = new WorkspaceAclEntity.Id(workspaceId, grantee, "read");
    assertTrue(id.equals(id));
    assertTrue(id.equals(same));
    assertEquals(id.hashCode(), same.hashCode());
    assertFalse(id.equals(null));
    assertFalse(id.equals((Object) "other"));
    assertFalse(id.equals(new WorkspaceAclEntity.Id(UUID.randomUUID(), grantee, "read")));
    assertFalse(id.equals(new WorkspaceAclEntity.Id(workspaceId, UUID.randomUUID(), "read")));
    assertFalse(id.equals(new WorkspaceAclEntity.Id(workspaceId, grantee, "write")));
  }

  @Test
  void virtualMachineStoresAllFields() {
    VirtualMachineEntity entity = new VirtualMachineEntity();
    UUID vmId = UUID.randomUUID();
    UUID workspaceId = UUID.randomUUID();
    entity.setVmId(vmId);
    entity.setWorkspaceId(workspaceId);
    entity.setNodeId("node-a");
    entity.setState("RUNNING");
    entity.setVcpuCount(2);
    entity.setMemoryMib(2048L);
    entity.setHomePath("/data/home");
    entity.setJailPath("/data/jail");
    entity.setCreatedAt(Instant.EPOCH);
    entity.setDestroyedAt(Instant.now());
    assertEquals(vmId, entity.getVmId());
    assertEquals(workspaceId, entity.getWorkspaceId());
    assertEquals("node-a", entity.getNodeId());
    assertEquals("RUNNING", entity.getState());
    assertEquals(2, entity.getVcpuCount());
    assertEquals(2048L, entity.getMemoryMib());
    assertEquals("/data/home", entity.getHomePath());
    assertEquals("/data/jail", entity.getJailPath());
    assertEquals(Instant.EPOCH, entity.getCreatedAt());
  }

  @Test
  void virtualMachineEqualsById() {
    UUID vmId = UUID.randomUUID();
    VirtualMachineEntity first = new VirtualMachineEntity();
    first.setVmId(vmId);
    first.setState("a");
    VirtualMachineEntity second = new VirtualMachineEntity();
    second.setVmId(vmId);
    second.setState("b");
    assertTrue(first.equals(first));
    assertTrue(first.equals(second));
    assertEquals(first.hashCode(), second.hashCode());
    assertFalse(first.equals(null));
    assertFalse(first.equals((Object) "other"));
    second.setVmId(UUID.randomUUID());
    assertFalse(first.equals(second));
  }
}
