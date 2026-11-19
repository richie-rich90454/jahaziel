/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.passkey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.identity.auth.AuthIdentity;
import dev.jahaziel.identity.auth.PasskeyPlugin;
import dev.jahaziel.plugin.PluginContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.HashMap;
import java.util.Map;

final class PasskeyMfaTest {
    private static final String ORIGIN = "https://app.example.org";
    private static final String RP_ID = "example.org";
    private static final String CHALLENGE = "fixed-challenge-1234567890";
    private static final byte[] CREDENTIAL_ID = "mfa-credential".getBytes(StandardCharsets.UTF_8);

    @TempDir Path temp;

    private PluginContext context;
    private PasskeyPlugin plugin;
    private PrivateKey privateKey;
    private byte[] cose;

    @BeforeEach
    void setUp() throws Exception {
        Map<String, String> settings = new HashMap<String, String>();
        settings.put("passkey.origin", ORIGIN);
        settings.put("passkey.rp-id", RP_ID);
        context = new PluginContext(temp, settings);
        plugin = new PasskeyPlugin();
        plugin.initialize(context);

        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = generator.generateKeyPair();
        privateKey = pair.getPrivate();
        ECPublicKey publicKey = (ECPublicKey) pair.getPublic();
        cose = cose(fixed32(publicKey.getW().getAffineX()), fixed32(publicKey.getW().getAffineY()));
        PasskeyStore store = (PasskeyStore) context.service(PasskeyPlugin.SERVICE_STORE);
        store.putCredential(new PasskeyCredential(WebAuthn.encode(CREDENTIAL_ID), cose, 0L, RP_ID));
    }

    @Test
    void verifiedAssertionMarksMfaAssured() {
        AuthIdentity identity = plugin.handleCallback(context, assertion("uv-yes", 0x05, 1L));

        assertTrue(identity.mfaAssured());
        assertEquals("true", identity.claims().get("user_verified"));
        assertTrue(
                plugin.currentIdentity(context, identity.claims().get("session_id")).mfaAssured());
    }

    @Test
    void presenceOnlyAssertionIsAllowedButNotMfaAssured() {
        AuthIdentity identity = plugin.handleCallback(context, assertion("uv-no", 0x01, 2L));

        assertFalse(identity.mfaAssured());
        assertEquals("false", identity.claims().get("user_verified"));
        assertFalse(
                plugin.currentIdentity(context, identity.claims().get("session_id")).mfaAssured());
    }

    private Map<String, String> assertion(String state, int flags, long count) {
        PasskeyStore store = (PasskeyStore) context.service(PasskeyPlugin.SERVICE_STORE);
        store.putPending(
                state, new PasskeyPending(CHALLENGE, "", "", System.currentTimeMillis() + 60000L));
        byte[] client = clientData();
        byte[] data = authData(flags, count);
        byte[] signed = WebAuthn.concat(data, WebAuthn.sha256(client));

        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("state", state);
        parameters.put("credentialId", WebAuthn.encode(CREDENTIAL_ID));
        parameters.put("authenticatorData", WebAuthn.encode(data));
        parameters.put("clientDataJSON", WebAuthn.encode(client));
        parameters.put("signature", WebAuthn.encode(sign(signed)));
        return parameters;
    }

    private byte[] sign(byte[] data) {
        try {
            Signature signer = Signature.getInstance("SHA256withECDSA");
            signer.initSign(privateKey);
            signer.update(data);
            return signer.sign();
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static byte[] clientData() {
        String json =
                "{\"type\":\"webauthn.get\",\"challenge\":\""
                        + CHALLENGE
                        + "\",\"origin\":\""
                        + ORIGIN
                        + "\",\"crossOrigin\":false}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] authData(int flags, long count) {
        byte[] data = new byte[37];
        byte[] hash = WebAuthn.sha256(RP_ID.getBytes(StandardCharsets.UTF_8));
        System.arraycopy(hash, 0, data, 0, 32);
        data[32] = (byte) flags;
        data[33] = (byte) (count >>> 24);
        data[34] = (byte) (count >>> 16);
        data[35] = (byte) (count >>> 8);
        data[36] = (byte) count;
        return data;
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
}
