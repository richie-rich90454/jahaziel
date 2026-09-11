/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.internal;

import dev.jahaziel.identity.api.JwtService;
import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.identity.domain.UserId;
import java.util.List;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Web security. Bearer tokens authenticate the API; browser login uses OIDC against the
 * configured provider. Actuator health stays public.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
  private final JwtService tokens;
  private final UserService users;

  /**
   * Creates the configuration.
   *
   * @param tokens the token service
   * @param users the user lookup
   */
  public SecurityConfig(JwtService tokens, UserService users) {
    this.tokens = Objects.requireNonNull(tokens, "tokens");
    this.users = Objects.requireNonNull(users, "users");
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
    return http.authorizeHttpRequests(
            exchanges ->
                exchanges
                    .requestMatchers("/actuator/health", "/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2Login(oauth -> oauth.defaultSuccessUrl("/", true))
        .logout(logout -> logout.logoutSuccessUrl("/"))
        .sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/admin/**"))
        .addFilterBefore(bearer, UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
