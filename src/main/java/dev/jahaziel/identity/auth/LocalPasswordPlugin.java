/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth;

import dev.jahaziel.identity.auth.local.LocalAuthException;
import dev.jahaziel.identity.auth.local.LocalStore;
import dev.jahaziel.identity.auth.local.PasswordHasher;
import dev.jahaziel.identity.auth.local.Totp;
import dev.jahaziel.plugin.PluginContext;
import dev.jahaziel.plugin.PluginDescriptor;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Local username/password provider with TOTP enrollment and MFA assurance. */
public final class LocalPasswordPlugin extends AuthProviderPlugin {
    public static final String SERVICE_STORE = "local.store";
    public static final String SERVICE_CLOCK = "local.clock";

    private static final String PROVIDER = "local";
    private static final PluginDescriptor DESCRIPTOR =
            new PluginDescriptor(
                    PROVIDER,
                    "1",
                    Collections.<String>emptyList(),
                    Arrays.asList(
                            AuthCapability.PASSWORD,
                            AuthCapability.TOTP,
                            AuthCapability.MFA_ASSURANCE));

    private LocalStore store;
    private Clock clock;

    public LocalPasswordPlugin() {
        super(DESCRIPTOR);
    }

    @Override
    public void initialize(PluginContext context) {
        Object supplied = context.service(SERVICE_CLOCK);
        clock = supplied instanceof Clock ? (Clock) supplied : Clock.systemUTC();
        store = new LocalStore(context, clock);
        context.putService(SERVICE_STORE, store);
        bootstrapAdmin(context);
    }

    @Override
    public void shutdown() {}

    @Override
    public String startLogin(
            PluginContext context, String state, String nonce, String redirectUri) {
        if (state == null || state.length() == 0) {
            throw new LocalAuthException("missing state");
        }
        store.putPending(state, nonce, redirectUri, clock.instant().getEpochSecond());
        return "/login/local";
    }

    @Override
    public AuthIdentity handleCallback(PluginContext context, Map<String, String> parameters) {
        String state = parameters.get("state");
        if (state == null || !store.takePending(state)) {
            throw new LocalAuthException("invalid state");
        }
        String username = normalize(parameters.get("username"));
        String password = parameters.get("password");
        if (username == null || password == null) {
            throw new LocalAuthException("username and password are required");
        }
        LocalStore.User user = store.getUser(username);
        if (user == null
                || !PasswordHasher.verify(
                        password.toCharArray(), user.salt(), user.iterations(), user.hash())) {
            throw new LocalAuthException("invalid credentials");
        }

        String suppliedCode = parameters.get("totp");
        boolean mfaAssured;
        if (!user.enrolled()) {
            mfaAssured = enroll(user, suppliedCode);
        } else {
            mfaAssured = confirmTotp(user, suppliedCode);
        }

        String sessionId = newSessionId();
        Map<String, String> claims = new HashMap<String, String>();
        claims.put("session_id", sessionId);
        claims.put("mfa_enrollment", user.enrolled() ? "complete" : "pending");
        claims.put("admin", mfaAssured && user.admin() ? "true" : "false");
        AuthIdentity identity =
                new AuthIdentity(
                        PROVIDER, username, user.email(), username, false, mfaAssured, claims);
        store.putSession(sessionId, identity);
        return identity;
    }

    @Override
    public AuthIdentity currentIdentity(PluginContext context, String sessionId) {
        return store.getSession(sessionId);
    }

    @Override
    public void endSession(PluginContext context, String sessionId) {
        store.removeSession(sessionId);
    }

    /** Creates the configured first user. Admin is granted only when TOTP enrollment completes. */
    public void bootstrapAdmin(PluginContext context) {
        String username = normalize(context.setting("local.username"));
        String password = context.setting("local.password");
        if (username == null
                || username.length() == 0
                || password == null
                || password.length() == 0) {
            return;
        }
        if (store.hasUser(username)) {
            return;
        }
        String email = context.setting("local.email");
        byte[] salt = PasswordHasher.newSalt();
        byte[] hash = PasswordHasher.hash(password.toCharArray(), salt, PasswordHasher.ITERATIONS);
        byte[] secret = Totp.newSecret();
        store.createUser(
                username,
                email == null ? "" : email,
                salt,
                hash,
                PasswordHasher.ITERATIONS,
                store.encryptSecret(secret));
    }

    /** Returns the decrypted TOTP secret for enrollment, or null for an unknown user. */
    public byte[] totpSecret(String username) {
        LocalStore.User user = store.getUser(normalize(username));
        return user == null ? null : store.totpSecret(user);
    }

    private boolean enroll(LocalStore.User user, String suppliedCode) {
        if (suppliedCode == null || suppliedCode.length() == 0) {
            return false;
        }
        int matched =
                Totp.verify(
                        store.totpSecret(user),
                        suppliedCode,
                        clock.instant().getEpochSecond(),
                        user.totpCounter());
        if (matched < 0) {
            throw new LocalAuthException("invalid TOTP code");
        }
        user.setTotpCounter(matched);
        user.setEnrolled(true);
        user.setAdmin(true);
        store.saveUser(user);
        return true;
    }

    private boolean confirmTotp(LocalStore.User user, String suppliedCode) {
        if (suppliedCode == null || suppliedCode.length() == 0) {
            throw new LocalAuthException("TOTP code is required");
        }
        int matched =
                Totp.verify(
                        store.totpSecret(user),
                        suppliedCode,
                        clock.instant().getEpochSecond(),
                        user.totpCounter());
        if (matched < 0) {
            throw new LocalAuthException("invalid or replayed TOTP code");
        }
        user.setTotpCounter(matched);
        store.saveUser(user);
        return true;
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String newSessionId() {
        byte[] value = new byte[16];
        new SecureRandom().nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
