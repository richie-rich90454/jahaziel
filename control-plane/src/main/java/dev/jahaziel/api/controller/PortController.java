/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.controller;

import dev.jahaziel.api.dto.PortDto;
import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.networking.api.PortService;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** User-facing port endpoints. */
@RestController
@RequestMapping("/api/v1")
public class PortController {
  private final PortService ports;
  private final UserService users;
  private final String appDomain;

  /**
   * Creates the controller.
   *
   * @param ports the port service
   * @param users the user lookup for URL slugs
   * @param appDomain the public domain
   */
  public PortController(
      PortService ports, UserService users, @Value("${jahaziel.domain}") String appDomain) {
    this.ports = ports;
    this.users = users;
    this.appDomain = appDomain;
  }

  /**
   * Lists a workspace's registered ports.
   *
   * @param id the workspace identifier
   * @return the port DTOs
   */
  @GetMapping("/workspaces/{id}/ports")
  public List<PortDto> list(@PathVariable UUID id) {
    var bindings = ports.bindingsByWorkspace(new WorkspaceId(id));
    return bindings.stream()
        .map(binding -> PortDto.from(binding, slugOf(binding), appDomain))
        .toList();
  }

  private String slugOf(dev.jahaziel.networking.api.PortBinding binding) {
    return users.slug(new UserId(binding.userId()));
  }
}
