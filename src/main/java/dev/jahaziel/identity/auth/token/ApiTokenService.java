/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Issues, verifies, rotates, and revokes scoped tokens. Only hashes are persisted. */
public final class ApiTokenService {
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final String PREFIX = "jat_";

    private final ApiTokenConfig config;
    private final ApiTokenStore store;
    private final TokenAudit audit;
    private final SecureRandom random = new SecureRandom();

    public ApiTokenService(ApiTokenConfig config, ApiTokenStore store, TokenAudit audit) {
        this.config = config;
        this.store = store;
        this.audit = audit;
    }

    public String issue(String subject, String email, String displayName, String scopes) {
        if (subject == null || subject.length() == 0) {
            throw new ApiTokenException("token subject is required");
        }
        String token = generate();
        TokenRecord record =
                new TokenRecord(
                        tokenId(hash(token)),
                        hash(token),
                        subject,
                        email == null ? "" : email,
                        displayName == null ? "" : displayName,
                        parseScopes(scopes),
                        System.currentTimeMillis() + config.ttlMillis(),
                        true);
        store.put(record);
        audit.record("issue", record.id());
        return token;
    }

    public TokenRecord verify(String token) {
        return verifyAt(token, System.currentTimeMillis());
    }

    public TokenRecord verifyAt(String token, long nowMillis) {
        if (token == null || token.length() == 0) {
            audit.record("deny", "missing-token");
            return null;
        }
        TokenRecord record = store.findByHash(hash(token));
        if (record == null) {
            audit.record("deny", "unknown-token");
            return null;
        }
        if (!record.active()) {
            audit.record("deny", "inactive-token");
            return null;
        }
        if (nowMillis >= record.expiresAt()) {
            store.put(
                    new TokenRecord(
                            record.id(),
                            record.hash(),
                            record.subject(),
                            record.email(),
                            record.displayName(),
                            record.scopes(),
                            record.expiresAt(),
                            false));
            audit.record("expire", record.id());
            return null;
        }
        audit.record("use", record.id());
        return record;
    }

    public boolean hasScope(String token, String scope) {
        TokenRecord record = verify(token);
        return record != null && record.hasScope(scope);
    }

    public String rotate(String token) {
        TokenRecord current = verify(token);
        if (current == null) {
            throw new ApiTokenException("token is invalid or expired");
        }
        String replacement = generate();
        store.put(
                new TokenRecord(
                        current.id(),
                        hash(replacement),
                        current.subject(),
                        current.email(),
                        current.displayName(),
                        current.scopes(),
                        current.expiresAt(),
                        true));
        audit.record("rotate", current.id());
        return replacement;
    }

    public boolean revoke(String token) {
        if (token == null || token.length() == 0) {
            return false;
        }
        TokenRecord current = store.findByHash(hash(token));
        if (current == null || !current.active()) {
            return false;
        }
        store.put(
                new TokenRecord(
                        current.id(),
                        current.hash(),
                        current.subject(),
                        current.email(),
                        current.displayName(),
                        current.scopes(),
                        current.expiresAt(),
                        false));
        audit.record("revoke", current.id());
        return true;
    }

    public TokenAudit audit() {
        return audit;
    }

    public ApiTokenConfig config() {
        return config;
    }

    private String generate() {
        byte[] value = new byte[32];
        random.nextBytes(value);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String hash(String token) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder text = new StringBuilder(digest.length * 2);
            for (int index = 0; index < digest.length; index++) {
                int value = digest[index] & 0xFF;
                text.append(HEX[value >>> 4]).append(HEX[value & 0x0F]);
            }
            return text.toString();
        } catch (Exception failure) {
            throw new ApiTokenException("SHA-256 is unavailable", failure);
        }
    }

    private static String tokenId(String hash) {
        return hash.substring(0, 12);
    }

    private static List<String> parseScopes(String raw) {
        List<String> scopes = new ArrayList<String>();
        if (raw == null || raw.trim().length() == 0) {
            return scopes;
        }
        String[] parts = raw.trim().split("[,\\s]+");
        for (int index = 0; index < parts.length; index++) {
            if (parts[index].length() > 0) {
                scopes.add(parts[index]);
            }
        }
        return scopes;
    }
}
