/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.basis;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import dev.jahaziel.identity.auth.AuthIdentity;

import java.text.ParseException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** RS256 verification for ID, backchannel, and access tokens. Fails closed on every doubt. */
public final class TokenVerifier {
    public static final String LOGOUT_EVENT = "http://schemas.openid.net/event/backchannel-logout";
    private static final long CLOCK_SKEW_SECONDS = 60L;
    private static final long BACKCHANNEL_MAX_LIFETIME_MS = 130_000L;

    private final BasisConfig config;
    private final BasisMetadata metadata;
    private final BasisStore store;
    private final BasisInternalClient subjects;

    public TokenVerifier(BasisConfig config, BasisMetadata metadata, BasisStore store) {
        this(config, metadata, store, null);
    }

    /**
     * Creates a verifier with an optional live subject check. The legacy constructor keeps working
     * for the backchannel receiver; resource-API callers inject the internal client.
     */
    public TokenVerifier(
            BasisConfig config,
            BasisMetadata metadata,
            BasisStore store,
            BasisInternalClient subjects) {
        this.config = config;
        this.metadata = metadata;
        this.store = store;
        this.subjects = subjects;
    }

    public VerifiedToken verifyIdToken(String token, String expectedNonce, String accessToken) {
        SignedJWT jwt = parse(token);
        requireAlgorithm(jwt);
        verifySignature(jwt);
        JWTClaimsSet claims = claims(jwt);
        requireIssuer(claims);
        requireAudience(claims, config.clientId());
        requireExpiry(claims, 0L);
        String nonce = stringClaim(claims, "nonce");
        if (expectedNonce == null || !expectedNonce.equals(nonce)) {
            throw new TokenRejectedException("ID token nonce mismatch");
        }
        String hash = stringClaim(claims, "at_hash");
        if (hash != null && accessToken != null && !hash.equals(Pkce.atHash(accessToken))) {
            throw new TokenRejectedException("ID token access token hash mismatch");
        }
        return new VerifiedToken(jwt, claims);
    }

    public VerifiedToken verifyBackchannelToken(
            String token, String expectedType, String audience) {
        VerifiedToken verified = verifyBackchannelToken(token, expectedType, audience, 0L);
        Date expiry = verified.claims().getExpirationTime();
        if (expiry == null
                || expiry.getTime() - System.currentTimeMillis() > BACKCHANNEL_MAX_LIFETIME_MS) {
            throw new TokenRejectedException("backchannel token exceeds its two-minute lifetime");
        }
        return verified;
    }

    private VerifiedToken verifyBackchannelToken(
            String token, String expectedType, String audience, long skewSeconds) {
        SignedJWT jwt = parse(token);
        requireAlgorithm(jwt);
        requireType(jwt, expectedType);
        verifySignature(jwt);
        JWTClaimsSet claims = claims(jwt);
        requireIssuer(claims);
        requireAudience(claims, audience);
        requireExpiry(claims, skewSeconds);
        return new VerifiedToken(jwt, claims);
    }

