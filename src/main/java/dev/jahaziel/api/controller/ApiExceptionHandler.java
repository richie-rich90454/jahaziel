/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.controller;

import dev.jahaziel.api.dto.ApiError;
import dev.jahaziel.authorization.api.AccessDeniedException;
import dev.jahaziel.extension.api.ExtensionPolicyNotFoundException;
import dev.jahaziel.identity.api.DevConnectException;
import dev.jahaziel.identity.api.LoginRejectedException;
import dev.jahaziel.identity.api.UserNotFoundException;
import dev.jahaziel.node.api.NodeNotFoundException;
import dev.jahaziel.policy.api.PolicyNotFoundException;
import dev.jahaziel.runtime.api.NodeAgentUnreachableException;
import dev.jahaziel.snapshot.api.SnapshotNotFoundException;
import dev.jahaziel.terminal.api.TerminalNotFoundException;
import dev.jahaziel.workspace.api.WorkspaceNotFoundException;
import dev.jahaziel.workspace.domain.IllegalWorkspaceStateException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps domain failures to error bodies. User messages are handwritten; stack traces never leave the
 * server.
 */
@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  /** Maps missing workspaces to 404. */
  @ExceptionHandler(WorkspaceNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiError workspaceNotFound(WorkspaceNotFoundException failure) {
    return error("NOT_FOUND", "Workspace not found");
  }

  /** Maps missing users to 404. */
  @ExceptionHandler(UserNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiError userNotFound(UserNotFoundException failure) {
    return error("NOT_FOUND", "User not found");
  }

  /** Maps missing snapshots, nodes, terminals, and policies to 404. */
  @ExceptionHandler({
    SnapshotNotFoundException.class,
    NodeNotFoundException.class,
    TerminalNotFoundException.class,
    PolicyNotFoundException.class,
    ExtensionPolicyNotFoundException.class
  })
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiError resourceNotFound(RuntimeException failure) {
    return error("NOT_FOUND", "Resource not found");
  }

  /** Maps refused access to 403 with who and what. */
  @ExceptionHandler(AccessDeniedException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public ApiError accessDenied(AccessDeniedException failure) {
    return new ApiError(
        "FORBIDDEN",
        "Forbidden",
        UUID.randomUUID().toString(),
        Map.of("user", failure.userId().value().toString(), "action", failure.action()));
  }

  /** Maps a refused login to 403. An operator resolves it. */
  @ExceptionHandler(LoginRejectedException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public ApiError loginRejected(LoginRejectedException failure) {
    return error("LOGIN_REJECTED", failure.getMessage());
  }

  /** Maps a failed DevConnect step to 500, naming the target and cause for the operator. */
  @ExceptionHandler(DevConnectException.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ApiError devConnectFailed(DevConnectException failure) {
    return error("DEVCONNECT_FAILED", failure.getMessage());
  }

  /** Maps unreachable node agents to 503. Starts fail safe, never fail open. */
  @ExceptionHandler(NodeAgentUnreachableException.class)
  @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
  public ApiError nodeAgentUnreachable(NodeAgentUnreachableException failure) {
    return error("NODE_AGENT_UNREACHABLE", "Node agent not responding");
  }

  /** Maps illegal lifecycle transitions to 409. */
  @ExceptionHandler(IllegalWorkspaceStateException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ApiError illegalState(IllegalWorkspaceStateException failure) {
    return error("STATE_CONFLICT", "Workspace state does not allow this action");
  }

  /** Maps bad input to 400. */
  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiError badInput(IllegalArgumentException failure) {
    return error("VALIDATION_FAILED", failure.getMessage());
  }

  /** Maps an unknown path to 404, not 500: nothing is broken, the route does not exist. */
  @ExceptionHandler(NoResourceFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiError noResource(NoResourceFoundException failure, HttpServletRequest request) {
    String path = Objects.requireNonNullElse(failure.getResourcePath(), "");
    String remote = request == null ? "-" : request.getRemoteAddr();
    String host = request == null ? "-" : String.valueOf(request.getHeader("Host"));
    String agent = request == null ? "-" : String.valueOf(request.getHeader("User-Agent"));
    String rawTarget =
        request == null
            ? "-"
            : request.getRequestURI()
                + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
    String accept = request == null ? "-" : String.valueOf(request.getHeader("Accept"));
    log.warn(
        "no route for {} {} (raw {}) from {} host={} agent={} accept={}",
        failure.getHttpMethod(),
        path,
        rawTarget,
        remote,
        host,
        agent,
        accept);
    return new ApiError(
        "NOT_FOUND", "No such endpoint", UUID.randomUUID().toString(), Map.of("path", path));
  }

  /** Maps bean validation failures to 400. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiError invalidBody(MethodArgumentNotValidException failure) {
    return error("VALIDATION_FAILED", "Request body is invalid");
  }

  /** Maps everything else to 500 without leaking detail. */
  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ApiError unexpected(Exception failure) {
    return error("INTERNAL_ERROR", "Unexpected error");
  }

  private static ApiError error(String code, String message) {
    return new ApiError(code, message, UUID.randomUUID().toString(), Map.of());
  }
}
