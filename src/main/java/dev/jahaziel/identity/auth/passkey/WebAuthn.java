/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.passkey;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * JDK-only WebAuthn verification helpers. Supports ES256 (P-256) assertion signatures, client-data
 * challenge/origin checks, relying-party hash checks, and sign-counter replay detection.
 * Attestation statement signatures are not verified (see PasskeyPlugin).
 */
public final class WebAuthn {
    private static final int FLAG_USER_PRESENT = 0x01;
    private static final int FLAG_ATTESTED_DATA = 0x40;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WebAuthn() {}

    public static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    public static byte[] decode(String value) {
        if (value == null) {
            throw new PasskeyException("base64url value is missing");
        }
        try {
            return Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException failure) {
            throw new PasskeyException("base64url value is invalid", failure);
        }
    }

    public static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (Exception failure) {
            throw new PasskeyException("SHA-256 is unavailable", failure);
        }
    }

    public static byte[] concat(byte[] first, byte[] second) {
        byte[] joined = new byte[first.length + second.length];
        System.arraycopy(first, 0, joined, 0, first.length);
        System.arraycopy(second, 0, joined, first.length, second.length);
        return joined;
    }

    public static ClientData parseClientData(byte[] raw) {
        JsonNode node;
        try {
            node = MAPPER.readTree(raw);
        } catch (Exception failure) {
            throw new PasskeyException("clientDataJSON is not valid JSON", failure);
        }
        if (node == null || !node.isObject()) {
            throw new PasskeyException("clientDataJSON is not an object");
        }
        String type = text(node, "type");
        String challenge = text(node, "challenge");
        String origin = text(node, "origin");
        if (type == null || challenge == null || origin == null) {
            throw new PasskeyException("clientDataJSON is missing required fields");
        }
        return new ClientData(raw, type, challenge, origin);
    }

    public static AuthenticatorData parseAuthenticatorData(byte[] raw) {
        if (raw == null || raw.length < 37) {
            throw new PasskeyException("authenticator data is too short");
        }
        byte[] rpIdHash = Arrays.copyOfRange(raw, 0, 32);
        int flags = raw[32] & 0xFF;
        long signCount =
                ((long) (raw[33] & 0xFF) << 24)
                        | ((long) (raw[34] & 0xFF) << 16)
                        | ((long) (raw[35] & 0xFF) << 8)
                        | (long) (raw[36] & 0xFF);
        byte[] credentialId = null;
        byte[] cosePublicKey = null;
        if ((flags & FLAG_ATTESTED_DATA) != 0) {
            if (raw.length < 55) {
                throw new PasskeyException("attested credential data is truncated");
            }
            int length = ((raw[53] & 0xFF) << 8) | (raw[54] & 0xFF);
            int start = 55;
            if (length < 0 || start + length > raw.length) {
                throw new PasskeyException("credential id length is invalid");
            }
            credentialId = Arrays.copyOfRange(raw, start, start + length);
            cosePublicKey = Arrays.copyOfRange(raw, start + length, raw.length);
        }
        return new AuthenticatorData(raw, rpIdHash, flags, signCount, credentialId, cosePublicKey);
    }

    public static AttestationObject parseAttestationObject(byte[] raw) {
        CborReader reader = new CborReader(raw);
        int count = reader.readMapLength();
        String format = null;
        byte[] authData = null;
        for (int index = 0; index < count; index++) {
            String key = reader.readTextString();
            if ("fmt".equals(key)) {
                format = reader.readTextString();
            } else if ("attStmt".equals(key)) {
                reader.skipValue();
            } else if ("authData".equals(key)) {
                authData = reader.readByteString();
            } else {
                reader.skipValue();
            }
        }
        if (format == null || authData == null) {
            throw new PasskeyException("attestation object is missing fmt or authData");
        }
        return new AttestationObject(format, authData);
    }

    public static boolean userPresent(AuthenticatorData data) {
        return (data.flags() & FLAG_USER_PRESENT) != 0;
    }

    public static boolean verifySignature(
            byte[] cosePublicKey, byte[] signature, byte[] signedData) {
        CoseKey key = CoseKey.parse(cosePublicKey);
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            ECParameterSpec spec = parameters.getParameterSpec(ECParameterSpec.class);
            ECPoint point = new ECPoint(new BigInteger(1, key.x()), new BigInteger(1, key.y()));
            PublicKey publicKey =
                    KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, spec));
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(publicKey);
            verifier.update(signedData);
            return verifier.verify(signature);
        } catch (PasskeyException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new PasskeyException("unable to verify passkey assertion", failure);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    /** The decoded clientDataJSON fields plus the raw bytes that are hashed into the signature. */
    public static final class ClientData {
        private final byte[] raw;
        private final String type;
        private final String challenge;
        private final String origin;

        private ClientData(byte[] raw, String type, String challenge, String origin) {
            this.raw = raw;
            this.type = type;
            this.challenge = challenge;
            this.origin = origin;
        }

        public byte[] raw() {
            return raw.clone();
        }

        public String type() {
            return type;
        }

        public String challenge() {
            return challenge;
        }

        public String origin() {
            return origin;
        }
    }

    /** Parsed authenticator data, including attested credential data when present. */
    public static final class AuthenticatorData {
        private final byte[] raw;
        private final byte[] rpIdHash;
        private final int flags;
        private final long signCount;
        private final byte[] credentialId;
        private final byte[] cosePublicKey;

        private AuthenticatorData(
                byte[] raw,
                byte[] rpIdHash,
                int flags,
                long signCount,
                byte[] credentialId,
                byte[] cosePublicKey) {
            this.raw = raw;
            this.rpIdHash = rpIdHash;
            this.flags = flags;
            this.signCount = signCount;
            this.credentialId = credentialId;
            this.cosePublicKey = cosePublicKey;
        }

        public byte[] raw() {
            return raw.clone();
        }

        public byte[] rpIdHash() {
            return rpIdHash.clone();
        }

        public int flags() {
            return flags;
        }

        public long signCount() {
            return signCount;
        }

        public byte[] credentialId() {
            return credentialId == null ? null : credentialId.clone();
        }

        public byte[] cosePublicKey() {
            return cosePublicKey == null ? null : cosePublicKey.clone();
        }
    }

    /** The fmt and authData members of an attestation object. */
    public static final class AttestationObject {
        private final String format;
        private final byte[] authData;

        private AttestationObject(String format, byte[] authData) {
            this.format = format;
            this.authData = authData;
        }

        public String format() {
            return format;
        }

        public byte[] authData() {
            return authData.clone();
        }
    }
}
