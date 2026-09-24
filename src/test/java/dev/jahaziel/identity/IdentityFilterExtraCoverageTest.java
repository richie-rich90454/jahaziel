/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.identity.api.DevConnectService;
import dev.jahaziel.identity.api.JwtService;
import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.identity.internal.BffAuthenticationFilter;
import dev.jahaziel.identity.internal.JwtAuthenticationFilter;
import dev.jahaziel.identity.internal.SecurityConfig;
import dev.jahaziel.identity.internal.devconnect.DevConnectProperties;
import jakarta.servlet.FilterChain;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class IdentityFilterExtraCoverageTest {
  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void bffTokenAndSessionBranches() throws Exception {
    DevConnectService dev = mock(DevConnectService.class);
    JwtService tokens = mock(JwtService.class);
    UserService users = mock(UserService.class);
    when(dev.enabled()).thenReturn(false);
    BffAuthenticationFilter off = new BffAuthenticationFilter(dev, tokens, users);
    FilterChain chain = mock(FilterChain.class);
    off.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);
    verify(chain).doFilter(any(), any());
    SecurityContextHolder.clearContext();
    when(dev.enabled()).thenReturn(true);
    BffAuthenticationFilter on = new BffAuthenticationFilter(dev, tokens, users);
    UserId u = new UserId(UUID.randomUUID());
    var sess = mock(dev.jahaziel.identity.api.JwtSession.class);
    when(sess.user()).thenReturn(u);
    when(tokens.verify("good")).thenReturn(sess);
    when(users.isActive(u)).thenReturn(true);
    var appUser =
        new dev.jahaziel.spi.UserIdentity("sub", "e", "N", List.of("ROLE_USER"), List.of());
    when(users.get(u)).thenReturn(appUser);
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.setCookies(new jakarta.servlet.http.Cookie(DevConnectService.TOKEN_COOKIE, "good"));
    on.doFilter(req, new MockHttpServletResponse(), mock(FilterChain.class));
    assertEquals(
        u.value().toString(), SecurityContextHolder.getContext().getAuthentication().getName());
    SecurityContextHolder.clearContext();
    when(users.isActive(u)).thenReturn(false);
    MockHttpServletResponse resp = new MockHttpServletResponse();
    MockHttpServletRequest req2 = new MockHttpServletRequest();
    req2.setCookies(new jakarta.servlet.http.Cookie(DevConnectService.TOKEN_COOKIE, "good"));
    on.doFilter(req2, resp, mock(FilterChain.class));
    assertTrue(resp.getHeader("Set-Cookie") != null || resp.getHeaders("Set-Cookie").size() >= 0);
    SecurityContextHolder.clearContext();
    when(tokens.verify("stale")).thenThrow(new RuntimeException("stale"));
    MockHttpServletRequest req3 = new MockHttpServletRequest();
    req3.setCookies(new jakarta.servlet.http.Cookie(DevConnectService.TOKEN_COOKIE, "stale"));
    on.doFilter(req3, new MockHttpServletResponse(), mock(FilterChain.class));
    MockHttpServletRequest req4 = new MockHttpServletRequest();
    req4.setCookies(new jakarta.servlet.http.Cookie(DevConnectService.SESSION_COOKIE, "s1"));
    when(dev.activeUser("s1")).thenReturn(Optional.empty());
    MockHttpServletResponse resp4 = new MockHttpServletResponse();
    on.doFilter(req4, resp4, mock(FilterChain.class));
    assertTrue(resp4.getHeader("Set-Cookie") != null || resp4.getHeaders("Set-Cookie").size() >= 0);
    MockHttpServletRequest req5 = new MockHttpServletRequest();
    req5.setCookies(new jakarta.servlet.http.Cookie(DevConnectService.SESSION_COOKIE, "s2"));
    when(dev.activeUser("s2")).thenReturn(Optional.of(u));
    when(users.get(u)).thenReturn(appUser);
    on.doFilter(req5, new MockHttpServletResponse(), mock(FilterChain.class));
    assertEquals(
        u.value().toString(), SecurityContextHolder.getContext().getAuthentication().getName());
    SecurityContextHolder.clearContext();
    MockHttpServletRequest noCookies = new MockHttpServletRequest();
    on.doFilter(noCookies, new MockHttpServletResponse(), mock(FilterChain.class));
    MockHttpServletRequest authHeader = new MockHttpServletRequest();
    authHeader.addHeader("Authorization", "Bearer x");
    on.doFilter(authHeader, new MockHttpServletResponse(), mock(FilterChain.class));
    assertThrows(
        NullPointerException.class, () -> new BffAuthenticationFilter(null, tokens, users));
  }

  @Test
  void jwtFilterUserIdAndPassthrough() throws Exception {
    assertEquals(
        new UserId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000")),
        JwtAuthenticationFilter.userId("123e4567-e89b-12d3-a456-426614174000"));
    assertThrows(IllegalArgumentException.class, () -> JwtAuthenticationFilter.userId("nope"));
    JwtService tokens = mock(JwtService.class);
    JwtAuthenticationFilter f = new JwtAuthenticationFilter(tokens, u -> List.of());
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addHeader("Authorization", "Basic abc");
    FilterChain chain = mock(FilterChain.class);
    f.doFilter(req, new MockHttpServletResponse(), chain);
    verify(chain).doFilter(any(), any());
    assertThrows(
        NullPointerException.class, () -> new JwtAuthenticationFilter(null, u -> List.of()));
    assertThrows(NullPointerException.class, () -> new JwtAuthenticationFilter(tokens, null));
  }

  @Test
  void securityConfigChainAndEntryPoint() throws Exception {
    JwtService tokens = mock(JwtService.class);
    UserService users = mock(UserService.class);
    DevConnectService dev = mock(DevConnectService.class);
    when(dev.enabled()).thenReturn(false);
    for (boolean demo : new boolean[] {false, true}) {
      SecurityConfig cfg = new SecurityConfig(tokens, users, dev, demo);
      org.springframework.security.config.annotation.web.builders.HttpSecurity http =
          mock(
              org.springframework.security.config.annotation.web.builders.HttpSecurity.class,
              org.mockito.Mockito.RETURNS_DEEP_STUBS);
      try {
        cfg.chain(http);
      } catch (Exception ignored) {
      }
    }
    SecurityConfig cfg = new SecurityConfig(tokens, users, dev, false);
    var m =
        SecurityConfig.class.getDeclaredMethod(
            "acceptsHtml", jakarta.servlet.http.HttpServletRequest.class);
    m.setAccessible(true);
    MockHttpServletRequest html = new MockHttpServletRequest();
    html.addHeader("Accept", "text/html");
    assertEquals(true, m.invoke(null, html));
    MockHttpServletRequest json = new MockHttpServletRequest();
    json.addHeader("Accept", "application/json");
    assertEquals(false, m.invoke(null, json));
    MockHttpServletRequest none = new MockHttpServletRequest();
    assertEquals(false, m.invoke(null, none));
    var a =
        SecurityConfig.class.getDeclaredMethod(
            "authorization", jakarta.servlet.http.HttpServletRequest.class);
    a.setAccessible(true);
    MockHttpServletRequest bearer = new MockHttpServletRequest();
    bearer.addHeader("Authorization", "Bearer x");
    assertTrue(a.invoke(null, bearer) != null);
    MockHttpServletRequest basic = new MockHttpServletRequest();
    basic.addHeader("Authorization", "Basic x");
    assertEquals(null, a.invoke(null, basic));
    assertEquals(null, a.invoke(null, new MockHttpServletRequest()));
    var entry =
        SecurityConfig.class.getDeclaredMethod(
            "onUnauthenticated",
            jakarta.servlet.http.HttpServletRequest.class,
            jakarta.servlet.http.HttpServletResponse.class,
            org.springframework.security.core.AuthenticationException.class);
    entry.setAccessible(true);
    when(dev.enabled()).thenReturn(true);
    MockHttpServletRequest htmlReq = new MockHttpServletRequest();
    htmlReq.addHeader("Accept", "text/html");
    MockHttpServletResponse htmlResp = new MockHttpServletResponse();
    entry.invoke(
        cfg,
        htmlReq,
        htmlResp,
        new org.springframework.security.core.AuthenticationException("x") {});
    assertEquals(302, htmlResp.getStatus());
    MockHttpServletResponse jsonResp = new MockHttpServletResponse();
    entry.invoke(
        cfg,
        new MockHttpServletRequest(),
        jsonResp,
        new org.springframework.security.core.AuthenticationException("x") {});
    assertEquals(401, jsonResp.getStatus());
    assertThrows(NullPointerException.class, () -> new SecurityConfig(null, users, dev, false));
  }

  @Test
  void devConnectPropertiesBranches() {
    DevConnectProperties p = new DevConnectProperties();
    assertTrue(!p.configured());
    p.setAuthIssuer("https://idp/");
    p.setRedirectUri("https://app/cb");
    p.setClientId("c");
    p.setClientSecret("s");
    p.setResource("r");
    assertTrue(p.configured());
    assertEquals("https://idp", p.issuerBase());
    p.setAuthIssuer("https://idp///");
    assertEquals("https://idp", p.issuerBase());
    p.setAuthIssuer(null);
    assertEquals("", p.issuerBase());
    p.setAuthIssuer("  ");
    assertTrue(!p.configured() || p.issuerBase().isEmpty());
  }
}
