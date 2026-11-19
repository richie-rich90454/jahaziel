/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.security.Principal;
import java.time.Duration;
import java.util.UUID;

/**
 * Applies API quotas and request rates to {@code /api/v1/**}. Decisions come from the existing ops
 * services; when a service is absent the request passes through, and health probes are never
 * limited.
 */
public final class OpsLimitFilter implements Filter {
    /** Prefix this filter guards. */
    public static final String API_PREFIX = "/api/v1/";

    private final QuotaService quotas;
    private final RateLimiter rateLimiter;
    private final int rateLimit;
    private final long windowMillis;
    private final long sessionLimit;

    /** Creates a filter; null services disable that check. */
    public OpsLimitFilter(
            QuotaService quotas,
            RateLimiter rateLimiter,
            int rateLimit,
            long windowMillis,
            long sessionLimit) {
        if (rateLimit <= 0 || windowMillis <= 0L || sessionLimit <= 0L) {
            throw new IllegalArgumentException("invalid ops limit configuration");
        }
        this.quotas = quotas;
        this.rateLimiter = rateLimiter;
        this.rateLimit = rateLimit;
        this.windowMillis = windowMillis;
        this.sessionLimit = sessionLimit;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest)
                || !(response instanceof HttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }
        HttpServletRequest http = (HttpServletRequest) request;
        HttpServletResponse reply = (HttpServletResponse) response;
        String path = http.getRequestURI();
        if (!guarded(path)) {
            chain.doFilter(request, response);
            return;
        }
        String caller = caller(http);
        boolean reserved = false;
        if (quotas != null) {
            LimitDecision decision =
                    quotas.reserve("user", caller, QuotaService.SESSION, 1L, sessionLimit);
            if (!decision.allowed()) {
                writeProblem(reply, path, decision);
                return;
            }
            reserved = true;
        }
        try {
            if (rateLimiter != null) {
                LimitDecision decision =
                        rateLimiter.tryAcquire(caller, rateLimit, Duration.ofMillis(windowMillis));
                if (!decision.allowed()) {
                    writeProblem(reply, path, decision);
                    return;
                }
            }
            chain.doFilter(request, response);
        } finally {
            if (reserved) {
                quotas.release("user", caller, QuotaService.SESSION, 1L);
            }
        }
    }

    private static boolean guarded(String path) {
        if (path == null || !path.startsWith(API_PREFIX)) {
            return false;
        }
        return path.indexOf("/health") < 0;
    }

    private static String caller(HttpServletRequest request) {
        Principal principal = request.getUserPrincipal();
        if (principal != null && principal.getName() != null && !principal.getName().isEmpty()) {
            return "user:" + principal.getName();
        }
        String address = request.getRemoteAddr();
        return "ip:" + (address == null ? "unknown" : address);
    }

    private static void writeProblem(HttpServletResponse reply, String path, LimitDecision decision)
            throws IOException {
        reply.setStatus(decision.statusCode());
        reply.setContentType("application/problem+json");
        reply.setCharacterEncoding("UTF-8");
        String title =
                decision.statusCode() == LimitDecision.TOO_MANY ? "Too Many Requests" : "Forbidden";
        StringBuilder body = new StringBuilder("{\"type\":\"about:blank\",\"title\":\"");
        body.append(title).append("\",\"status\":").append(decision.statusCode());
        body.append(",\"detail\":\"")
                .append(OpsFiles.jsonEscape(OpsFiles.sanitizeText(decision.reason())))
                .append('"');
        body.append(",\"instance\":\"").append(OpsFiles.jsonEscape(path)).append('"');
        body.append(",\"correlationId\":\"").append(UUID.randomUUID()).append("\"}");
        reply.getWriter().write(body.toString());
    }
}
