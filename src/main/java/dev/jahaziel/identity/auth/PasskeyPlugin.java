/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth;

import dev.jahaziel.identity.auth.passkey.CoseKey;
import dev.jahaziel.identity.auth.passkey.PasskeyConfig;
import dev.jahaziel.identity.auth.passkey.PasskeyCredential;
import dev.jahaziel.identity.auth.passkey.PasskeyException;
import dev.jahaziel.identity.auth.passkey.PasskeyPending;
import dev.jahaziel.identity.auth.passkey.PasskeyStore;
import dev.jahaziel.identity.auth.passkey.WebAuthn;
import dev.jahaziel.plugin.PluginContext;
import dev.jahaziel.plugin.PluginDescriptor;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * WebAuthn/FIDO2 provider. Registration accepts self ("none") attestation; authentication verifies
 * ES256 P-256 assertions with challenge, origin, relying-party, and sign-counter checks.
 *
 * <p>LIMITATION (scoped subset): attestation statement signatures (packed/u2f/x5c chains) are not
 * verified, and raw r||s ES256 signatures are not decoded. Only DER-encoded ES256 assertions are
 * accepted. Registration establishes a credential key but does not attest device provenance.
 */
public final class PasskeyPlugin extends AuthProviderPlugin {
    public static final String SERVICE_STORE = "passkey.store";

    private static final PluginDescriptor DESCRIPTOR =
            new PluginDescriptor(
                    "passkey",
                    "1",
                    Collections.<String>emptyList(),
                    Arrays.asList(AuthCapability.PASSKEY, AuthCapability.MFA_ASSURANCE));

    private final SecureRandom random = new SecureRandom();
    private PasskeyConfig config;
    private PasskeyStore store;

    public PasskeyPlugin() {
        super(DESCRIPTOR);
    }

    @Override
    public void initialize(PluginContext context) {
        ensure(context);
    }

    @Override
    public void shutdown() {}

    @Override
    public String startLogin(
            PluginContext context, String state, String nonce, String redirectUri) {
        ensure(context);
        if (state == null || state.length() == 0) {
            throw new PasskeyException("passkey state is required");
        }
        byte[] challengeBytes = new byte[32];
        random.nextBytes(challengeBytes);
        String challenge = WebAuthn.encode(challengeBytes);
        store.putPending(
                state,
                new PasskeyPending(
                        challenge,
                        nonce,
                        redirectUri,
                        System.currentTimeMillis() + config.challengeLifetimeMillis()));
        StringBuilder options = new StringBuilder();
        options.append("{\"type\":\"public-key\",\"challenge\":\"")
                .append(challenge)
                .append("\",\"rpId\":\"")
                .append(config.rpId())
                .append("\",\"rpName\":\"")
                .append(config.rpName())
                .append("\",\"origin\":\"")
                .append(config.origin())
                .append("\",\"state\":\"")
                .append(state)
                .append("\"}");
        return options.toString();
    }

    @Override
    public AuthIdentity handleCallback(PluginContext context, Map<String, String> parameters) {
        ensure(context);
        if (parameters == null) {
            throw new PasskeyException("passkey parameters are missing");
        }
        String state = parameters.get("state");
        PasskeyPending pending = state == null ? null : store.takePending(state);
        if (pending == null) {
            throw new PasskeyException("passkey state mismatch");
        }
        WebAuthn.ClientData clientData =
                WebAuthn.parseClientData(WebAuthn.decode(parameters.get("clientDataJSON")));
        String type = clientData.type();
        if (!"webauthn.create".equals(type) && !"webauthn.get".equals(type)) {
            throw new PasskeyException("unsupported WebAuthn ceremony type");
        }
        if (!pending.challenge().equals(clientData.challenge())) {
            throw new PasskeyException("passkey challenge mismatch");
        }
        if (!config.origin().equals(clientData.origin())) {
            throw new PasskeyException("passkey origin mismatch");
        }
        if ("webauthn.create".equals(type)) {
            return register(clientData, parameters);
        }
        return authenticate(clientData, parameters);
    }

    @Override
    public AuthIdentity currentIdentity(PluginContext context, String sessionId) {
        ensure(context);
        if (sessionId == null || sessionId.length() == 0) {
            return null;
        }
        return store.getSession(sessionId);
    }

