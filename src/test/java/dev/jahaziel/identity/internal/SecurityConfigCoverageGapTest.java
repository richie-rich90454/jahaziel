/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.identity.api.DevConnectService;
import dev.jahaziel.identity.api.JwtService;
import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.identity.domain.UserId;
import jakarta.servlet.FilterChain;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer.AuthorizationManagerRequestMatcherRegistry;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.annotation.web.configurers.ExceptionHandlingConfigurer;
import org.springframework.security.config.annotation.web.configurers.LogoutConfigurer;
import org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;

class SecurityConfigCoverageGapTest {
  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private HttpSecurity wired(CsrfConfigurer<HttpSecurity> csrf) throws Exception {
    HttpSecurity http = mock(HttpSecurity.class, RETURNS_DEEP_STUBS);
    when(http.authorizeHttpRequests(any()))
        .thenAnswer(
            inv -> {
              ((org.springframework.security.config.Customizer) inv.getArgument(0))
                  .customize(
                      mock(AuthorizationManagerRequestMatcherRegistry.class, RETURNS_DEEP_STUBS));
              return http;
            });
    when(http.logout(any()))
        .thenAnswer(
            inv -> {
              ((org.springframework.security.config.Customizer) inv.getArgument(0))
                  .customize(mock(LogoutConfigurer.class, RETURNS_DEEP_STUBS));
              return http;
            });
    when(http.sessionManagement(any()))
        .thenAnswer(
            inv -> {
              ((org.springframework.security.config.Customizer) inv.getArgument(0))
                  .customize(mock(SessionManagementConfigurer.class, RETURNS_DEEP_STUBS));
              return http;
            });
    when(http.exceptionHandling(any()))
        .thenAnswer(
            inv -> {
              ((org.springframework.security.config.Customizer) inv.getArgument(0))
                  .customize(mock(ExceptionHandlingConfigurer.class, RETURNS_DEEP_STUBS));
              return http;
            });
    when(http.csrf(any()))
        .thenAnswer(
            inv -> {
              ((org.springframework.security.config.Customizer) inv.getArgument(0)).customize(csrf);
              return http;
            });
    when(http.addFilterBefore(any(), any())).thenReturn(http);
    org.mockito.Mockito.doReturn(mock(SecurityFilterChain.class)).when(http).build();
    return http;
  }

  @Test
  void chainCoversDemoBothSidesAndCsrfMatcher() throws Exception {
    JwtService tokens = mock(JwtService.class);
    UserService users = mock(UserService.class);
    DevConnectService dev = mock(DevConnectService.class);
    CsrfConfigurer<HttpSecurity> plain = mock(CsrfConfigurer.class, RETURNS_DEEP_STUBS);
    when(plain.ignoringRequestMatchers(any(RequestMatcher[].class))).thenReturn(plain);
    CsrfConfigurer<HttpSecurity> csrf = mock(CsrfConfigurer.class, RETURNS_DEEP_STUBS);
    when(csrf.ignoringRequestMatchers(any(RequestMatcher[].class))).thenReturn(csrf);
    new SecurityConfig(tokens, users, dev, false).chain(wired(plain));
    new SecurityConfig(tokens, users, dev, true).chain(wired(csrf));
    ArgumentCaptor<RequestMatcher> matchers = ArgumentCaptor.forClass(RequestMatcher.class);
    verify(csrf).ignoringRequestMatchers(any(RequestMatcher.class), matchers.capture());
    RequestMatcher bearer = matchers.getValue();
    MockHttpServletRequest withBearer = new MockHttpServletRequest();
    withBearer.addHeader("Authorization", "Bearer token");
    assertTrue(bearer.matches(withBearer));
    assertFalse(bearer.matches(new MockHttpServletRequest()));
    MockHttpServletRequest basic = new MockHttpServletRequest();
    basic.addHeader("Authorization", "Basic abc");
    assertFalse(bearer.matches(basic));
  }

  @Test
  void bffSkipsWhenAlreadyAuthenticated() throws Exception {
    DevConnectService dev = mock(DevConnectService.class);
    JwtService tokens = mock(JwtService.class);
    UserService users = mock(UserService.class);
    when(dev.enabled()).thenReturn(true);
    BffAuthenticationFilter filter = new BffAuthenticationFilter(dev, tokens, users);
    var present =
        new UsernamePasswordAuthenticationToken(
            "already", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    SecurityContextHolder.getContext().setAuthentication(present);
    MockHttpServletRequest req = new MockHttpServletRequest();
    FilterChain chain = mock(FilterChain.class);
    filter.doFilter(req, new MockHttpServletResponse(), chain);
    verify(chain).doFilter(any(), any());
    assertEquals(present, SecurityContextHolder.getContext().getAuthentication());
    SecurityContextHolder.clearContext();
    UserId user = new UserId(UUID.randomUUID());
    when(dev.activeUser(any())).thenReturn(java.util.Optional.of(user));
    when(users.get(user))
        .thenReturn(new dev.jahaziel.spi.UserIdentity("s", "e", "N", List.of(), List.of()));
    MockHttpServletRequest anon = new MockHttpServletRequest();
    anon.setCookies(new jakarta.servlet.http.Cookie(DevConnectService.SESSION_COOKIE, "sess"));
    filter.doFilter(anon, new MockHttpServletResponse(), mock(FilterChain.class));
    assertTrue(SecurityContextHolder.getContext().getAuthentication() != null);
  }
}
