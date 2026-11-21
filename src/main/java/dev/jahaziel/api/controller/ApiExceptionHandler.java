/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.controller;

import dev.jahaziel.agenttools.api.AgentToolBusyException;
import dev.jahaziel.agenttools.api.AgentToolNotInstalledException;
import dev.jahaziel.api.dto.ApiError;
import dev.jahaziel.authorization.api.AccessDeniedException;
import dev.jahaziel.extension.api.ExtensionPolicyNotFoundException;
import dev.jahaziel.identity.api.DevConnectException;
import dev.jahaziel.identity.api.LoginRejectedException;
import dev.jahaziel.identity.api.UserNotFoundException;
import dev.jahaziel.identity.auth.basis.BasisOAuthException;
import dev.jahaziel.language.api.LanguagePackInstallException;
import dev.jahaziel.node.api.NodeNotFoundException;
import dev.jahaziel.policy.api.PolicyNotFoundException;
import dev.jahaziel.runtime.api.NodeAgentUnreachableException;
import dev.jahaziel.security.SecretRedactor;
import dev.jahaziel.snapshot.api.SnapshotNotFoundException;
import dev.jahaziel.terminal.api.TerminalLimitException;
import dev.jahaziel.terminal.api.TerminalNotFoundException;
import dev.jahaziel.terminal.api.TerminalUnavailableException;
import dev.jahaziel.workspace.api.WorkspaceFileConflictException;
import dev.jahaziel.workspace.api.WorkspaceNotFoundException;
import dev.jahaziel.workspace.domain.IllegalWorkspaceStateException;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Maps domain failures to fixed error bodies. Raw exception messages and IdP URLs stay in the
 * server log behind a correlation id; a caller only ever sees a handwritten message.
 */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Maps missing workspaces to 404. */
    @ExceptionHandler(WorkspaceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError workspaceNotFound(WorkspaceNotFoundException failure) {
        return respond("NOT_FOUND", "Workspace not found", failure);
    }

    /** Maps missing users to 404. */
    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError userNotFound(UserNotFoundException failure) {
        return respond("NOT_FOUND", "User not found", failure);
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
        return respond("NOT_FOUND", "Resource not found", failure);
    }

    /** Maps refused access to 403 with who and what kept server-side. */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiError accessDenied(AccessDeniedException failure) {
        Map<String, String> details = new LinkedHashMap<String, String>();
        details.put("user", failure.userId().value().toString());
        details.put("action", failure.action());
        return respond("FORBIDDEN", "Forbidden", failure, details);
    }

    /** Maps a refused login to 403. An operator resolves it. */
    @ExceptionHandler(LoginRejectedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiError loginRejected(LoginRejectedException failure) {
        return respond("LOGIN_REJECTED", "Login was rejected", failure);
    }

    /** Surfaces the documented basis-auth code and recovery guidance, never a raw IdP URL. */
    @ExceptionHandler(BasisOAuthException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError basisOAuthFailed(BasisOAuthException failure) {
        Map<String, String> details = new LinkedHashMap<String, String>();
        details.put("code", String.valueOf(failure.code()));
        details.put("recovery", failure.recovery());
        return respond(
                String.valueOf(failure.code()),
                "Authentication with the identity provider failed",
                failure,
                details);
    }

    /** Maps a failed DevConnect step to 500, logging the target and cause for the operator. */
    @ExceptionHandler(DevConnectException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError devConnectFailed(DevConnectException failure) {
        return respond("DEVCONNECT_FAILED", "An external connection step failed", failure);
    }

    /** Maps unreachable node agents to 503. Starts fail safe, never fail open. */
    @ExceptionHandler(NodeAgentUnreachableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiError nodeAgentUnreachable(NodeAgentUnreachableException failure) {
        return respond("NODE_AGENT_UNREACHABLE", "Node agent not responding", failure);
    }

    /** Maps terminal host failures to 503. */
    @ExceptionHandler(TerminalUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiError terminalUnavailable(TerminalUnavailableException failure) {
        return respond("TERMINAL_UNAVAILABLE", "Terminal is unavailable", failure);
    }

    /** Maps terminal capacity failures to 429. */
    @ExceptionHandler(TerminalLimitException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ApiError terminalLimit(TerminalLimitException failure) {
        return respond("TERMINAL_LIMIT", "Terminal capacity reached", failure);
    }

    /** Maps managed tool lifecycle conflicts to 409. */
    @ExceptionHandler({AgentToolBusyException.class, AgentToolNotInstalledException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError agentToolConflict(RuntimeException failure) {
        return respond("AGENT_TOOL_CONFLICT", "Managed tool conflict", failure);
    }

    /**
     * Maps opt-in language-tool download failures without returning source or filesystem details.
     */
    @ExceptionHandler(LanguagePackInstallException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiError languagePackInstallFailed(LanguagePackInstallException failure) {
        return respond(
                "LANGUAGE_PACK_INSTALL_FAILED",
                "Language pack could not be installed; retry or inspect the server log",
                failure);
    }

    /** Maps illegal lifecycle transitions to 409. */
    @ExceptionHandler(IllegalWorkspaceStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError illegalState(IllegalWorkspaceStateException failure) {
        return respond("STATE_CONFLICT", "Workspace state does not allow this action", failure);
    }

    /** Maps stale editor saves to 412 so the workbench can offer a reload or merge. */
    @ExceptionHandler(WorkspaceFileConflictException.class)
    @ResponseStatus(HttpStatus.PRECONDITION_FAILED)
    public ApiError fileConflict(WorkspaceFileConflictException failure) {
        return respond("FILE_CONFLICT", "The file has changed since it was loaded", failure);
    }

    /** Maps bad input to 400 without exposing raw messages or local paths. */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError badInput(IllegalArgumentException failure) {
        return respond("VALIDATION_FAILED", "The request contains an invalid value", failure);
    }

    /** Maps an unknown path to 404. The query string is never logged: OAuth codes land there. */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError noResource(NoResourceFoundException failure, HttpServletRequest request) {
        String path = Objects.requireNonNullElse(failure.getResourcePath(), "");
        String remote = request == null ? "-" : request.getRemoteAddr();
        String host = request == null ? "-" : String.valueOf(request.getHeader("Host"));
        String agent = request == null ? "-" : String.valueOf(request.getHeader("User-Agent"));
        String accept = request == null ? "-" : String.valueOf(request.getHeader("Accept"));
        String correlationId = UUID.randomUUID().toString();
        log.warn(
                "NOT_FOUND [{}] {} {} from {} host={} agent={} accept={} - {}",
                correlationId,
                failure.getHttpMethod(),
                path,
                remote,
                SecretRedactor.redact(host),
                SecretRedactor.redact(agent),
                SecretRedactor.redact(accept),
                SecretRedactor.redact(String.valueOf(failure.getMessage())));
        return new ApiError("NOT_FOUND", "No such endpoint", correlationId, Map.of("path", path));
    }

    /** Maps bean validation failures to 400. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError invalidBody(MethodArgumentNotValidException failure) {
        return respond("VALIDATION_FAILED", "Request body is invalid", failure);
    }

    /** Maps everything else to 500 without leaking detail. The trace id links to the log. */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError unexpected(Exception failure) {
        String correlationId = UUID.randomUUID().toString();
        log.error(
                "INTERNAL_ERROR [{}] {}",
                correlationId,
                SecretRedactor.redact(String.valueOf(failure.getMessage())),
                failure);
        return new ApiError("INTERNAL_ERROR", "Unexpected error", correlationId, Map.of());
    }

    private static ApiError respond(String code, String message, Exception cause) {
        return respond(code, message, cause, Map.of());
    }

    private static ApiError respond(
            String code, String message, Exception cause, Map<String, String> details) {
        String correlationId = UUID.randomUUID().toString();
        log.warn(
                "{} [{}] {}",
                code,
                correlationId,
                SecretRedactor.redact(String.valueOf(cause.getMessage())),
                cause);
        return new ApiError(code, message, correlationId, details);
    }
}
