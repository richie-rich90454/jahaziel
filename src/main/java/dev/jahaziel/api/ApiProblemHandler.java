/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api;

import dev.jahaziel.agent.AgentException;
import dev.jahaziel.authorization.api.AccessDeniedException;
import dev.jahaziel.club.api.ClubAccessDeniedException;
import dev.jahaziel.club.api.ClubLimitException;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps API-boundary and module failures to RFC 9457 bodies. Details are always fixed strings; a
 * domain exception message or a filesystem path is never returned to a caller.
 */
@RestControllerAdvice(
        assignableTypes = {
            AgentSessionController.class,
            AgentPromptController.class,
            AgentDiffController.class,
            AgentChatController.class,
            AgentMetaController.class,
            SqliteController.class,
            WorkspaceDataController.class,
            ClubSessionController.class,
            ClubLearningController.class,
            ClubModerationController.class,
            OpsAdminController.class
        })
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class ApiProblemHandler {
    private static final MediaType PROBLEM_JSON = MediaType.valueOf("application/problem+json");

    @ExceptionHandler(ApiFailureException.class)
    public ResponseEntity<ApiProblem> apiFailure(
            ApiFailureException failure, HttpServletRequest request) {
        return problem(failure.status(), failure.title(), failure.detail(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiProblem> accessDenied(
            AccessDeniedException failure, HttpServletRequest request) {
        return problem(403, "Forbidden", "You may not perform this action", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiProblem> invalidInput(
            IllegalArgumentException failure, HttpServletRequest request) {
        return problem(400, "Bad Request", "The request contains an invalid value", request);
    }

    @ExceptionHandler(AgentException.class)
    public ResponseEntity<ApiProblem> agentFailure(
            AgentException failure, HttpServletRequest request) {
        return problem(400, "Bad Request", "The agent service rejected the request", request);
    }

    @ExceptionHandler(ClubAccessDeniedException.class)
    public ResponseEntity<ApiProblem> clubAccessDenied(
            ClubAccessDeniedException failure, HttpServletRequest request) {
        return problem(403, "Forbidden", "You may not perform this action", request);
    }

    @ExceptionHandler(ClubLimitException.class)
    public ResponseEntity<ApiProblem> clubLimit(
            ClubLimitException failure, HttpServletRequest request) {
        return problem(429, "Too Many Requests", "The session limit was reached", request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiProblem> stateFailure(
            IllegalStateException failure, HttpServletRequest request) {
        return problem(409, "Conflict", "The current state does not allow this action", request);
    }

    @ExceptionHandler(java.io.UncheckedIOException.class)
    public ResponseEntity<ApiProblem> ioFailure(
            java.io.UncheckedIOException failure, HttpServletRequest request) {
        return problem(
                500, "Internal Server Error", "The operation could not be completed", request);
    }

    private static ResponseEntity<ApiProblem> problem(
            int status, String title, String detail, HttpServletRequest request) {
        String instance = request == null ? "" : request.getRequestURI();
        ApiProblem body = new ApiProblem(title, status, detail, instance);
        return ResponseEntity.status(status).contentType(PROBLEM_JSON).body(body);
    }
}
