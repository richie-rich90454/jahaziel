/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class CookieWriteOriginFilterTest {
  private final CookieWriteOriginFilter filter = new CookieWriteOriginFilter();

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void permitsAuthenticatedSameOriginWrite() throws Exception {
    authenticated();
    MockHttpServletRequest request = request("POST");
    request.addHeader(HttpHeaders.ORIGIN, "http://localhost:8080");
    request.addHeader("Sec-Fetch-Site", "same-origin");
    FilterChain chain = mock(FilterChain.class);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    assertEquals(200, response.getStatus());
  }

  @Test
  void rejectsMissingOriginAndSameSiteSiblingOrigin() throws Exception {
    authenticated();
    MockHttpServletResponse missingResponse = new MockHttpServletResponse();
    filter.doFilter(request("POST"), missingResponse, mock(FilterChain.class));
    assertEquals(403, missingResponse.getStatus());
    assertTrue(missingResponse.getContentAsString().contains("CSRF_REJECTED"));

    MockHttpServletRequest sibling = request("PUT");
    sibling.addHeader(HttpHeaders.ORIGIN, "http://attacker.localhost:8080");
    sibling.addHeader("Sec-Fetch-Site", "same-site");
    MockHttpServletResponse siblingResponse = new MockHttpServletResponse();
    filter.doFilter(sibling, siblingResponse, mock(FilterChain.class));
    assertEquals(403, siblingResponse.getStatus());
  }

  @Test
  void usesTrustedForwardedOriginAndRejectsMalformedOrigin() throws Exception {
    authenticated();
    MockHttpServletRequest forwarded = request("DELETE");
    forwarded.addHeader("X-Forwarded-Proto", "https");
    forwarded.addHeader("X-Forwarded-Host", "ide.example.test:443");
    forwarded.addHeader(HttpHeaders.ORIGIN, "https://ide.example.test");
    FilterChain chain = mock(FilterChain.class);
    MockHttpServletResponse forwardedResponse = new MockHttpServletResponse();

    filter.doFilter(forwarded, forwardedResponse, chain);

    verify(chain).doFilter(forwarded, forwardedResponse);
    MockHttpServletRequest malformed = request("POST");
    malformed.addHeader(HttpHeaders.ORIGIN, "null");
    MockHttpServletResponse malformedResponse = new MockHttpServletResponse();
    filter.doFilter(malformed, malformedResponse, mock(FilterChain.class));
    assertEquals(403, malformedResponse.getStatus());
  }

  @Test
  void bearerAndSafeRequestsDoNotNeedOrigin() throws Exception {
    MockHttpServletRequest bearer = request("POST");
    bearer.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token");
    bearer.addHeader(HttpHeaders.ORIGIN, "https://attacker.test");
    FilterChain bearerChain = mock(FilterChain.class);
    MockHttpServletResponse bearerResponse = new MockHttpServletResponse();
    filter.doFilter(bearer, bearerResponse, bearerChain);
    verify(bearerChain).doFilter(bearer, bearerResponse);

    authenticated();
    MockHttpServletRequest safe = request("GET");
    FilterChain safeChain = mock(FilterChain.class);
    MockHttpServletResponse safeResponse = new MockHttpServletResponse();
    filter.doFilter(safe, safeResponse, safeChain);
    verify(safeChain).doFilter(safe, safeResponse);
  }

  private static MockHttpServletRequest request(String method) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/v1/workspaces");
    request.setScheme("http");
    request.setServerName("localhost");
    request.setServerPort(8080);
    return request;
  }

  private static void authenticated() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "user", null, java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))));
  }
}
