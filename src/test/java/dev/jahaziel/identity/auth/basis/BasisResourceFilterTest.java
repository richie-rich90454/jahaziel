/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.basis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

final class BasisResourceFilterTest {
    private static final String CLIENT_ID = "example-web";
    private static final String RESOURCE = "urn:example:api";
    private static final String RESOURCE_SECRET = "resource-secret-1234567890";
    private static final String PATH = "/api/v1/workspaces/123/files";

    @TempDir Path temp;

    private LoopbackIdp idp;
    private BasisInternalStub stub;
    private BasisStore store;
    private BasisResourceFilter filter;
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
        BasisConfig config = new BasisConfig(new dev.jahaziel.plugin.PluginContext(temp, settings));
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        BasisHttp http = new BasisHttp(client);
        store = new BasisStore(config);
        BasisInternalClient subjects =
                new BasisInternalClient(stub.url(), RESOURCE, RESOURCE_SECRET, http, client);
        TokenVerifier verifier =
                new TokenVerifier(config, new BasisMetadata(config, http), store, subjects);
        filter =
                new BasisResourceFilter(
                        verifier,
                        new BasisScopeGuard(),
                        new String[] {"projects.read"},
                        new String[] {"projects.member"});
    }

    @AfterEach
    void tearDown() {
        stub.close();
        idp.close();
    }

    @Test
    void validTokenReachesTheChain() throws Exception {
        MockHttpServletRequest request = request(bearer(validToken()));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
        assertNull(response.getHeader("WWW-Authenticate"));
    }

    @Test
    void wrongAudienceGetsStandardInvalidToken() throws Exception {
        MockHttpServletResponse response = call(PATH, bearer(token("urn:wrong:api")));
        assertInvalidToken(response);
    }

    @Test
    void missingRequiredScopeGets403() throws Exception {
        MockHttpServletResponse response =
                call(PATH, bearer(token(RESOURCE, "projects.write", "projects.member")));
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("insufficient_scope"));
    }

    @Test
    void missingRequiredPermissionGets403() throws Exception {
        MockHttpServletResponse response =
                call(PATH, bearer(token(RESOURCE, "projects.read", "other.permission")));
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("insufficient_permissions"));
    }

    @Test
    void disabledUserAndBarrierGetStandard401() throws Exception {
        stub.disabled(true);
        assertInvalidToken(call(PATH, bearer(validToken())));
        stub.disabled(false);
        Map<String, Object> claims = claims(RESOURCE);
        stub.barrier(((Long) claims.get("iat")).longValue());
        assertInvalidToken(call(PATH, bearer(idp.sign("at+jwt", claims))));
    }

    @Test
    void revokedJtiGetsStandard401() throws Exception {
        Map<String, Object> claims = claims(RESOURCE);
        store.addDeniedJti((String) claims.get("jti"), System.currentTimeMillis() / 1000L - 5L, 0L);
        assertInvalidToken(call(PATH, bearer(idp.sign("at+jwt", claims))));
    }

    @Test
    void malformedAndExpiredTokensGetStandard401() throws Exception {
        String valid = validToken();
        String last = valid.substring(valid.length() - 1);
        String tampered = valid.substring(0, valid.length() - 1) + ("A".equals(last) ? "B" : "A");
        assertInvalidToken(call(PATH, bearer(tampered)));

        Map<String, Object> expired = claims(RESOURCE);
        expired.put("exp", Long.valueOf(System.currentTimeMillis() / 1000L - 120L));
        assertInvalidToken(call(PATH, bearer(idp.sign("at+jwt", expired))));
    }

    @Test
    void nonBasisBearerAndOtherPathsPassThrough() throws Exception {
        assertNull(call(PATH, "Bearer app-session-token").getHeader("WWW-Authenticate"));
        assertNull(call("/api/v1/other", bearer(validToken())).getHeader("WWW-Authenticate"));
        assertNull(call(PATH, null).getHeader("WWW-Authenticate"));
    }

    private MockHttpServletRequest request(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", PATH);
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return request;
    }

    private MockHttpServletResponse call(String path, String authorization) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private String validToken() {
        return idp.sign("at+jwt", claims(RESOURCE));
    }

    private String token(String audience) {
        return idp.sign("at+jwt", claims(audience));
    }

    private String token(String audience, String scope, String permissions) {
        Map<String, Object> claims = claims(audience);
        claims.put("scope", scope);
        claims.put("permissions", permissions);
        return idp.sign("at+jwt", claims);
    }

    private Map<String, Object> claims(String audience) {
        long now = System.currentTimeMillis() / 1000L;
        Map<String, Object> claims = new HashMap<String, Object>();
        claims.put("iss", idp.issuer());
        claims.put("aud", audience);
        claims.put("sub", "basis-sub-1");
        claims.put("client_id", CLIENT_ID);
        claims.put("scope", "projects.read");
        claims.put("permissions", "projects.member");
        claims.put("gty", "authorization_code");
        claims.put("jti", "filter-jti-" + sequence++);
        claims.put("iat", Long.valueOf(now));
        claims.put("exp", Long.valueOf(now + 3600L));
        return claims;
    }

    private static void assertInvalidToken(MockHttpServletResponse response) throws Exception {
        assertEquals(401, response.getStatus());
        assertEquals("Bearer error=\"invalid_token\"", response.getHeader("WWW-Authenticate"));
        assertTrue(response.getContentAsString().contains("invalid_token"));
    }
}