    /** Verifies a resource access token and exposes its parsed scopes and permissions. */
    public VerifiedToken verifyAccessToken(String token) {
        VerifiedToken verified =
                verifyBackchannelToken(token, "at+jwt", config.resource(), CLOCK_SKEW_SECONDS);
        JWTClaimsSet claims = verified.claims();
        String subject = requiredText(claims, "sub");
        String jti = requiredText(claims, "jti");
        String scopesText = "";
        String permissionsText = "";
        if (subjects != null) {
            // Resource-API verification requires the full documented claim set. The legacy
            // constructor keeps the backchannel receiver's historical access-token contract.
            requiredText(claims, "client_id");
            requiredText(claims, "gty");
            scopesText = requiredText(claims, "scope");
            permissionsText = requiredText(claims, "permissions");
        }
        if (claims.getIssueTime() == null || claims.getExpirationTime() == null) {
            throw new TokenRejectedException("access token lacks iat or exp");
        }
        if (claims.getIssuer() == null || !config.issuer().equals(claims.getIssuer())) {
            throw new TokenRejectedException("access token lacks iss");
        }
        List<String> audiences = claims.getAudience();
        if (audiences == null || !audiences.contains(config.resource())) {
            throw new TokenRejectedException("access token lacks aud");
        }
        Set<String> scopes = new HashSet<String>();
        addWords(scopes, scopesText);
        Set<String> permissions = new HashSet<String>();
        addWords(permissions, permissionsText);
        if (subjects != null && (scopes.isEmpty() || permissions.isEmpty())) {
            throw new TokenRejectedException("access token has empty scope or permissions");
        }
        if (store.isDenied(jti, System.currentTimeMillis() / 1000L + CLOCK_SKEW_SECONDS)) {
            throw new TokenRejectedException("access token is revoked");
        }
        if (subjects != null) {
            try {
                subjects.verify(subject, claims.getIssueTime().getTime() / 1000L);
            } catch (BasisException failure) {
                throw new TokenRejectedException("access token subject rejected");
            }
        }
        return new VerifiedToken(verified.jwt(), claims, scopes, permissions);
    }

    public AuthIdentity identity(VerifiedToken verified) {
        JWTClaimsSet claims = verified.claims();
        String subject = stringClaim(claims, "sub");
        if (subject == null || subject.length() == 0) {
            throw new TokenRejectedException("ID token lacks sub");
        }
        Map<String, String> safe = new HashMap<String, String>();
        copy(safe, claims, "sub");
        copy(safe, claims, "email");
        copy(safe, claims, "name");
        copy(safe, claims, "email_verified");
        copy(safe, claims, "acr");
        copy(safe, claims, "amr");
        copy(safe, claims, "scope");
        copy(safe, claims, "permissions");
        copy(safe, claims, "gty");
        copy(safe, claims, "jti");
        return new AuthIdentity(
                "basis-auth",
                subject,
                stringClaim(claims, "email"),
                stringClaim(claims, "name"),
                booleanClaim(claims, "email_verified"),
                mfaAssured(claims),
                safe);
    }

    /** MFA is assured by a multi-factor amr entry or an MFA-bearing acr value. */
    public static boolean mfaAssured(JWTClaimsSet claims) {
        List<String> methods = stringList(claims, "amr");
        if (methods != null) {
            for (int index = 0; index < methods.size(); index++) {
                String method = methods.get(index);
                if ("mfa".equalsIgnoreCase(method) || "otp".equalsIgnoreCase(method)) {
                    return true;
                }
            }
        }
        String acr = stringClaim(claims, "acr");
        if (acr != null) {
            String normalized = acr.toLowerCase();
            if ("2".equals(acr) || normalized.contains("mfa")) {
                return true;
            }
        }
        return false;
    }

    private static void copy(Map<String, String> target, JWTClaimsSet claims, String name) {
        Object value = claims.getClaim(name);
        if (value == null) {
            return;
        }
        if (value instanceof List) {
            StringBuilder joined = new StringBuilder();
            List<?> values = (List<?>) value;
            for (int index = 0; index < values.size(); index++) {
                if (joined.length() > 0) {
                    joined.append(' ');
                }
                joined.append(String.valueOf(values.get(index)));
            }
            target.put(name, joined.toString());
            return;
        }
        target.put(name, String.valueOf(value));
    }

    private static SignedJWT parse(String token) {
        if (token == null || token.length() == 0) {
            throw new TokenRejectedException("empty token");
        }
        try {
            return SignedJWT.parse(token);
        } catch (ParseException failure) {
            throw new TokenRejectedException("malformed token");
        }
    }

