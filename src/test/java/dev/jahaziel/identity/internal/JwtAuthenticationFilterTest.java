/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.identity.api.JwtService;
import dev.jahaziel.identity.api.JwtSession;
import dev.jahaziel.identity.domain.UserId;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/** Behavior of {@link JwtAuthenticationFilter} with a stubbed token service. */
class JwtAuthenticationFilterTest {
  private final UserId userId = new UserId(UUID.randomUUID());

  /** Clears the security context after every test. */
  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void acceptsBearerToken() throws Exception {
    JwtService tokens = mock(JwtService.class);
    JwtSession session =
        new JwtSession(userId, Optional.empty(), List.of(), Instant.now().plusSeconds(60));
    when(tokens.verify("good")).thenReturn(session);
    JwtAuthenticationFilter filter =
        new JwtAuthenticationFilter(tokens, user -> List.of("ROLE_USER"));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer good");
    FilterChain chain = mock(FilterChain.class);
    filter.doFilter(request, new MockHttpServletResponse(), chain);
    assertEquals(
        userId.value().toString(),
        SecurityContextHolder.getContext().getAuthentication().getName());
  }

  @Test
  void passesThroughWithoutToken() throws Exception {
    JwtService tokens = mock(JwtService.class);
    JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokens, user -> List.of());
    FilterChain chain = mock(FilterChain.class);
    filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);
    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  void rejectsInvalidToken() throws Exception {
    JwtService tokens = mock(JwtService.class);
    when(tokens.verify("bad")).thenThrow(new RuntimeException("nope"));
    JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokens, user -> List.of());
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer bad");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);
    filter.doFilter(request, response, chain);
    assertNull(SecurityContextHolder.getContext().getAuthentication());
    assertEquals(401, response.getStatus());
    assertTrue(response.getContentAsString().contains("AUTH_INVALID"));
    verify(chain, never()).doFilter(any(), any());
  }
}