    @Override
    public void endSession(PluginContext context, String sessionId) {
        ensure(context);
        if (sessionId != null) {
            store.removeSession(sessionId);
        }
    }

    private AuthIdentity register(WebAuthn.ClientData clientData, Map<String, String> parameters) {
        WebAuthn.AttestationObject attestation =
                WebAuthn.parseAttestationObject(
                        WebAuthn.decode(parameters.get("attestationObject")));
        if (!"none".equals(attestation.format())) {
            throw new PasskeyException(
                    "passkey attestation format "
                            + attestation.format()
                            + " is outside the JDK-only scoped subset");
        }
        WebAuthn.AuthenticatorData authData =
                WebAuthn.parseAuthenticatorData(attestation.authData());
        verifyRpId(authData);
        if (!WebAuthn.userPresent(authData)) {
            throw new PasskeyException("passkey user presence not asserted");
        }
        byte[] credentialIdBytes = authData.credentialId();
        byte[] cose = authData.cosePublicKey();
        if (credentialIdBytes == null || cose == null) {
            throw new PasskeyException("attested credential data is missing");
        }
        CoseKey.parse(cose);
        String id = WebAuthn.encode(credentialIdBytes);
        store.putCredential(new PasskeyCredential(id, cose, authData.signCount(), config.rpId()));

        String sessionId = sessionId();
        Map<String, String> claims = new HashMap<String, String>();
        claims.put("credential_id", id);
        claims.put("rp_id", config.rpId());
        claims.put("sign_count", Long.toString(authData.signCount()));
        claims.put("registration", "true");
        claims.put("session_id", sessionId);
        AuthIdentity identity = new AuthIdentity("passkey", id, "", "", false, false, claims);
        store.putSession(sessionId, identity);
        return identity;
    }

    private AuthIdentity authenticate(
            WebAuthn.ClientData clientData, Map<String, String> parameters) {
        String id = WebAuthn.encode(WebAuthn.decode(parameters.get("credentialId")));
        WebAuthn.AuthenticatorData authData =
                WebAuthn.parseAuthenticatorData(
                        WebAuthn.decode(parameters.get("authenticatorData")));
        verifyRpId(authData);
        if (!WebAuthn.userPresent(authData)) {
            throw new PasskeyException("passkey user presence not asserted");
        }
        PasskeyCredential credential = store.getCredential(id);
        if (credential == null) {
            throw new PasskeyException("unknown passkey credential");
        }
        byte[] signedData = WebAuthn.concat(authData.raw(), WebAuthn.sha256(clientData.raw()));
        byte[] signature = WebAuthn.decode(parameters.get("signature"));
        if (!WebAuthn.verifySignature(credential.cosePublicKey(), signature, signedData)) {
            throw new PasskeyException("passkey assertion signature is invalid");
        }
        long stored = credential.signCount();
        long observed = authData.signCount();
        if ((observed > 0L || stored > 0L) && observed <= stored) {
            throw new PasskeyException("passkey sign counter replay detected");
        }
        if (observed > stored) {
            store.updateSignCount(id, observed);
        }

        boolean userVerified = (authData.flags() & 0x04) != 0;
        String sessionId = sessionId();
        Map<String, String> claims = new HashMap<String, String>();
        claims.put("credential_id", id);
        claims.put("rp_id", config.rpId());
        claims.put("sign_count", Long.toString(observed));
        claims.put("user_verified", userVerified ? "true" : "false");
        claims.put("session_id", sessionId);
        AuthIdentity identity =
                new AuthIdentity("passkey", id, "", "", false, userVerified, claims);
        store.putSession(sessionId, identity);
        return identity;
    }

    private void verifyRpId(WebAuthn.AuthenticatorData data) {
        byte[] expected = WebAuthn.sha256(config.rpId().getBytes(StandardCharsets.UTF_8));
        if (!Arrays.equals(expected, data.rpIdHash())) {
            throw new PasskeyException("passkey relying party hash mismatch");
        }
    }

    private String sessionId() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return WebAuthn.encode(bytes);
    }

    private void ensure(PluginContext context) {
        if (config == null) {
            config = new PasskeyConfig(context);
        }
        if (store == null) {
            Object injected = context.service(SERVICE_STORE);
            if (injected instanceof PasskeyStore) {
                store = (PasskeyStore) injected;
            } else {
                store = new PasskeyStore(config);
                context.putService(SERVICE_STORE, store);
            }
        }
    }
}
