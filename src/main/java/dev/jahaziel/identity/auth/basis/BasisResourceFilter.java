/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.basis;

import com.nimbusds.jwt.SignedJWT;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.text.ParseException;

/**
 * Enforces basis-auth access tokens on workspace API calls. Leaves app session, JWT, and cookie
 * authentication untouched: it only runs for a bearer token whose header is typ=at+jwt.
 */
public final class BasisResourceFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(BasisResourceFilter.class);
    private static final String WORKSPACES = "/api/v1/workspaces/";
    private static final String BEARER = "Bearer ";

    private final TokenVerifier verifier;
    private final BasisScopeGuard guard;
    private final String[] requiredScopes;
    private final String[] requiredPermissions;

    public BasisResourceFilter(
            TokenVerifier verifier,
            BasisScopeGuard guard,
            String[] requiredScopes,
            String[] requiredPermissions) {
        this.verifier = verifier;
        this.guard = guard;
        this.requiredScopes = requiredScopes;
        this.requiredPermissions = requiredPermissions;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || !uri.startsWith(WORKSPACES)) {
            return true;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            return true;
        }
        return !isBasisToken(header.substring(BEARER.length()).trim());
    }

    private static boolean isBasisToken(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            return jwt.getHeader().getType() != null
                    && "at+jwt".equals(jwt.getHeader().getType().getType());
        } catch (ParseException failure) {
            // A malformed JWT-shaped bearer must reach the verifier so it gets the uniform 401
            // instead of silently passing through as a non-basis credential.
            int dots = 0;
            for (int index = 0; index < token.length(); index++) {
                if (token.charAt(index) == '.') {
                    dots++;
                }
            }
            return dots == 2;
        }
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = request.getHeader("Authorization").substring(BEARER.length()).trim();
        try {
            TokenVerifier.VerifiedToken verified = verifier.verifyAccessToken(token);
            guard.requireScopes(verified.scopes(), requiredScopes);
            guard.requirePermissions(verified.permissions(), requiredPermissions);
        } catch (TokenVerifier.TokenRejectedException rejected) {
            log.debug("basis access token rejected: {}", rejected.getMessage());
            invalidToken(response);
            return;
        } catch (BasisScopeGuard.ScopeDeniedException denied) {
            log.debug("basis access token lacks a required grant: {}", denied.getMessage());
            forbidden(response, denied.code());
            return;
        } catch (BasisException failure) {
            log.debug("basis access token rejected: {}", failure.getMessage());
            invalidToken(response);
            return;
        } catch (RuntimeException failure) {
            log.warn("basis access token verification failed unexpectedly");
            invalidToken(response);
            return;
        }
        chain.doFilter(request, response);
    }

    private static void invalidToken(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\"");
        response.setContentType("application/json");
        response.getWriter()
                .write(
                        "{\"error\":\"invalid_token\","
                                + "\"error_description\":\"Invalid access token\"}");
    }

    private static void forbidden(HttpServletResponse response, String code) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter()
                .write("{\"error\":\"" + code + "\",\"error_description\":\"Access denied\"}");
    }
}
