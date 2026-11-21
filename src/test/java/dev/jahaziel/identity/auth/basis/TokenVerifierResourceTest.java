/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.basis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

final class TokenVerifierResourceTest {
    private static final String CLIENT_ID = "example-web";
    private static final String RESOURCE = "urn:example:api";
    private static final String RESOURCE_SECRET = "resource-secret-1234567890";

    @TempDir Path temp;

    private LoopbackIdp idp;
    private BasisInternalStub stub;
    private BasisConfig config;
    private BasisStore store;
    private BasisInternalClient subjects;
    private TokenVerifier verifier;
    private int sequence;

    @BeforeEach
    void setUp() throws Exception {
        idp = LoopbackIdp.start(CLIENT_ID, "client-secret-1234567890", RESOURCE);
        stub = BasisInternalStub.start();
        Map<String, String> settings = new HashMap<String, String>();
        settings.put("basis.auth-issuer", idp.issuer());
        settings.put("basis.client-id", CLIENT_ID);
        settings.put("basis.resource", RESOURCE);
        settings.put("basis.resource-secret", RESOURCE_SECRET);
        settings.put("basis.internal-url", stub.url());
        config = new BasisConfig(new dev.jahaziel.plugin.PluginContext(temp, settings));
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        BasisHttp http = new BasisHttp(client);
        store = new BasisStore(config);
        subjects = new BasisInternalClient(stub.url(), RESOURCE, RESOURCE_SECRET, client);
        verifier = new TokenVerifier(config, new BasisMetadata(config, http), store, subjects);
    }

    @AfterEach
    void tearDown() {
        stub.close();
        idp.close();
    }

    @Test
    void validTokenExposesParsedScopesAndPermissions() {
        TokenVerifier.VerifiedToken verified =
                verifier.verifyAccessToken(
                        idp.sign(
                                "at+jwt",
                                access(
                                        RESOURCE,
                                        "projects.read projects.write",
                                        "projects.member other.permission")));

        assertTrue(verified.scopes().contains("projects.read"));
        assertTrue(verified.scopes().contains("projects.write"));
        assertTrue(verified.permissions().contains("projects.member"));
        assertEquals("basis-sub-1", verified.claims().getSubject());
    }

    @Test
    void wrongAudienceIsRejected() {
        assertThrows(
                TokenVerifier.TokenRejectedException.class,
                new Verify(access("urn:wrong:api", "projects.read", "projects.member")));
    }

    @Test
    void everyDocumentedClaimIsRequired() {
        Map<String, Object> missingClient = access(RESOURCE, "projects.read", "projects.member");
        missingClient.remove("client_id");
        assertThrows(TokenVerifier.TokenRejectedException.class, new Verify(missingClient));

        Map<String, Object> missingGty = access(RESOURCE, "projects.read", "projects.member");
        missingGty.remove("gty");
        assertThrows(TokenVerifier.TokenRejectedException.class, new Verify(missingGty));
    }

    @Test
    void expiredTokenIsRejectedButSmallSkewIsTolerated() {
        long now = System.currentTimeMillis() / 1000L;
        Map<String, Object> slightlyExpired = access(RESOURCE, "projects.read", "projects.member");
        slightlyExpired.put("exp", Long.valueOf(now - 10L));
        verifier.verifyAccessToken(idp.sign("at+jwt", slightlyExpired));

        Map<String, Object> expired = access(RESOURCE, "projects.read", "projects.member");
        expired.put("exp", Long.valueOf(now - 120L));
        assertThrows(TokenVerifier.TokenRejectedException.class, new Verify(expired));
    }

    @Test
    void disabledUserIsRejected() {
        stub.disabled(true);
        assertThrows(
                TokenVerifier.TokenRejectedException.class,
                new Verify(access(RESOURCE, "projects.read", "projects.member")));
    }

    @Test
    void tokenAtOrBeforeBarrierIsRejected() {
        Map<String, Object> claims = access(RESOURCE, "projects.read", "projects.member");
        long iat = ((Long) claims.get("iat")).longValue();
        stub.barrier(iat);
        assertThrows(TokenVerifier.TokenRejectedException.class, new Verify(claims));
        stub.barrier(iat - 1L);
        verifier.verifyAccessToken(idp.sign("at+jwt", claims));
    }

    @Test
    void revokedJtiIsRejected() {
        Map<String, Object> claims = access(RESOURCE, "projects.read", "projects.member");
        String jti = (String) claims.get("jti");
        store.addDeniedJti(jti, System.currentTimeMillis() / 1000L - 5L, 0L);
        assertThrows(TokenVerifier.TokenRejectedException.class, new Verify(claims));
    }

    @Test
    void malformedTokenIsRejected() {
        assertThrows(TokenVerifier.TokenRejectedException.class, new VerifyRaw("not-a-token"));
    }

    private Map<String, Object> access(String audience, String scope, String permissions) {
        long now = System.currentTimeMillis() / 1000L;
        Map<String, Object> claims = new HashMap<String, Object>();
        claims.put("iss", idp.issuer());
        claims.put("aud", audience);
        claims.put("sub", "basis-sub-1");
        claims.put("client_id", CLIENT_ID);
        claims.put("scope", scope);
        claims.put("permissions", permissions);
        claims.put("gty", "authorization_code");
        claims.put("jti", "resource-jti-" + sequence++);
        claims.put("iat", Long.valueOf(now));
        claims.put("exp", Long.valueOf(now + 3600L));
        return claims;
    }

    private final class Verify implements Executable {
        private final Map<String, Object> claims;

        private Verify(Map<String, Object> claims) {
            this.claims = claims;
        }

        @Override
        public void execute() {
            verifier.verifyAccessToken(idp.sign("at+jwt", claims));
        }
    }

    private final class VerifyRaw implements Executable {
        private final String token;

        private VerifyRaw(String token) {
            this.token = token;
        }

        @Override
        public void execute() {
            verifier.verifyAccessToken(token);
        }
    }
}
