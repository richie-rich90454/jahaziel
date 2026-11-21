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

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.Principal;
import java.time.Duration;
import java.util.UUID;

/**
 * Applies API quotas and request rates to {@code /api/v1/**}. Decisions come from the existing ops
 * services; when a service is absent the request passes through, and exact health paths are never
 * limited. Authentication endpoints are covered by the nested {@link AuthRateLimitFilter}.
 */
public final class OpsLimitFilter implements Filter {
    /** Prefix this filter guards. */
    public static final String API_PREFIX = "/api/v1/";

    private static final String[] HEALTH_PATHS =
            new String[] {"/api/v1/health", "/api/v1/healthz", "/api/v1/actuator/health"};

    private static final String AUTH_ROOT = "/api/auth";
    private static final String AUTH_PREFIX = "/api/auth/";
    private static final String LOGIN_PATH = "/api/login";

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
        for (int i = 0; i < HEALTH_PATHS.length; i++) {
            if (HEALTH_PATHS[i].equals(path)) {
                return false;
            }
        }
        return true;
    }

    static boolean isAuthPath(String path) {
        if (path == null) {
            return false;
        }
        return AUTH_ROOT.equals(path) || path.startsWith(AUTH_PREFIX) || LOGIN_PATH.equals(path);
    }

    private static String authKey(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        String identity = null;
        String[] names =
                new String[] {"username", "j_username", "email", "user", "login", "identity"};
        for (int i = 0; i < names.length && identity == null; i++) {
            String value = request.getParameter(names[i]);
            if (value != null && !value.trim().isEmpty()) {
                identity = value.trim();
            }
        }
        Principal principal = request.getUserPrincipal();
        if (identity == null
                && principal != null
                && principal.getName() != null
                && !principal.getName().isEmpty()) {
            identity = principal.getName();
        }
        return "ip:"
                + (address == null ? "unknown" : address)
                + (identity == null ? "" : "|id:" + identity);
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

    /**
     * Strict, IP-and-identity scoped rate limit for authentication endpoints. Registered as a
     * component filter so it covers the whole auth surface, not just {@code /api/v1/**}.
     */
    @Component
    @Order(Ordered.HIGHEST_PRECEDENCE + 100)
    public static final class AuthRateLimitFilter extends OncePerRequestFilter {
        /** Attempts allowed per window. */
        static final int LIMIT = 10;

        /** Window length in milliseconds. */
        static final long WINDOW_MILLIS = 60000L;

        private final RateLimiter limiter;
        private final int limit;
        private final long windowMillis;

        /** Creates the auto-registered filter with strict defaults. */
        public AuthRateLimitFilter() {
            this(new RateLimiter(), LIMIT, WINDOW_MILLIS);
        }

        /** Creates a filter with an explicit limiter, useful for tests. */
        AuthRateLimitFilter(RateLimiter limiter, int limit, long windowMillis) {
            if (limiter == null || limit <= 0 || windowMillis <= 0L) {
                throw new IllegalArgumentException("invalid auth rate-limit configuration");
            }
            this.limiter = limiter;
            this.limit = limit;
            this.windowMillis = windowMillis;
        }

        @Override
        protected void doFilterInternal(
                HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String path = request.getRequestURI();
            if (!isAuthPath(path)) {
                chain.doFilter(request, response);
                return;
            }
            LimitDecision decision =
                    limiter.tryAcquire(authKey(request), limit, Duration.ofMillis(windowMillis));
            if (!decision.allowed()) {
                writeProblem(
                        response,
                        path,
                        LimitDecision.throttled("Too many authentication attempts"));
                return;
            }
            chain.doFilter(request, response);
        }
    }
}