    private static void requireAlgorithm(SignedJWT jwt) {
        if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) {
            throw new TokenRejectedException("unsupported token algorithm");
        }
    }

    private static void requireType(SignedJWT jwt, String expectedType) {
        JOSEObjectType type = jwt.getHeader().getType();
        if (type == null || !expectedType.equals(type.getType())) {
            throw new TokenRejectedException("unexpected token type");
        }
    }

    private void verifySignature(SignedJWT jwt) {
        try {
            if (!jwt.verify(metadata.verifierFor(jwt.getHeader().getKeyID()))) {
                throw new TokenRejectedException("bad token signature");
            }
        } catch (JOSEException failure) {
            throw new TokenRejectedException("signature check failed");
        }
    }

    private static JWTClaimsSet claims(SignedJWT jwt) {
        try {
            return jwt.getJWTClaimsSet();
        } catch (ParseException failure) {
            throw new TokenRejectedException("malformed token claims");
        }
    }

    private void requireIssuer(JWTClaimsSet claims) {
        if (!config.issuer().equals(claims.getIssuer())) {
            throw new TokenRejectedException("wrong token issuer");
        }
    }

    private static void requireAudience(JWTClaimsSet claims, String audience) {
        List<String> audiences = claims.getAudience();
        if (audiences == null || !audiences.contains(audience)) {
            throw new TokenRejectedException("wrong token audience");
        }
    }

    private static void requireExpiry(JWTClaimsSet claims, long skewSeconds) {
        Date expiry = claims.getExpirationTime();
        if (expiry == null
                || expiry.getTime() + skewSeconds * 1000L <= System.currentTimeMillis()) {
            throw new TokenRejectedException("expired token");
        }
    }

    private static String requiredText(JWTClaimsSet claims, String name) {
        String value = textClaim(claims, name);
        if (value == null || value.length() == 0) {
            throw new TokenRejectedException("access token lacks " + name);
        }
        return value;
    }

    private static String textClaim(JWTClaimsSet claims, String name) {
        Object value = claims.getClaim(name);
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof List) {
            StringBuilder joined = new StringBuilder();
            List<?> values = (List<?>) value;
            for (int index = 0; index < values.size(); index++) {
                if (joined.length() > 0) {
                    joined.append(' ');
                }
                joined.append(String.valueOf(values.get(index)));
            }
            return joined.toString();
        }
        throw new TokenRejectedException("malformed token claim " + name);
    }

    private static void addWords(Set<String> target, String text) {
        String[] words = text.trim().split("\\s+");
        for (int index = 0; index < words.length; index++) {
            if (words[index].length() > 0) {
                target.add(words[index]);
            }
        }
    }

    private static String stringClaim(JWTClaimsSet claims, String name) {
        try {
            return claims.getStringClaim(name);
        } catch (ParseException failure) {
            throw new TokenRejectedException("malformed token claim " + name);
        }
    }

    private static boolean booleanClaim(JWTClaimsSet claims, String name) {
        Object value = claims.getClaim(name);
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return false;
    }

    private static List<String> stringList(JWTClaimsSet claims, String name) {
        try {
            return claims.getStringListClaim(name);
        } catch (ParseException failure) {
            throw new TokenRejectedException("malformed token claim " + name);
        }
    }

    /** A token that failed verification. */
    public static final class TokenRejectedException extends BasisException {
        private static final long serialVersionUID = 1L;

        public TokenRejectedException(String message) {
            super(message);
        }
    }

    /**
     * Verified token plus its claims, with space-delimited scopes and permissions for access
     * tokens.
     */
    public static final class VerifiedToken {
        private final SignedJWT jwt;
        private final JWTClaimsSet claims;
        private final Set<String> scopes;
        private final Set<String> permissions;

        private VerifiedToken(SignedJWT jwt, JWTClaimsSet claims) {
            this(jwt, claims, Collections.<String>emptySet(), Collections.<String>emptySet());
        }

        private VerifiedToken(
                SignedJWT jwt, JWTClaimsSet claims, Set<String> scopes, Set<String> permissions) {
            this.jwt = jwt;
            this.claims = claims;
            this.scopes = Collections.unmodifiableSet(new HashSet<String>(scopes));
            this.permissions = Collections.unmodifiableSet(new HashSet<String>(permissions));
        }

        public SignedJWT jwt() {
            return jwt;
        }

        public JWTClaimsSet claims() {
            return claims;
        }

        public Set<String> scopes() {
            return scopes;
        }

        public Set<String> permissions() {
            return permissions;
        }
    }
}
