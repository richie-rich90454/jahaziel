/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.internal;

import dev.jahaziel.identity.api.DevConnectService;
import dev.jahaziel.identity.api.JwtService;
import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.identity.auth.basis.BasisResourceFilter;
import dev.jahaziel.identity.auth.wiring.UserIdentityStore;
import dev.jahaziel.identity.domain.UserId;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Web security. Bearer tokens and DevConnect cookies authenticate the API; the login endpoints are
 * public because they are how a browser proves who it is. Actuator health and the workbench shell
 * stay public so unauthenticated browsers can reach its sign-in link. Demo mode additionally opens
 * the admin dashboard: localhost try-before-configure with no secrets behind it.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final JwtService tokens;
    private final UserService users;
    private final DevConnectService devconnect;
    private final boolean demo;

    /**
     * Creates the configuration.
     *
     * @param tokens the token service
     * @param users the user lookup
     * @param devconnect the DevConnect login service
     * @param demo true for the insecure localhost demo mode
     */
    public SecurityConfig(
            JwtService tokens,
            UserService users,
            DevConnectService devconnect,
            @Value("${jahaziel.demo:false}") boolean demo) {
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        this.users = Objects.requireNonNull(users, "users");
        this.devconnect = Objects.requireNonNull(devconnect, "devconnect");
        this.demo = demo;
    }

    /** Gives the basis resource filter its link lookup once the identity store bean is ready. */
    @Autowired
    public void setIdentityStore(UserIdentityStore identityStore) {
        BasisResourceFilter.setIdentityStore(identityStore);
    }

    /**
     * Builds the filter chain.
     *
     * @param http the http security
     * @return the chain
     */
    @Bean
    public SecurityFilterChain chain(HttpSecurity http) throws Exception {
        JwtAuthenticationFilter.UserLookup lookup = user -> users.get(user).roles();
        JwtAuthenticationFilter bearer = new JwtAuthenticationFilter(tokens, lookup);
        BffAuthenticationFilter cookies =
                new BffAuthenticationFilter(devconnect, tokens, users, demo);
        return http.authorizeHttpRequests(
                        exchanges -> {
                            exchanges
                                    .requestMatchers("/actuator/health", "/error")
                                    .permitAll()
                                    .requestMatchers(
                                            "/api/login",
                                            "/api/oauth/callback",
                                            "/api/oauth2/bcallback",
                                            "/api/auth/local",
                                            "/api/auth/passkey",
                                            "/api/auth/passkey/options",
                                            "/api/auth/ldap",
                                            "/api/auth/token")
                                    .permitAll()
                                    // Public brand assets: browsers fetch these cookieless
                                    // (manifest,
                                    // favicon, PWA icons), so gating them behind auth only produces
                                    // console 401s. They carry no secrets.
                                    .requestMatchers(
                                            "/favicon.ico",
                                            "/favicon.svg",
                                            "/icons/**",
                                            "/assets/**",
                                            "/site.webmanifest")
                                    .permitAll();
                            exchanges.requestMatchers("/", "/index.html").permitAll();
                            if (demo) {
                                exchanges
                                        .requestMatchers("/_admin", "/_admin/diagnostics")
                                        .permitAll();
                            }
                            exchanges.anyRequest().authenticated();
                        })
                .logout(logout -> logout.logoutSuccessUrl("/"))
                .sessionManagement(
                        sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(
                        csrf ->
                                csrf.ignoringRequestMatchers(
                                        PathPatternRequestMatcher.withDefaults().matcher("/api/**"),
                                        request -> authorization(request) != null))
                .exceptionHandling(
                        handling -> handling.authenticationEntryPoint(this::onUnauthenticated))
                .addFilterBefore(cookies, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(
                        new BasisPrincipalFilter(users), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(
                        new SkipBasisBearer(bearer), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new CookieWriteOriginFilter(), BffAuthenticationFilter.class)
                .build();
    }

    /** Authenticates a workspace call whose basis token was already verified upstream. */
    private static final class BasisPrincipalFilter extends OncePerRequestFilter {
        private final UserService users;

        private BasisPrincipalFilter(UserService users) {
            this.users = users;
        }

        @Override
        protected void doFilterInternal(
                HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            Object attribute = request.getAttribute(BasisResourceFilter.PRINCIPAL_ATTRIBUTE);
            if (attribute instanceof String
                    && SecurityContextHolder.getContext().getAuthentication() == null) {
                try {
                    UserId user = new UserId(UUID.fromString((String) attribute));
                    List<String> roles = users.get(user).roles();
                    List<SimpleGrantedAuthority> authorities =
                            new ArrayList<SimpleGrantedAuthority>();
                    for (int index = 0; index < roles.size(); index++) {
                        authorities.add(new SimpleGrantedAuthority(roles.get(index)));
                    }
                    SecurityContextHolder.getContext()
                            .setAuthentication(
                                    new UsernamePasswordAuthenticationToken(
                                            user.value().toString(), null, authorities));
                } catch (RuntimeException ignored) {
                    // An unresolved attribute must never authenticate the request.
                }
            }
            chain.doFilter(request, response);
        }
    }

    /** An app JWT must not be required when a verified basis principal is already present. */
    private static final class SkipBasisBearer extends OncePerRequestFilter {
        private final JwtAuthenticationFilter delegate;

        private SkipBasisBearer(JwtAuthenticationFilter delegate) {
            this.delegate = delegate;
        }

        @Override
        protected void doFilterInternal(
                HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            if (request.getAttribute(BasisResourceFilter.PRINCIPAL_ATTRIBUTE) instanceof String) {
                chain.doFilter(request, response);
                return;
            }
            delegate.doFilter(request, response, chain);
        }
    }

    private void onUnauthenticated(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException failure)
            throws IOException {
        if (devconnect.enabled() && acceptsHtml(request)) {
            response.sendRedirect("/api/login");
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter()
                .write(
                        "{\"code\":\"AUTH_REQUIRED\",\"message\":\"Authentication required\","
                                + "\"correlationId\":\""
                                + UUID.randomUUID()
                                + "\",\"details\":{}}");
    }

    private static boolean acceptsHtml(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("text/html");
    }

    private static String authorization(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ") ? header : null;
    }
}
