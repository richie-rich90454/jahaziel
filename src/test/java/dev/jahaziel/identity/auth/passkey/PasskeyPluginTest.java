/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.passkey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.jahaziel.identity.auth.AuthCapability;
import dev.jahaziel.identity.auth.AuthIdentity;
import dev.jahaziel.identity.auth.PasskeyPlugin;
import dev.jahaziel.plugin.PluginContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class PasskeyPluginTest {
    private static final String ORIGIN = "https://app.example.org";
    private static final String RP_ID = "example.org";
    private static final String CHALLENGE = "fixed-challenge-1234567890";
    private static final byte[] CREDENTIAL_ID = "credential-0001".getBytes(StandardCharsets.UTF_8);

    @TempDir Path temp;

    private PluginContext context;
    private PasskeyPlugin plugin;
    private PasskeyStore store;
    private KeyPair keys;
    private byte[] cose;

    @BeforeEach
    void setUp() throws Exception {
        Map<String, String> settings = new HashMap<String, String>();
        settings.put("passkey.origin", ORIGIN);
        settings.put("passkey.rp-id", RP_ID);
        context = new PluginContext(temp, settings);
        plugin = new PasskeyPlugin();
        plugin.initialize(context);
        store = (PasskeyStore) context.service(PasskeyPlugin.SERVICE_STORE);
        keys = keyPair();
        ECPublicKeyHolder holder = new ECPublicKeyHolder(keys);
        cose = cose(holder.x(), holder.y());
        store.putCredential(new PasskeyCredential(WebAuthn.encode(CREDENTIAL_ID), cose, 0L, RP_ID));
    }

    @Test
    void descriptorAdvertisesPasskeyAndMfaAssurance() {
        assertEquals("passkey", plugin.descriptor().name());
        assertTrue(plugin.supports(AuthCapability.PASSKEY));
        assertTrue(plugin.supports(AuthCapability.MFA_ASSURANCE));
        assertFalse(plugin.supports(AuthCapability.PASSWORD));
    }

    @Test
    void startLoginStoresChallengeUnderDataDir() {
        String options =
                plugin.startLogin(context, "state-login", "nonce", "https://app.example.org/cb");
        assertTrue(options.contains("\"challenge\""));
        assertTrue(options.contains(RP_ID));
        assertTrue(options.contains(ORIGIN));
        PasskeyPending pending = store.takePending("state-login");
        assertNotNull(pending);
        assertFalse(pending.challenge().isEmpty());
        assertNull(store.takePending("state-login"));
    }

    @Test
    void verifiedAssertionYieldsMfaAssuredIdentityAndSession() {
        Map<String, String> parameters =
                assertionParameters("state-get-1", CHALLENGE, ORIGIN, 1L, 0x05, keys.getPrivate());

        AuthIdentity identity = plugin.handleCallback(context, parameters);
        assertEquals("passkey", identity.provider());
        assertEquals(WebAuthn.encode(CREDENTIAL_ID), identity.subject());
        assertTrue(identity.mfaAssured());
        assertFalse(identity.emailVerified());
        assertEquals("true", identity.claims().get("user_verified"));
        String sessionId = identity.claims().get("session_id");
        assertNotNull(sessionId);
        assertEquals(identity.subject(), plugin.currentIdentity(context, sessionId).subject());
        assertTrue(plugin.currentIdentity(context, sessionId).mfaAssured());

        plugin.endSession(context, sessionId);
        assertNull(plugin.currentIdentity(context, sessionId));
    }

    @Test
    void tamperedSignatureIsRejected() {
        Map<String, String> parameters =
                assertionParameters("state-get-2", CHALLENGE, ORIGIN, 2L, 0x05, keys.getPrivate());
        byte[] signature = WebAuthn.decode(parameters.get("signature"));
        signature[signature.length - 1] ^= 0x01;
        parameters.put("signature", WebAuthn.encode(signature));

        expectFailure(parameters, "assertion");
    }

    @Test
    void wrongChallengeIsRejected() {
        Map<String, String> parameters =
                assertionParameters("state-get-3", CHALLENGE, ORIGIN, 3L, 0x05, keys.getPrivate());
        byte[] client = clientData("webauthn.get", "wrong-challenge", ORIGIN);
        parameters.put("clientDataJSON", WebAuthn.encode(client));
        expectFailure(parameters, "challenge");
    }

    @Test
    void wrongOriginIsRejected() {
        Map<String, String> parameters =
                assertionParameters("state-get-4", CHALLENGE, ORIGIN, 4L, 0x05, keys.getPrivate());
        byte[] client = clientData("webauthn.get", CHALLENGE, "https://evil.example.org");
        parameters.put("clientDataJSON", WebAuthn.encode(client));
        expectFailure(parameters, "origin");
    }

    @Test
    void wrongRelyingPartyHashIsRejected() {
        Map<String, String> parameters =
                assertionParameters("state-get-5", CHALLENGE, ORIGIN, 5L, 0x05, keys.getPrivate());
        parameters.put("authenticatorData", WebAuthn.encode(authData("evil.org", 0x05, 5L)));
        expectFailure(parameters, "relying party");
    }

    @Test
    void missingUserPresenceIsRejected() {
        Map<String, String> parameters =
                assertionParameters("state-get-6", CHALLENGE, ORIGIN, 6L, 0x04, keys.getPrivate());
        expectFailure(parameters, "presence");
    }

    @Test
    void signCounterReplayIsRejected() throws Exception {
        store.putCredential(
                new PasskeyCredential(WebAuthn.encode(CREDENTIAL_ID), cose, 10L, RP_ID));
        Map<String, String> replay =
                assertionParameters("state-get-7", CHALLENGE, ORIGIN, 10L, 0x05, keys.getPrivate());
        expectFailure(replay, "replay");

        Map<String, String> advanced =
                assertionParameters("state-get-8", CHALLENGE, ORIGIN, 11L, 0x05, keys.getPrivate());
        AuthIdentity identity = plugin.handleCallback(context, advanced);
        assertTrue(identity.mfaAssured());
        assertEquals(11L, store.getCredential(WebAuthn.encode(CREDENTIAL_ID)).signCount());
    }

    @Test
    void noneAttestationRegistrationStoresCredentialWithoutMfaClaim() {
        String state = "state-create-1";
        pending(state);
        byte[] credentialId = "registered-credential".getBytes(StandardCharsets.UTF_8);
        byte[] data = authDataWithCredential(RP_ID, 0x45, 0L, credentialId, cose);
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("state", state);
        parameters.put(
                "clientDataJSON",
                WebAuthn.encode(clientData("webauthn.create", CHALLENGE, ORIGIN)));
        parameters.put("attestationObject", WebAuthn.encode(attestation(data, "none")));

        AuthIdentity identity = plugin.handleCallback(context, parameters);
        assertEquals(WebAuthn.encode(credentialId), identity.subject());
        assertFalse(identity.mfaAssured());
        assertNotNull(store.getCredential(WebAuthn.encode(credentialId)));
    }

    @Test
    void unsupportedAttestationFormatIsReportedNotSilentlyAccepted() {
        String state = "state-create-2";
        pending(state);
        byte[] credentialId = "unsupported-credential".getBytes(StandardCharsets.UTF_8);
        byte[] data = authDataWithCredential(RP_ID, 0x45, 0L, credentialId, cose);
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("state", state);
        parameters.put(
                "clientDataJSON",
                WebAuthn.encode(clientData("webauthn.create", CHALLENGE, ORIGIN)));
        parameters.put("attestationObject", WebAuthn.encode(attestation(data, "packed")));

        expectFailure(parameters, "scoped subset");
        assertNull(store.getCredential(WebAuthn.encode(credentialId)));
    }

    @Test
    void persistedFileStaysUnderTheDataDir() throws IOException {
        Map<String, String> parameters =
                assertionParameters("state-get-9", CHALLENGE, ORIGIN, 12L, 0x05, keys.getPrivate());
        plugin.handleCallback(context, parameters);

        final List<String> relative = new ArrayList<String>();
        Files.walkFileTree(
                temp,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                        relative.add(temp.relativize(file).toString().replace('\\', '/'));
                        return FileVisitResult.CONTINUE;
                    }
                });
        assertTrue(relative.contains("passkey/passkey-store.json"));
        for (int index = 0; index < relative.size(); index++) {
            assertTrue(relative.get(index).startsWith("passkey/"), relative.get(index));
        }
    }

    private void expectFailure(Map<String, String> parameters, String messagePart) {
        try {
            plugin.handleCallback(context, parameters);
            fail("assertion must be rejected");
        } catch (PasskeyException expected) {
            assertTrue(expected.getMessage().contains(messagePart), expected.getMessage());
        }
    }

    private void pending(String state) {
        store.putPending(
                state, new PasskeyPending(CHALLENGE, "", "", System.currentTimeMillis() + 60000L));
    }

    private Map<String, String> assertionParameters(
            String state,
            String challenge,
            String origin,
            long count,
            int flags,
            PrivateKey privateKey) {
        pending(state);
        byte[] client = clientData("webauthn.get", challenge, origin);
        byte[] data = authData(RP_ID, flags, count);
        byte[] signed = WebAuthn.concat(data, WebAuthn.sha256(client));
        byte[] signature = sign(signed, privateKey);
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("state", state);
        parameters.put("credentialId", WebAuthn.encode(CREDENTIAL_ID));
        parameters.put("authenticatorData", WebAuthn.encode(data));
        parameters.put("clientDataJSON", WebAuthn.encode(client));
        parameters.put("signature", WebAuthn.encode(signature));
        return parameters;
    }

    private static KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private static byte[] sign(byte[] data, PrivateKey privateKey) {
        try {
            Signature signer = Signature.getInstance("SHA256withECDSA");
            signer.initSign(privateKey);
            signer.update(data);
            return signer.sign();
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static byte[] fixed32(BigInteger value) {
        byte[] raw = value.toByteArray();
        byte[] out = new byte[32];
        if (raw.length > 32) {
            System.arraycopy(raw, raw.length - 32, out, 0, 32);
        } else {
            System.arraycopy(raw, 0, out, 32 - raw.length, raw.length);
        }
        return out;
    }

    private static byte[] cose(byte[] x, byte[] y) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xA5);
        out.write(0x01);
        out.write(0x02);
        out.write(0x03);
        out.write(0x26);
        out.write(0x20);
        out.write(0x01);
        out.write(0x21);
        byteString(out, x);
        out.write(0x22);
        byteString(out, y);
        return out.toByteArray();
    }

    private static byte[] clientData(String type, String challenge, String origin) {
        String json =
                "{\"type\":\""
                        + type
                        + "\",\"challenge\":\""
                        + challenge
                        + "\",\"origin\":\""
                        + origin
                        + "\",\"crossOrigin\":false}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] authData(String rpId, int flags, long count) {
        byte[] data = new byte[37];
        byte[] hash = WebAuthn.sha256(rpId.getBytes(StandardCharsets.UTF_8));
        System.arraycopy(hash, 0, data, 0, 32);
        data[32] = (byte) flags;
        data[33] = (byte) (count >>> 24);
        data[34] = (byte) (count >>> 16);
        data[35] = (byte) (count >>> 8);
        data[36] = (byte) count;
        return data;
    }

    private static byte[] authDataWithCredential(
            String rpId, int flags, long count, byte[] credentialId, byte[] coseKey) {
        byte[] base = authData(rpId, flags, count);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(base, 0, base.length);
        out.write(new byte[16], 0, 16);
        out.write((credentialId.length >>> 8) & 0xFF);
        out.write(credentialId.length & 0xFF);
        out.write(credentialId, 0, credentialId.length);
        out.write(coseKey, 0, coseKey.length);
        return out.toByteArray();
    }

    private static byte[] attestation(byte[] authData, String format) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xA3);
        text(out, "fmt");
        text(out, format);
        text(out, "attStmt");
        out.write(0xA0);
        text(out, "authData");
        byteString(out, authData);
        return out.toByteArray();
    }

    private static void byteString(ByteArrayOutputStream out, byte[] value) {
        if (value.length < 24) {
            out.write(0x40 | value.length);
        } else if (value.length < 256) {
            out.write(0x58);
            out.write(value.length);
        } else {
            out.write(0x59);
            out.write((value.length >>> 8) & 0xFF);
            out.write(value.length & 0xFF);
        }
        out.write(value, 0, value.length);
    }

    private static void text(ByteArrayOutputStream out, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.write(0x60 | bytes.length);
        out.write(bytes, 0, bytes.length);
    }

    /** Pulls the P-256 public coordinates out of the generated key pair. */
    private static final class ECPublicKeyHolder {
        private final byte[] x;
        private final byte[] y;

        ECPublicKeyHolder(KeyPair pair) {
            java.security.interfaces.ECPublicKey publicKey =
                    (java.security.interfaces.ECPublicKey) pair.getPublic();
            this.x = fixed32(publicKey.getW().getAffineX());
            this.y = fixed32(publicKey.getW().getAffineY());
        }

        byte[] x() {
            return x.clone();
        }

        byte[] y() {
            return y.clone();
        }
    }
}
