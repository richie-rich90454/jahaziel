/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.dist;

import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.identity.persistence.AppUserEntity;
import dev.jahaziel.identity.persistence.AppUserRepository;
import dev.jahaziel.identity.persistence.UserRoleEntity;
import dev.jahaziel.identity.persistence.UserRoleRepository;
import dev.jahaziel.node.domain.NodeId;
import dev.jahaziel.workspace.api.WorkspaceService;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Seeds the demo account and workspace for {@code --demo} boots. Runs only when {@code
 * jahaziel.demo} is true; every step is existence-checked, so restarts are idempotent.
 */
@Component
@ConditionalOnProperty(name = "jahaziel.demo", havingValue = "true")
public final class DemoSeeder implements ApplicationRunner {
  /** Fixed demo identity, also used as the demo admin fallback. */
  public static final UserId DEMO_USER =
      new UserId(UUID.fromString("00000000-0000-4000-8000-000000000001"));

  /** Name of the seeded workspace. */
  public static final String DEMO_WORKSPACE = "demo";

  private final AppUserRepository users;
  private final UserRoleRepository roles;
  private final WorkspaceService workspaces;

  /**
   * Creates the seeder.
   *
   * @param users the user rows
   * @param roles the role grants
   * @param workspaces the workspace lifecycle
   */
  public DemoSeeder(
      AppUserRepository users, UserRoleRepository roles, WorkspaceService workspaces) {
    this.users = Objects.requireNonNull(users, "users");
    this.roles = Objects.requireNonNull(roles, "roles");
    this.workspaces = Objects.requireNonNull(workspaces, "workspaces");
  }

  @Override
  public void run(ApplicationArguments args) {
    ensureUser();
    ensureWorkspace();
  }

  private void ensureUser() {
    if (users.existsById(DEMO_USER.value())) {
      return;
    }
    Instant now = Instant.now();
    AppUserEntity row = new AppUserEntity();
    row.setUserId(DEMO_USER.value());
    row.setExternalId("demo");
    row.setEmail("demo@example.com");
    row.setDisplayName("Demo");
    row.setCreatedAt(now);
    row.setLastLoginAt(now);
    row.setStatus("active");
    row.setMfaEnabled(false);
    users.save(row);
    UserRoleEntity grant = new UserRoleEntity();
    grant.setId(new UserRoleEntity.Id(DEMO_USER.value(), "ROLE_ADMIN"));
    grant.setGrantedAt(now);
    grant.setGrantedBy("demo-seeder");
    roles.save(grant);
  }

  private void ensureWorkspace() {
    boolean present =
        workspaces.describeByOwner(DEMO_USER).stream()
            .anyMatch(view -> view.workspace().name().equals(DEMO_WORKSPACE));
    if (!present) {
      workspaces.create(DEMO_USER, DEMO_WORKSPACE, 2, 4096, new NodeId("demo-node"));
    }
  }
}
