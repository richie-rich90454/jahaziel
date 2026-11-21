/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.controller;

import dev.jahaziel.api.dto.UserDto;
import dev.jahaziel.identity.api.DevConnectService;
import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.identity.auth.credential.CredentialAuthService;
import dev.jahaziel.identity.auth.credential.CredentialSubmission;
import dev.jahaziel.identity.domain.UserId;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Duration;
import java.util.UUID;

/**
 * DevConnect (basis-auth) login endpoints. {@code /api/login} sends the browser to the IdP; the
 * callback stores the session and API token cookies and lands on the dashboard. Identity lives
 * server-side, so the browser holds only an opaque session and a short-lived token.
 */
@RestController
public class DevConnectController {
    private static final String SUCCESS_REDIRECT = "/";
    private static final String FAILURE_REDIRECT = "/?login=failed";
    static final String STATE_COOKIE = "basis_login_state";

    private final DevConnectService devconnect;
    private final UserService users;
    private final CredentialAuthService credentials;
    private final boolean demo;

    /**
     * Creates the controller without credential routing.
     *
     * @param devconnect the login service
     * @param users the user lookup
     */
    public DevConnectController(DevConnectService devconnect, UserService users) {
        this(devconnect, users, null, false);
    }

    /** Creates the controller with the demo-mode sign-in behavior. */
    public DevConnectController(DevConnectService devconnect, UserService users, boolean demo) {
        this(devconnect, users, null, demo);
    }

    /** Creates the controller with credential routing for non-redirect providers. */
    @Autowired
    public DevConnectController(
            DevConnectService devconnect,
            UserService users,
            CredentialAuthService credentials,
            @Value("${jahaziel.demo:false}") boolean demo) {
        this.devconnect = devconnect;
        this.users = users;
        this.credentials = credentials;
        this.demo = demo;
    }

    /**
     * Starts login by redirecting to the IdP.
     *
     * @return a redirect to the authorization endpoint
     */
    @GetMapping("/api/login")
    public ResponseEntity<Void> login() {
        if (demo) return redirect(SUCCESS_REDIRECT);
        String url = devconnect.startLogin();
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, stateCookie(stateOf(url)).toString())
                .location(URI.create(url))
                .build();
    }

    /**
     * Completes login. The alias path is the byte-for-byte redirect URI some operators registered.
     *
     * @param state the login state
     * @param code the authorization code
     * @param error the IdP-reported error, when present
     * @return a redirect to the dashboard, with the session and token cookies set
     */
    @GetMapping({"/api/auth/basis/callback", "/api/oauth/callback", "/api/oauth2/bcallback"})
    public ResponseEntity<Void> callback(
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "error", required = false) String error,
            @CookieValue(name = STATE_COOKIE, required = false) String stateCookie) {
        if (error != null) {
            return redirect(FAILURE_REDIRECT);
        }
        if (state == null || state.isBlank() || !state.equals(stateCookie)) {
            return redirect(FAILURE_REDIRECT);
        }
        return withCookies(devconnect.completeLogin(state, code));
    }

    /**
     * Starts a passkey ceremony. The returned options carry the state the browser must submit back.
     *
     * @return the WebAuthn options document
     */
    @GetMapping("/api/auth/passkey/options")
    public ResponseEntity<String> passkeyOptions() {
        return ResponseEntity.ok(credentials.passkeyOptions());
    }

    /**
     * Completes a local username, password, and optional TOTP login.
     *
     * @param submission the explicit credential fields
     * @return the session and token cookies
     */
    @PostMapping("/api/auth/local")
    public ResponseEntity<Void> local(@RequestBody CredentialSubmission submission) {
        return withCookies(credentials.local(submission));
    }

    /**
     * Completes a passkey registration or assertion.
     *
     * @param submission the explicit WebAuthn response fields
     * @return the session and token cookies
     */
    @PostMapping("/api/auth/passkey")
    public ResponseEntity<Void> passkey(@RequestBody CredentialSubmission submission) {
        return withCookies(credentials.passkey(submission));
    }

    /**
     * Completes an LDAP bind login.
     *
     * @param submission the explicit bind credentials
     * @return the session and token cookies
     */
    @PostMapping("/api/auth/ldap")
    public ResponseEntity<Void> ldap(@RequestBody CredentialSubmission submission) {
        return withCookies(credentials.ldap(submission));
    }

    /**
     * Exchanges an issued API token for a browser session.
     *
     * @param submission the explicit token field
     * @return the session and token cookies
     */
    @PostMapping("/api/auth/token")
    public ResponseEntity<Void> token(@RequestBody CredentialSubmission submission) {
        return withCookies(credentials.token(submission));
    }

    /**
     * Attaches a second verified identity to the signed-in account. Never joins on email.
     *
     * @param submission the credential fields plus the provider to verify
     * @param principal the signed-in user
     * @return an empty response
     */
    @PostMapping("/api/auth/link")
    public ResponseEntity<Void> link(
            @RequestBody CredentialSubmission submission, Principal principal) {
        credentials.link(userId(principal).value(), submission);
        return ResponseEntity.noContent().build();
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
        dev.jahaziel.spi.UserIdentity identity = users.get(id);
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
            @CookieValue(name = DevConnectService.SESSION_COOKIE, required = false)
                    String sessionId) {
        devconnect.logout(sessionId);
        return ResponseEntity.noContent()
                .header(
                        HttpHeaders.SET_COOKIE,
                        cleared(DevConnectService.SESSION_COOKIE).toString(),
                        cleared(DevConnectService.TOKEN_COOKIE).toString())
                .build();
    }

    private ResponseEntity<Void> withCookies(DevConnectService.LoginResult result) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(
                        HttpHeaders.SET_COOKIE,
                        sessionCookie(result.sessionId()).toString(),
                        tokenCookie(result.apiToken()).toString())
                .location(URI.create(SUCCESS_REDIRECT))
                .build();
    }

    private ResponseCookie sessionCookie(String sessionId) {
        return ResponseCookie.from(DevConnectService.SESSION_COOKIE, sessionId)
                .httpOnly(true)
                .secure(!demo)
                .sameSite("Lax")
                .path("/")
                .maxAge(devconnect.sessionTtl())
                .build();
    }

    private ResponseCookie tokenCookie(String apiToken) {
        return ResponseCookie.from(DevConnectService.TOKEN_COOKIE, apiToken)
                .httpOnly(true)
                .secure(!demo)
                .sameSite("Lax")
                .path("/")
                .maxAge(devconnect.tokenTtl())
                .build();
    }

    private ResponseCookie cleared(String name) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(!demo)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }

    private ResponseCookie stateCookie(String state) {
        return ResponseCookie.from(STATE_COOKIE, state == null ? "" : state)
                .httpOnly(true)
                .secure(!demo)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofMinutes(10))
                .build();
    }

    private static String stateOf(String url) {
        String query = URI.create(url).getRawQuery();
        if (query == null) return null;
        String[] pairs = query.split("&");
        for (int index = 0; index < pairs.length; index++) {
            int equal = pairs[index].indexOf('=');
            if (equal > 0 && "state".equals(pairs[index].substring(0, equal))) {
                return java.net.URLDecoder.decode(
                        pairs[index].substring(equal + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
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
