/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.credential;

import dev.jahaziel.identity.api.DevConnectService;
import dev.jahaziel.identity.api.LoginRejectedException;
import dev.jahaziel.identity.auth.AuthCapability;
import dev.jahaziel.identity.auth.AuthIdentity;
import dev.jahaziel.identity.auth.AuthProviderPlugin;
import dev.jahaziel.identity.auth.LocalPasswordPlugin;
import dev.jahaziel.identity.auth.local.LocalStore;
import dev.jahaziel.identity.auth.wiring.AuthProviderDispatcher;
import dev.jahaziel.identity.internal.devconnect.DevConnectServiceImpl;
import dev.jahaziel.plugin.PluginContext;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Routes browser credential submissions to the active non-redirect provider. The provider's
 * handleCallback is the only verifier; this class never trusts a submitted provider name to pick a
 * different plugin than the one configured, and never accepts a URL or executable path.
 */
public final class CredentialAuthService {
    private static final String PROVIDER_LOCAL = "local";
    private static final String PROVIDER_LDAP = "ldap";
    private static final String PROVIDER_PASSKEY = "passkey";
    private static final String PROVIDER_API_TOKEN = "api-token";

    private final AuthProviderDispatcher dispatcher;
    private final DevConnectServiceImpl login;
    private final PluginContext context;
    private final String redirectUri;

    public CredentialAuthService(
            AuthProviderDispatcher dispatcher,
            DevConnectServiceImpl login,
            PluginContext context,
            String redirectUri) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.login = Objects.requireNonNull(login, "login");
        this.context = context;
        this.redirectUri = redirectUri == null ? "" : redirectUri;
    }

    /** Returns WebAuthn options for a passkey ceremony, including the state to submit back. */
    public String passkeyOptions() {
        requireProvider(PROVIDER_PASSKEY, capabilitiesFor(PROVIDER_PASSKEY));
        String options = dispatcher.startLogin(randomToken(), randomToken(), redirectUri);
        if (options == null || options.length() == 0) {
            throw new LoginRejectedException("passkey ceremony is unavailable");
        }
        return options;
    }

    /** Verifies a local password and optional TOTP, then opens the browser session. */
    public DevConnectService.LoginResult local(CredentialSubmission submission) {
        return finish(authenticate(submission, PROVIDER_LOCAL));
    }

    /** Verifies LDAP bind credentials, then opens the browser session. */
    public DevConnectService.LoginResult ldap(CredentialSubmission submission) {
        return finish(authenticate(submission, PROVIDER_LDAP));
    }

    /** Verifies a WebAuthn registration or assertion, then opens the browser session. */
    public DevConnectService.LoginResult passkey(CredentialSubmission submission) {
        return finish(authenticate(submission, PROVIDER_PASSKEY));
    }

    /** Exchanges an already-issued API token for a browser session. */
    public DevConnectService.LoginResult token(CredentialSubmission submission) {
        return finish(authenticate(submission, PROVIDER_API_TOKEN));
    }

    /**
     * Verifies credentials for an authenticated caller who is attaching a second identity. The
     * caller's session is never replaced here.
     */
    public AuthIdentity verify(CredentialSubmission submission) {
        if (submission == null) {
            throw new LoginRejectedException("credentials are missing");
        }
        String name = submission.getProvider();
        if (name == null || name.trim().length() == 0) {
            throw new LoginRejectedException("provider is required");
        }
        return authenticate(submission, name);
    }

    /** Completes a verified credential login in the shared provisioning path. */
    public void link(UUID userId, CredentialSubmission submission) {
        login.linkCredentialIdentity(userId, verify(submission));
    }

    private DevConnectService.LoginResult finish(AuthIdentity identity) {
        return login.completeCredentialLogin(identity);
    }

    private AuthIdentity authenticate(CredentialSubmission submission, String expectedName) {
        if (submission == null) {
            throw new LoginRejectedException("credentials are missing");
        }
        String[] capabilities = capabilitiesFor(expectedName);
        requireProvider(expectedName, capabilities);
        String state = submission.getState();
        boolean started = state == null || state.trim().length() == 0;
        if (started) {
            state = randomToken();
            dispatcher.startLogin(state, randomToken(), redirectUri);
        }
        Map<String, String> parameters = parameters(submission);
        parameters.put("state", state);
        if (started && PROVIDER_LOCAL.equals(expectedName)) {
            clearLocalNonce(state);
        }
        AuthIdentity identity = dispatcher.handleCallback(parameters);
        if (identity == null) {
            throw new LoginRejectedException("credentials were rejected");
        }
        return identity;
    }

    private void requireProvider(String expectedName, String[] capabilities) {
        AuthProviderPlugin provider = dispatcher.active();
        if (provider == null || !expectedName.equals(provider.descriptor().name())) {
            throw new LoginRejectedException("credential provider is not active");
        }
        for (int index = 0; index < capabilities.length; index++) {
            if (!provider.supports(capabilities[index])) {
                throw new LoginRejectedException("provider lacks the required capability");
            }
        }
    }

    private static String[] capabilitiesFor(String name) {
        if (PROVIDER_LOCAL.equals(name)) {
            return new String[] {AuthCapability.PASSWORD, AuthCapability.TOTP};
        }
        if (PROVIDER_LDAP.equals(name)) {
            return new String[] {AuthCapability.PASSWORD};
        }
        if (PROVIDER_PASSKEY.equals(name)) {
            return new String[] {AuthCapability.PASSKEY};
        }
        if (PROVIDER_API_TOKEN.equals(name)) {
            return new String[] {AuthCapability.API_TOKEN};
        }
        throw new LoginRejectedException("unsupported credential provider");
    }

    private static Map<String, String> parameters(CredentialSubmission submission) {
        Map<String, String> parameters = new HashMap<String, String>();
        put(parameters, "username", submission.getUsername());
        put(parameters, "password", submission.getPassword());
        put(parameters, "totp", submission.getTotp());
        put(parameters, "token", submission.getToken());
        put(parameters, "clientDataJSON", submission.getClientDataJson());
        put(parameters, "attestationObject", submission.getAttestationObject());
        put(parameters, "credentialId", submission.getCredentialId());
        put(parameters, "authenticatorData", submission.getAuthenticatorData());
        put(parameters, "signature", submission.getSignature());
        return parameters;
    }

    private static void put(Map<String, String> parameters, String key, String value) {
        if (value != null) {
            parameters.put(key, value);
        }
    }

    /**
     * The local plugin stores a pending nonce that its callback never reads. Clear it through the
     * store exposed on the plugin context, so no unused nonce survives the pending window.
     */
    private void clearLocalNonce(String state) {
        if (context == null) {
            return;
        }
        Object service = context.service(LocalPasswordPlugin.SERVICE_STORE);
        if (service instanceof LocalStore) {
            long epoch = System.currentTimeMillis() / 1000L;
            Object clockService = context.service(LocalPasswordPlugin.SERVICE_CLOCK);
            if (clockService instanceof Clock) {
                epoch = ((Clock) clockService).instant().getEpochSecond();
            }
            ((LocalStore) service).putPending(state, "", "", epoch);
        }
    }

    private static String randomToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
