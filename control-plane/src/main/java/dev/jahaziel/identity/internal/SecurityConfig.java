/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.internal;

import dev.jahaziel.identity.api.DevConnectService;
import dev.jahaziel.identity.api.JwtService;
import dev.jahaziel.identity.api.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * Web security. Bearer tokens and DevConnect cookies authenticate the API; the login endpoints are
 * public because they are how a browser proves who it is. Actuator health stays public.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
  private final JwtService tokens;
  private final UserService users;
  private final DevConnectService devconnect;

  /**
   * Creates the configuration.
   *
   * @param tokens the token service
   * @param users the user lookup
   * @param devconnect the DevConnect login service
   */
  public SecurityConfig(JwtService tokens, UserService users, DevConnectService devconnect) {
    this.tokens = Objects.requireNonNull(tokens, "tokens");
    this.users = Objects.requireNonNull(users, "users");
    this.devconnect = Objects.requireNonNull(devconnect, "devconnect");
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
    BffAuthenticationFilter cookies = new BffAuthenticationFilter(devconnect, tokens, users);
    return http.authorizeHttpRequests(
            exchanges ->
                exchanges
                    .requestMatchers("/actuator/health", "/error")
                    .permitAll()
                    .requestMatchers("/api/login", "/api/oauth/callback", "/api/oauth2/bcallback")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .logout(logout -> logout.logoutSuccessUrl("/"))
        .sessionManagement(
            sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(
            csrf ->
                csrf.ignoringRequestMatchers(
                    PathPatternRequestMatcher.withDefaults().matcher("/api/**"),
                    request -> authorization(request) != null))
        .exceptionHandling(handling -> handling.authenticationEntryPoint(this::onUnauthenticated))
        .addFilterBefore(cookies, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(bearer, UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  private void onUnauthenticated(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException failure)
      throws IOException {
    if (devconnect.enabled() && acceptsHtml(request)) {
      response.sendRedirect("/api/login");
      return;
    }
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType("application/json");
    response
        .getWriter()
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
