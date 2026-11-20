/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.identity.domain.UserId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Behavior of {@link Workspace}. */
class WorkspaceTest {
    private WorkspaceId workspaceId;
    private UserId owner;
    private Workspace workspace;

    @BeforeEach
    void setUp() {
        workspaceId = new WorkspaceId(UUID.randomUUID());
        owner = new UserId(UUID.randomUUID());
        WorkspaceQuota quota = new WorkspaceQuota(21474836480L, 1000000L);
        VirtualMachine vm =
                new VirtualMachine(
                        new VirtualMachineId(UUID.randomUUID()),
                        workspaceId,
                        new VirtualMachineConfiguration(2, 2048));
        workspace = new Workspace(workspaceId, owner, "club-website", quota, vm);
    }

    @Test
    void startsRequestedWithDefaults() {
        assertInstanceOf(WorkspaceState.Requested.class, workspace.state());
        assertEquals("club-website", workspace.name());
        assertTrue(workspace.isOwnedBy(owner));
        assertFalse(workspace.isOwnedBy(new UserId(UUID.randomUUID())));
        assertFalse(workspace.isActive());
        assertTrue(workspace.snapshots().isEmpty());
    }

    @Test
    void blankOrLongNamesAreRejected() {
        WorkspaceQuota quota = new WorkspaceQuota(21474836480L, 1000000L);
        VirtualMachine vm =
                new VirtualMachine(
                        new VirtualMachineId(UUID.randomUUID()),
                        workspaceId,
                        new VirtualMachineConfiguration(2, 2048));
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        new Workspace(workspaceId, owner, "  ", quota, vm);
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        new Workspace(workspaceId, owner, "x".repeat(65), quota, vm);
                    }
                });
    }

    @Test
    void abortBootSuspendsStartingWorkspaces() {
        workspace.prepare();
        workspace.boot();
        workspace.abortBoot();
        assertInstanceOf(WorkspaceState.Suspended.class, workspace.state());
        assertThrows(
                IllegalWorkspaceStateException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        workspace.abortBoot();
                    }
                });
    }

    @Test
    void rebootRestartsParkedWorkspaces() {
        workspace.prepare();
        workspace.boot();
        workspace.markReady();
        workspace.start();
        workspace.suspend();
        workspace.reboot();
        assertInstanceOf(WorkspaceState.Requested.class, workspace.state());
        assertThrows(
                IllegalWorkspaceStateException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        workspace.reboot();
                    }
                });
        workspace.prepare();
        assertThrows(
                IllegalWorkspaceStateException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        workspace.reboot();
                    }
                });
    }

    @Test
    void prepareRejectsNonRequested() {
        workspace.prepare();
        assertThrows(
                IllegalWorkspaceStateException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        workspace.prepare();
                    }
                });
        workspace.boot();
        assertThrows(
                IllegalWorkspaceStateException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        workspace.prepare();
                    }
                });
    }

    @Test
    void walksToActive() {
        workspace.prepare();
        assertInstanceOf(WorkspaceState.Preparing.class, workspace.state());
        workspace.boot();
        assertInstanceOf(WorkspaceState.Booting.class, workspace.state());
        workspace.markReady();
        assertInstanceOf(WorkspaceState.Ready.class, workspace.state());
        workspace.start();
        assertInstanceOf(WorkspaceState.Active.class, workspace.state());
        assertTrue(workspace.isActive());
    }

    @Test
    void startRequiresReadyOrIdle() {
        IllegalWorkspaceStateException failure =
                assertThrows(
                        IllegalWorkspaceStateException.class,
                        new org.junit.jupiter.api.function.Executable() {
                            @Override
                            public void execute() throws Throwable {
                                workspace.start();
                            }
                        });
        assertEquals(workspaceId, failure.workspaceId());
        assertEquals("start", failure.action());
    }

    @Test
    void walksThroughIdleToDestroy() {
        workspace.prepare();
        workspace.boot();
        workspace.markReady();
        workspace.start();
        workspace.markIdle(Instant.now().minusSeconds(3600));
        assertInstanceOf(WorkspaceState.Idle.class, workspace.state());
        workspace.suspend();
        assertInstanceOf(WorkspaceState.Suspended.class, workspace.state());
        workspace.resume();
        assertInstanceOf(WorkspaceState.Active.class, workspace.state());
        workspace.destroy();
        assertInstanceOf(WorkspaceState.Destroyed.class, workspace.state());
    }

    @Test
    void suspendRequiresActiveOrIdle() {
        workspace.prepare();
        workspace.boot();
        workspace.markReady();
        assertThrows(
                IllegalWorkspaceStateException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        workspace.suspend();
                    }
                });
    }

    @Test
    void resumeRequiresSuspended() {
        assertThrows(
                IllegalWorkspaceStateException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        workspace.resume();
                    }
                });
    }

    @Test
    void illegalTransitionsThrow() {
        assertThrows(
                IllegalWorkspaceStateException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        workspace.boot();
                    }
                });
        assertThrows(
                IllegalWorkspaceStateException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        workspace.markReady();
                    }
                });
        assertThrows(
                IllegalWorkspaceStateException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        workspace.markIdle(Instant.now());
                    }
                });
        workspace.destroy();
        assertThrows(
                IllegalWorkspaceStateException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        workspace.destroy();
                    }
                });
        assertThrows(
                IllegalWorkspaceStateException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        workspace.fail("boom");
                    }
                });
    }

    @Test
    void failRecordsReason() {
        workspace.fail("disk lost");
        WorkspaceState.Failed failed =
                assertInstanceOf(WorkspaceState.Failed.class, workspace.state());
        assertEquals("disk lost", failed.reason());
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        workspace.fail("  ");
                    }
                });
    }

    @Test
    void quotaPolicyGrantsAndSnapshotsAreVisible() {
        WorkspaceQuota quota = new WorkspaceQuota(42949672960L, 2000000L);
        workspace.changeQuota(quota);
        assertEquals(quota, workspace.quota());
        ResourcePolicy policy = new ResourcePolicy(4096, 4, 40);
        workspace.changePolicy(policy);
        assertEquals(policy, workspace.policy());
        UUID grantee = UUID.randomUUID();
        workspace.grant(grantee, "read");
        assertTrue(workspace.acl().grants(grantee, "read"));
        workspace.revoke(grantee, "read");
        assertFalse(workspace.acl().grants(grantee, "read"));
        UUID snapshot = UUID.randomUUID();
        workspace.recordSnapshot(snapshot);
        assertEquals(List.of(snapshot), workspace.snapshots());
    }
}
