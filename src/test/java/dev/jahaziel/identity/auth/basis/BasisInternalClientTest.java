/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.basis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.net.http.HttpClient;
import java.time.Duration;

final class BasisInternalClientTest {
    private static final String SECRET = "resource-secret-1234567890";
    private static final String AUDIENCE = "urn:example:api";

    private BasisInternalStub stub;
    private BasisInternalClient client;

    @BeforeEach
    void setUp() throws Exception {
        stub = BasisInternalStub.start();
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        client = new BasisInternalClient(stub.url(), AUDIENCE, SECRET, http);
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    @Test
    void validSubjectSendsResourceCredentialsAndEncodedSubject() {
        client.verify("basis sub/1", 1000L);

        assertEquals("Bearer " + SECRET, stub.authorization());
        assertEquals(BasisHttp.encode(AUDIENCE), stub.resource());
        assertEquals("basis+sub%2F1", stub.subject());
    }

    @Test
    void disabledUserIsRejected() {
        stub.disabled(true);
        assertThrows(BasisException.class, new Verify("basis-sub-1", 1000L));
    }

    @Test
    void tokenAtOrBeforeBarrierIsRejected() {
        stub.barrier(1000L);
        assertThrows(BasisException.class, new Verify("basis-sub-1", 1000L));
        assertThrows(BasisException.class, new Verify("basis-sub-1", 999L));
        client.verify("basis-sub-1", 1001L);
    }

    @Test
    void nonSuccessAndMalformedResponsesFailClosed() {
        stub.status(500);
        assertThrows(BasisException.class, new Verify("basis-sub-1", 1000L));
        stub.status(200);
        stub.malformed(true);
        assertThrows(BasisException.class, new Verify("basis-sub-1", 1000L));
    }

    @Test
    void missingConfigurationFailsClosed() {
        HttpClient http = HttpClient.newHttpClient();
        BasisInternalClient missing = new BasisInternalClient(null, AUDIENCE, null, http);
        assertThrows(BasisException.class, new MissingVerify(missing));
    }

    private final class Verify implements Executable {
        private final String subject;
        private final long issuedAt;

        private Verify(String subject, long issuedAt) {
            this.subject = subject;
            this.issuedAt = issuedAt;
        }

        @Override
        public void execute() {
            client.verify(subject, issuedAt);
        }
    }

    private static final class MissingVerify implements Executable {
        private final BasisInternalClient target;

        private MissingVerify(BasisInternalClient target) {
            this.target = target;
        }

        @Override
        public void execute() {
            target.verify("basis-sub-1", 1000L);
        }
    }
}
