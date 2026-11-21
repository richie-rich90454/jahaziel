/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.basis;

import com.nimbusds.jwt.JWTClaimsSet;

import dev.jahaziel.identity.auth.basis.TokenVerifier.TokenRejectedException;
import dev.jahaziel.identity.auth.basis.TokenVerifier.VerifiedToken;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Receiver for webhook verification, logout tokens, and access-token revocations. */
public final class BackchannelHandler {
    private static final Duration PROOF_TIMEOUT = Duration.ofSeconds(10);
    private static final Logger log = LoggerFactory.getLogger(BackchannelHandler.class);

    private final BasisConfig config;
    private final BasisHttp http;
    private final ObjectMapper mapper = new ObjectMapper();
    private final TokenVerifier verifier;
    private final BasisStore store;
    private final ExecutorService proofs =
            new ThreadPoolExecutor(
                    2,
                    2,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<Runnable>(16),
                    new ProofThreads(),
                    new ThreadPoolExecutor.DiscardPolicy());

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
        final String nonce = form.get("nonce");
        final String challenge = form.get("challenge");
        if (!isChallenge(nonce)
                || !isChallenge(challenge)
                || config.internalUrl() == null
                || config.resourceSecret() == null) {
            return HttpResult.badRequest();
        }
        proofs.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        postProof(nonce, challenge);
                    }
                });
        return HttpResult.accepted();
    }

    /** Posts the HMAC proof off the request thread; the caller has already returned 202. */
    private void postProof(String nonce, String challenge) {
        ObjectNode proof = mapper.createObjectNode();
        proof.put("nonce", nonce);
        proof.put("challenge_hash", hmacHex(config.resourceSecret(), challenge));
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Bearer " + config.resourceSecret());
        headers.put("X-Basis-Resource", BasisHttp.encode(config.resource()));
        int status;
        try {
            status =
                    http.postJsonStatus(
                            config.internalUrl() + "/internal/backchannel/validate",
                            mapper.writeValueAsString(proof),
                            headers,
                            PROOF_TIMEOUT);
        } catch (BasisException failure) {
            // The proof is advisory: a failed post leaves the saved URL list unchanged. Never log
            // the secret, challenge, nonce, or proof.
            log.warn("basis webhook proof could not be delivered");
            return;
        }
        if (status == 200) {
            log.debug("basis webhook proof accepted");
        } else if (status == 400) {
            log.warn("basis webhook proof rejected as invalid");
        } else if (status == 401) {
            log.warn("basis webhook proof rejected: unauthorized");
        } else if (status == 408) {
            log.warn("basis webhook proof expired; save the URL again");
        } else {
            log.warn("basis webhook proof returned HTTP {}", Integer.valueOf(status));
        }
    }

    /** A one-time webhook value is exactly 43 base64url characters. */
    private static boolean isChallenge(String value) {
        if (value == null || value.length() != 43) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            boolean allowed =
                    (current >= 'A' && current <= 'Z')
                            || (current >= 'a' && current <= 'z')
                            || (current >= '0' && current <= '9')
                            || current == '-'
                            || current == '_';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    private HttpResult logout(Map<String, String> form) {
        VerifiedToken verified =
                verifier.verifyBackchannelToken(
                        form.get("logout_token"), "logout+jwt", config.resource());
        Object event = verified.claims().getClaim(TokenVerifier.LOGOUT_EVENT);
        if (!(event instanceof Map) || !((Map<?, ?>) event).isEmpty()) {
            throw new TokenRejectedException("logout token lacks an empty backchannel event");
        }
        String subject = stringClaim(verified.claims(), "sub");
        if (subject == null || subject.length() == 0) {
            throw new TokenRejectedException("logout token lacks sub");
        }
        store.removeSessionsForSubject(subject);
        return HttpResult.loggedOut(subject);
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
        String subject = stringClaim(claims, "sub");
        if (subject == null || subject.length() == 0) {
            // No signed subject: deny the JTI only. Do not guess which sessions to end.
            return HttpResult.noContent();
        }
        store.removeSessionsForSubject(subject);
        // 204 with a subject makes the BFF controller end the (provider, sub) linked sessions.
        return HttpResult.loggedOut(subject);
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

    /** Bounded daemon threads so proof delivery never holds up the request thread or shutdown. */
    private static final class ProofThreads implements ThreadFactory {
        private final AtomicInteger count = new AtomicInteger();

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "basis-webhook-proof-" + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
