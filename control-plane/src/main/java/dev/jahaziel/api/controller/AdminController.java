/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.controller;

import dev.jahaziel.administration.api.AdminService;
import dev.jahaziel.api.dto.AdminActionRequest;
import dev.jahaziel.api.dto.AdminHealthDto;
import dev.jahaziel.api.dto.AuditRecordDto;
import dev.jahaziel.api.dto.CreatePolicyRequest;
import dev.jahaziel.api.dto.NodeDto;
import dev.jahaziel.api.dto.UserDto;
import dev.jahaziel.api.dto.WorkspaceDto;
import dev.jahaziel.audit.api.AuditService;
import dev.jahaziel.authorization.api.AccessDeniedException;
import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.monitoring.api.HourlyRollup;
import dev.jahaziel.monitoring.api.MetricsService;
import dev.jahaziel.node.api.NodeService;
import dev.jahaziel.node.domain.NodeId;
import dev.jahaziel.policy.api.PolicyDoc;
import dev.jahaziel.policy.api.PolicyService;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.domain.WorkspaceId;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Administrative endpoints. Every call requires the administrator role and enrolled MFA. */
@RestController
@RequestMapping("/admin/v1")
public class AdminController {
  private final AdminService admin;
  private final AuthorizationService auth;
  private final UserService users;
  private final WorkspaceService workspaces;
  private final NodeService nodes;
  private final AuditService audit;
  private final PolicyService policies;
  private final MetricsService metrics;
  private final String appDomain;

  /**
   * Creates the controller.
   *
   * @param admin the administration service
   * @param auth the authorization checks
   * @param users the user lookup
   * @param workspaces the workspace lookup
   * @param nodes the node lookup
   * @param audit the audit log
   * @param policies the policy store
   * @param metrics the metric rollups
   * @param appDomain the public domain
   */
  public AdminController(
      AdminService admin,
      AuthorizationService auth,
      UserService users,
      WorkspaceService workspaces,
      NodeService nodes,
      AuditService audit,
      PolicyService policies,
      MetricsService metrics,
      @Value("${jahaziel.domain}") String appDomain) {
    this.admin = admin;
    this.auth = auth;
    this.users = users;
    this.workspaces = workspaces;
    this.nodes = nodes;
    this.audit = audit;
    this.policies = policies;
    this.metrics = metrics;
    this.appDomain = appDomain;
  }

  /**
   * Lists all users.
   *
   * @param principal the acting administrator
   * @return the user DTOs
   */
  @GetMapping("/users")
  public List<UserDto> users(Principal principal) {
    requireAdmin(principal);
    return admin.listUsers().stream()
        .map(
            user ->
                new UserDto(
                    user.id().value(),
                    user.email(),
                    user.displayName(),
                    user.roles(),
                    user.mfaEnabled()))
        .toList();
  }

  /**
   * Suspends a user.
   *
   * @param id the user identifier
   * @param request the reason
   * @param principal the acting administrator
   */
  @PostMapping("/users/{id}/suspend")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void suspend(
      @PathVariable UUID id, @Valid @RequestBody AdminActionRequest request, Principal principal) {
    UserId adminUser = requireAdmin(principal);
    admin.suspendUser(new UserId(id), request.reason(), adminUser);
  }

  /**
   * Unsuspends a user.
   *
   * @param id the user identifier
   * @param request the reason
   * @param principal the acting administrator
   */
  @PostMapping("/users/{id}/unsuspend")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unsuspend(
      @PathVariable UUID id, @Valid @RequestBody AdminActionRequest request, Principal principal) {
    UserId adminUser = requireAdmin(principal);
    admin.unsuspendUser(new UserId(id), request.reason(), adminUser);
  }

  /**
   * Lists all workspaces.
   *
   * @param principal the acting administrator
   * @return the workspace DTOs
   */
  @GetMapping("/workspaces")
  public List<WorkspaceDto> workspaces(Principal principal) {
    requireAdmin(principal);
    return admin.listWorkspaces().stream().map(view -> WorkspaceDto.from(view, appDomain)).toList();
  }

