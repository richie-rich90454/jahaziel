/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.internal;

import dev.jahaziel.identity.api.JwtService;
import dev.jahaziel.identity.api.UserService;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * Web security. Bearer tokens authenticate the API; browser login uses OIDC against the configured
 * provider. Actuator health stays public.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
  private final JwtService tokens;
  private final UserService users;
  private final boolean oidcEnabled;

  /**
   * Creates the configuration.
   *
   * @param tokens the token service
   * @param users the user lookup
   * @param oidcEnabled false in portable single-file mode without an identity provider
   */
  public SecurityConfig(
      JwtService tokens,
      UserService users,
      @Value("${jahaziel.auth.oidc-enabled:true}") boolean oidcEnabled) {
    this.tokens = Objects.requireNonNull(tokens, "tokens");
    this.users = Objects.requireNonNull(users, "users");
    this.oidcEnabled = oidcEnabled;
  }

  /**
   * Builds the filter chain.
   *
   * @param http the http security
   * @return the chain
   */
  @Bean
  public SecurityFilterChain chain(HttpSecurity http) throws Exception {
    JwtAuthenticationFilter bearer =
        new JwtAuthenticationFilter(tokens, user -> users.get(user).roles());
    if (oidcEnabled) {
      http.oauth2Login(oauth -> oauth.defaultSuccessUrl("/", true));
    }
    return http.authorizeHttpRequests(
            exchanges ->
                exchanges
                    .requestMatchers("/actuator/health", "/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .logout(logout -> logout.logoutSuccessUrl("/"))
        .sessionManagement(
            sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .csrf(
            csrf ->
                csrf.ignoringRequestMatchers(
                    PathPatternRequestMatcher.withDefaults().matcher("/api/**"),
                    request -> authorization(request) != null))
        .addFilterBefore(bearer, UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  private static String authorization(jakarta.servlet.http.HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    return header != null && header.startsWith("Bearer ") ? header : null;
  }
}
