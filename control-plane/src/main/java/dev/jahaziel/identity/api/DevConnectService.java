/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.api;

import dev.jahaziel.identity.domain.UserId;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * DevConnect (basis-auth) login. The browser never holds IdP tokens: the callback stores an opaque
 * session server-side plus a short-lived API token cookie. Identity comes from UserInfo; the OIDC
 * subject is the account key after a one-time email join.
 */
public interface DevConnectService {
  /** The opaque browser session cookie. */
  String SESSION_COOKIE = "basis_sites_session";

  /** The short-lived API token cookie, equivalent to a bearer token. */
  String TOKEN_COOKIE = "token";

  /**
   * Checks whether login is configured.
   *
   * @return true when every BASIS_* value is present
   */
  boolean enabled();

  /**
   * Returns the browser session lifetime, for the session cookie's max age.
   *
   * @return the session lifetime
   */
  Duration sessionTtl();

  /**
   * Returns the API token lifetime, for the token cookie's max age.
   *
   * @return the token lifetime
   */
  Duration tokenTtl();

  /**
   * Starts a login: persists PKCE state and returns the IdP authorization URL.
   *
   * @return the URL to redirect the browser to
   * @throws DevConnectException when DevConnect is not configured or the IdP cannot be reached
   */
  String startLogin();

  /**
   * Completes a login: exchanges the code, verifies login proof, resolves the account, and opens a
   * session.
   *
   * @param state the login state from the callback
   * @param code the authorization code from the callback
   * @return the session and the API token for the token cookie
   * @throws LoginRejectedException when the account must not be admitted
   * @throws DevConnectException when a DevConnect step fails
   */
  LoginResult completeLogin(String state, String code);

  /**
   * Resolves an active session to its user, re-checking the account on every request so a suspend
   * or promotion takes effect without a new login.
   *
   * @param sessionId the opaque session cookie value
   * @return the user, or empty when the session is unknown, expired, or the account is disabled
   */
  Optional<UserId> activeUser(String sessionId);

  /**
   * Ends a session. Nothing is revoked at the IdP because we never request offline_access, so no
   * refresh token exists; the API token simply expires.
   *
   * @param sessionId the opaque session cookie value
   */
  void logout(String sessionId);

  /**
   * The result of a completed login.
   *
   * @param userId the resolved account
   * @param sessionId the opaque browser session identifier
   * @param apiToken the short-lived API token for scripts
   */
  record LoginResult(UUID userId, String sessionId, String apiToken) {}
}