  /**
   * Deletes a workspace.
   *
   * @param id the workspace identifier
   * @param principal the acting administrator
   */
  @PostMapping("/workspaces/{id}/delete")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteWorkspace(@PathVariable UUID id, Principal principal) {
    requireAdmin(principal);
    workspaces.delete(new WorkspaceId(id));
  }

  /**
   * Lists all nodes.
   *
   * @param principal the acting administrator
   * @return the node DTOs
   */
  @GetMapping("/nodes")
  public List<NodeDto> nodes(Principal principal) {
    requireAdmin(principal);
    return nodes.list().stream().map(NodeDto::from).toList();
  }

  /**
   * Drains a node.
   *
   * @param id the node identifier
   * @param principal the acting administrator
   */
  @PostMapping("/nodes/{id}/drain")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void drain(@PathVariable String id, Principal principal) {
    requireAdmin(principal);
    nodes.drain(new NodeId(id));
  }

  /**
   * Queries the audit log, newest first.
   *
   * @param limit the maximum events
   * @param principal the acting administrator
   * @return the event DTOs
   */
  @GetMapping("/audit/query")
  public List<AuditRecordDto> audit(
      @RequestParam(defaultValue = "100") int limit, Principal principal) {
    requireAdmin(principal);
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be positive");
    }
    return audit.latest(Math.min(limit, 1000)).stream().map(AuditRecordDto::from).toList();
  }

  /**
   * Lists policies for a scope.
   *
   * @param scope the policy scope
   * @param scopeId the scope target id, empty for global scope
   * @param principal the acting administrator
   * @return the policy documents
   */
  @GetMapping("/policy")
  public List<PolicyDoc> policy(
      @RequestParam String scope,
      @RequestParam(defaultValue = "") String scopeId,
      Principal principal) {
    requireAdmin(principal);
    return policies.listByScope(scope, scopeId.isEmpty() ? null : scopeId);
  }

  /**
   * Stores a policy document. Creates a new document on every call.
   *
   * @param request the document
   * @param principal the acting administrator
   * @return the stored document
   */
  @PostMapping("/policy")
  @ResponseStatus(HttpStatus.CREATED)
  public PolicyDoc putPolicy(@RequestBody CreatePolicyRequest request, Principal principal) {
    UserId admin = requireAdmin(principal);
    return policies.create(
        request.scope(), request.scopeId(), request.policyJson(), admin.value().toString());
  }

  /**
   * Deletes a policy document.
   *
   * @param id the policy identifier
   * @param principal the acting administrator
   */
  @DeleteMapping("/policy/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deletePolicy(@PathVariable UUID id, Principal principal) {
    requireAdmin(principal);
    policies.delete(id);
  }

  /**
   * Returns the newest rollup of a workspace.
   *
   * @param workspaceId the workspace identifier
   * @param principal the acting administrator
   * @return zero or one rollups
   */
  @GetMapping("/metrics")
  public List<HourlyRollup> metrics(@RequestParam UUID workspaceId, Principal principal) {
    requireAdmin(principal);
    return metrics.latest(new WorkspaceId(workspaceId)).stream().toList();
  }

  /**
   * Returns control plane health.
   *
   * @param principal the acting administrator
   * @return the health DTO
   */
  @GetMapping("/health")
  public AdminHealthDto health(Principal principal) {
    requireAdmin(principal);
    return new AdminHealthDto("ok", nodes.list().size(), admin.listWorkspaces().size());
  }

  private UserId requireAdmin(Principal principal) {
    UserId user;
    try {
      user = new UserId(UUID.fromString(principal.getName()));
    } catch (IllegalArgumentException failure) {
      throw new IllegalArgumentException("principal is not a user id");
    }
    if (!auth.isAdmin(user)) {
      throw new AccessDeniedException(user, "admin");
    }
    if (!users.isMfaEnabled(user)) {
      throw new AccessDeniedException(user, "admin:mfa");
    }
    return user;
  }
}
