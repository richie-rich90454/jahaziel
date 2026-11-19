/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.basis;

import com.nimbusds.jwt.JWTClaimsSet;

import dev.jahaziel.identity.auth.basis.TokenVerifier.TokenRejectedException;
import dev.jahaziel.identity.auth.basis.TokenVerifier.VerifiedToken;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Receiver for webhook verification, logout tokens, and access-token revocations. */
public final class BackchannelHandler {
    private static final Duration PROOF_TIMEOUT = Duration.ofSeconds(10);

    private final BasisConfig config;
    private final BasisHttp http;
    private final ObjectMapper mapper = new ObjectMapper();
    private final TokenVerifier verifier;
    private final BasisStore store;

    public BackchannelHandler(
            BasisConfig config, BasisHttp http, TokenVerifier verifier, BasisStore store) {
        this.config = config;
        this.http = http;
        this.verifier = verifier;
        this.store = store;
    }

    public HttpResult handle(Map<String, String> form) {
        String action = form.get("action");
        if ("test".equals(action)) {
            return verifyWebhook(form);
        }
        if (form.containsKey("logout_token")) {
            return logout(form);
        }
        if ("revoke_access_token".equals(action)) {
            return revoke(form);
        }
        return HttpResult.badRequest();
    }

    /** Validates a bearer access token, returning the standard rejection on any failure. */
    public HttpResult validateBearer(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            return HttpResult.invalidToken();
        }
        try {
            verifier.verifyAccessToken(header.substring(7).trim());
            return HttpResult.ok();
        } catch (TokenRejectedException rejected) {
            return HttpResult.invalidToken();
        }
    }

    private HttpResult verifyWebhook(Map<String, String> form) {
        String nonce = form.get("nonce");
        String challenge = form.get("challenge");
        if (nonce == null
                || challenge == null
                || config.internalUrl() == null
                || config.resourceSecret() == null) {
            return HttpResult.badRequest();
        }
        ObjectNode proof = mapper.createObjectNode();
        proof.put("nonce", nonce);
        proof.put("challenge_hash", hmacHex(config.resourceSecret(), challenge));
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Bearer " + config.resourceSecret());
        headers.put("X-Basis-Resource", BasisHttp.encode(config.resource()));
        try {
            http.postJson(
                    config.internalUrl() + "/internal/backchannel/validate",
                    mapper.writeValueAsString(proof),
                    headers,
                    PROOF_TIMEOUT);
        } catch (BasisException failure) {
            // The proof is advisory to this receiver: a failed post leaves the saved URL list
            // unchanged. Never log the secret, challenge, or proof.
        }
        return HttpResult.accepted();
    }

    private HttpResult logout(Map<String, String> form) {
        VerifiedToken verified =
                verifier.verifyBackchannelToken(
                        form.get("logout_token"), "logout+jwt", config.resource());
        if (verified.claims().getClaim(TokenVerifier.LOGOUT_EVENT) == null) {
            throw new TokenRejectedException("logout token lacks backchannel event");
        }
        String subject = stringClaim(verified.claims(), "sub");
        if (subject == null || subject.length() == 0) {
            throw new TokenRejectedException("logout token lacks sub");
        }
        store.removeSessionsForSubject(subject);
        return HttpResult.noContent();
    }

    private HttpResult revoke(Map<String, String> form) {
        String jti = form.get("jti");
        String validAfterText = form.get("valid_after");
        String token = form.get("revocation_token");
        if (jti == null || validAfterText == null || token == null) {
            return HttpResult.badRequest();
        }
        VerifiedToken verified =
                verifier.verifyBackchannelToken(token, "at-revocation+jwt", config.resource());
        JWTClaimsSet claims = verified.claims();
        if (!jti.equals(stringClaim(claims, "access_token_jti"))) {
            throw new TokenRejectedException("revocation jti mismatch");
        }
        long validAfter;
        try {
            validAfter = Long.parseLong(validAfterText);
        } catch (NumberFormatException failure) {
            throw new TokenRejectedException("malformed valid_after");
        }
        if (validAfter != numberClaim(claims, "valid_after")) {
            throw new TokenRejectedException("revocation valid_after mismatch");
        }
        store.addDeniedJti(jti, validAfter, numberClaim(claims, "access_token_expires_at"));
        return HttpResult.noContent();
    }

    private static String stringClaim(JWTClaimsSet claims, String name) {
        try {
            return claims.getStringClaim(name);
        } catch (ParseException failure) {
            throw new TokenRejectedException("malformed token claim " + name);
        }
    }

    private static long numberClaim(JWTClaimsSet claims, String name) {
        Object value = claims.getClaim(name);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException failure) {
                throw new TokenRejectedException("malformed token claim " + name);
            }
        }
        return 0L;
    }

    private static String hmacHex(String key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            char[] hex = new char[digest.length * 2];
            for (int index = 0; index < digest.length; index++) {
                int value = digest[index] & 0xff;
                hex[index * 2] = Character.forDigit(value >>> 4, 16);
                hex[index * 2 + 1] = Character.forDigit(value & 0x0f, 16);
            }
            return new String(hex);
        } catch (GeneralSecurityException failure) {
            throw new BasisException("cannot compute webhook proof", failure);
        }
    }
}
