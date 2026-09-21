/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.controller;

import dev.jahaziel.api.dto.UserDto;
import dev.jahaziel.identity.api.DevConnectService;
import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.identity.domain.UserId;
import java.net.URI;
import java.security.Principal;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * DevConnect (basis-auth) login endpoints. {@code /api/login} sends the browser to the IdP; the
 * callback stores the session and API token cookies and lands on the dashboard. Identity lives
 * server-side, so the browser holds only an opaque session and a short-lived token.
 */
@RestController
public class DevConnectController {
  private static final String SUCCESS_REDIRECT = "/";
  private static final String FAILURE_REDIRECT = "/?login=failed";

  private final DevConnectService devconnect;
  private final UserService users;

  /**
   * Creates the controller.
   *
   * @param devconnect the login service
   * @param users the user lookup
   */
  public DevConnectController(DevConnectService devconnect, UserService users) {
    this.devconnect = devconnect;
    this.users = users;
  }

  /**
   * Starts login by redirecting to the IdP.
   *
   * @return a redirect to the authorization endpoint
   */
  @GetMapping("/api/login")
  public ResponseEntity<Void> login() {
    return redirect(devconnect.startLogin());
  }

  /**
   * Completes login. The alias path is the byte-for-byte redirect URI some operators registered.
   *
   * @param state the login state
   * @param code the authorization code
   * @param error the IdP-reported error, when present
   * @return a redirect to the dashboard, with the session and token cookies set
   */
  @GetMapping({"/api/oauth/callback", "/api/oauth2/bcallback"})
  public ResponseEntity<Void> callback(
      @RequestParam(name = "state", required = false) String state,
      @RequestParam(name = "code", required = false) String code,
      @RequestParam(name = "error", required = false) String error) {
    if (error != null) {
      return redirect(FAILURE_REDIRECT);
    }
    DevConnectService.LoginResult result = devconnect.completeLogin(state, code);
    return ResponseEntity.status(HttpStatus.FOUND)
        .header(
            HttpHeaders.SET_COOKIE,
            sessionCookie(result.sessionId()).toString(),
            tokenCookie(result.apiToken()).toString())
        .location(URI.create(SUCCESS_REDIRECT))
        .build();
  }

  /**
   * Returns the current user.
   *
   * @param principal the authenticated principal
   * @return the user DTO
   */
  @GetMapping("/api/auth/me")
  public UserDto me(Principal principal) {
    UserId id = userId(principal);
    var identity = users.get(id);
    return new UserDto(
        id.value(),
        identity.email(),
        identity.displayName(),
        identity.roles(),
        users.isMfaEnabled(id));
  }

  /**
   * Ends the session and clears both cookies.
   *
   * @param sessionId the session cookie, when present
   * @return an empty response with the cookies cleared
   */
  @PostMapping("/api/auth/logout")
  public ResponseEntity<Void> logout(
      @CookieValue(name = DevConnectService.SESSION_COOKIE, required = false) String sessionId) {
    devconnect.logout(sessionId);
    return ResponseEntity.noContent()
        .header(
            HttpHeaders.SET_COOKIE,
            cleared(DevConnectService.SESSION_COOKIE).toString(),
            cleared(DevConnectService.TOKEN_COOKIE).toString())
        .build();
  }

  private ResponseCookie sessionCookie(String sessionId) {
    return ResponseCookie.from(DevConnectService.SESSION_COOKIE, sessionId)
        .httpOnly(true)
        .sameSite("Lax")
        .path("/")
        .maxAge(devconnect.sessionTtl())
        .build();
  }

  private ResponseCookie tokenCookie(String apiToken) {
    return ResponseCookie.from(DevConnectService.TOKEN_COOKIE, apiToken)
        .httpOnly(true)
        .sameSite("Lax")
        .path("/")
        .maxAge(devconnect.tokenTtl())
        .build();
  }

  private static ResponseCookie cleared(String name) {
    return ResponseCookie.from(name, "")
        .httpOnly(true)
        .sameSite("Lax")
        .path("/")
        .maxAge(Duration.ZERO)
        .build();
  }

  private static ResponseEntity<Void> redirect(String location) {
    return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
  }

  private static UserId userId(Principal principal) {
    try {
      return new UserId(UUID.fromString(principal.getName()));
    } catch (IllegalArgumentException failure) {
      throw new IllegalArgumentException("principal is not a user id");
    }
  }
}
