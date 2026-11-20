/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.api;

import dev.jahaziel.identity.domain.UserId;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * DevConnect (basis-auth) login. The browser never holds IdP tokens: the callback stores an opaque
 * session server-side plus a short-lived API token cookie. Identity comes from UserInfo; the OIDC
 * subject is the account key after a one-time email join.
 */
public abstract class DevConnectService {
    /** The opaque browser session cookie. */
    public static final String SESSION_COOKIE = "basis_sites_session";

    /** The short-lived API token cookie, equivalent to a bearer token. */
    public static final String TOKEN_COOKIE = "token";

    /**
     * Checks whether login is configured.
     *
     * @return true when every BASIS_* value is present
     */
    public abstract boolean enabled();

    /**
     * Returns the browser session lifetime, for the session cookie's max age.
     *
     * @return the session lifetime
     */
    public abstract Duration sessionTtl();

    /**
     * Returns the API token lifetime, for the token cookie's max age.
     *
     * @return the token lifetime
     */
    public abstract Duration tokenTtl();

    /**
     * Starts a login: persists PKCE state and returns the IdP authorization URL.
     *
     * @return the URL to redirect the browser to
     * @throws DevConnectException when DevConnect is not configured or the IdP cannot be reached
     */
    public abstract String startLogin();

    /**
     * Completes a login: exchanges the code, verifies login proof, resolves the account, and opens
     * a session.
     *
     * @param state the login state from the callback
     * @param code the authorization code from the callback
     * @return the session and the API token for the token cookie
     * @throws LoginRejectedException when the account must not be admitted
     * @throws DevConnectException when a DevConnect step fails
     */
    public abstract LoginResult completeLogin(String state, String code);

    /**
     * Resolves an active session to its user, re-checking the account on every request so a suspend
     * or promotion takes effect without a new login.
     *
     * @param sessionId the opaque session cookie value
     * @return the user, or empty when the session is unknown, expired, or the account is disabled
     */
    public abstract Optional<UserId> activeUser(String sessionId);

    /**
     * Ends a session. Nothing is revoked at the IdP because we never request offline_access, so no
     * refresh token exists; the API token simply expires.
     *
     * @param sessionId the opaque session cookie value
     */
    public abstract void logout(String sessionId);

    /**
     * The result of a completed login.
     *
     * @param userId the resolved account
     * @param sessionId the opaque browser session identifier
     * @param apiToken the short-lived API token for scripts
     */
    public static final class LoginResult {
        private final UUID userId;
        private final String sessionId;
        private final String apiToken;

        /** Creates a login result. */
        public LoginResult(UUID userId, String sessionId, String apiToken) {
            this.userId = userId;
            this.sessionId = sessionId;
            this.apiToken = apiToken;
        }

        /** Returns the resolved account. */
        public UUID userId() {
            return userId;
        }

        public UUID getUserId() {
            return userId;
        }

        /** Returns the opaque browser session identifier. */
        public String sessionId() {
            return sessionId;
        }

        public String getSessionId() {
            return sessionId;
        }

        /** Returns the short-lived API token. */
        public String apiToken() {
            return apiToken;
        }

        public String getApiToken() {
            return apiToken;
        }

        /** Compares by class and all fields. */
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            LoginResult that = (LoginResult) o;
            return Objects.equals(userId, that.userId)
                    && Objects.equals(sessionId, that.sessionId)
                    && Objects.equals(apiToken, that.apiToken);
        }

        /** Returns the hash code derived from all fields. */
        @Override
        public int hashCode() {
            return Objects.hash(userId, sessionId, apiToken);
        }

        /** Returns a readable representation. */
        @Override
        public String toString() {
            return "LoginResult[userId="
                    + userId
                    + ", sessionId="
                    + sessionId
                    + ", apiToken="
                    + apiToken
                    + "]";
        }
    }
}
