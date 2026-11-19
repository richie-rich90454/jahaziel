/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api;

import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.systemconfig.FeatureFlagService;
import dev.jahaziel.systemconfig.SystemConfigService;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

/** Administrator-only configuration and feature-flag endpoints. */
@RestController
@RequestMapping("/api/v1/admin")
public final class AdminConfigController extends ApiControllerBase {
    private static final MediaType PROBLEM_JSON = MediaType.valueOf("application/problem+json");

    private final SystemConfigService config;
    private final FeatureFlagService flags;

    public AdminConfigController(
            SystemConfigService config, FeatureFlagService flags, AuthorizationService auth) {
        super(auth);
        this.config = config;
        this.flags = flags;
    }

    @GetMapping("/config")
    public Map<String, String> config(Principal principal) {
        requireAdmin(principal);
        return config.view();
    }

    @PutMapping("/config")
    public Map<String, String> updateConfig(
            Principal principal, @RequestBody Map<String, String> body) {
        requireAdmin(principal);
        Map<String, String> changes = new LinkedHashMap<String, String>();
        String requestedPath = null;
        if (body != null) {
            for (Map.Entry<String, String> entry : body.entrySet()) {
                if (entry.getKey().startsWith("_")) {
                    if ("_path".equals(entry.getKey())) {
                        requestedPath = entry.getValue();
                    }
                    continue;
                }
                changes.put(entry.getKey(), entry.getValue());
            }
        }
        return config.update(changes, requestedPath);
    }

    @GetMapping("/flags")
    public Map<String, Object> flags(Principal principal) {
        requireAdmin(principal);
        return flags.view();
    }

    @PutMapping("/flags")
    public Map<String, Object> updateFlag(
            Principal principal, @RequestBody Map<String, Object> body) {
        requireAdmin(principal);
        String name = text(body, "name");
        String scope = text(body, "scope");
        String target = text(body, "target");
        if ("clear".equals(text(body, "action"))) {
            flags.clear(name, scope, target);
        } else {
            flags.set(name, scope, target, enabled(body));
        }
        return flags.view();
    }

    @ExceptionHandler(ApiFailureException.class)
    public ResponseEntity<ApiProblem> apiFailure(ApiFailureException failure) {
        return problem(failure.status(), failure.title(), failure.detail());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiProblem> invalid(IllegalArgumentException failure) {
        return problem(400, "Bad Request", "The request contains an invalid value");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiProblem> conflict(IllegalStateException failure) {
        return problem(409, "Conflict", "The current state does not allow this action");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiProblem> unexpected(Exception failure) {
        return problem(500, "Internal Server Error", "The operation could not be completed");
    }

    private static ResponseEntity<ApiProblem> problem(int status, String title, String detail) {
        ApiProblem body = new ApiProblem(title, status, detail, "");
        return ResponseEntity.status(status).contentType(PROBLEM_JSON).body(body);
    }

    private static String text(Map<String, Object> body, String key) {
        if (body == null || body.get(key) == null) {
            return null;
        }
        return String.valueOf(body.get(key));
    }

    private static boolean enabled(Map<String, Object> body) {
        Object value = body == null ? null : body.get("enabled");
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
