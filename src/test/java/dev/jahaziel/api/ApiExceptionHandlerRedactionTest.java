/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.jahaziel.api.controller.ApiExceptionHandler;
import dev.jahaziel.api.dto.ApiError;
import dev.jahaziel.identity.auth.basis.BasisOAuthException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Callers see fixed messages plus a correlation id; raw details stay in the server log. */
class ApiExceptionHandlerRedactionTest {
    @Test
    void badInputDoesNotEchoTheRawMessageOrIdpUrl() {
        ApiError error =
                new ApiExceptionHandler()
                        .badInput(
                                new IllegalArgumentException(
                                        "unknown issuer https://idp.example.com/authorize"
                                                + " token=abc123"));

        assertEquals("VALIDATION_FAILED", error.code());
        assertEquals("The request contains an invalid value", error.message());
        assertFalse(error.message().contains("idp.example.com"));
        assertNotNull(error.correlationId());
    }

    @Test
    void basisFailureKeepsDocumentedDetailsWithoutRawUrls() {
        ApiError error =
                new ApiExceptionHandler()
                        .basisOAuthFailed(
                                BasisOAuthException.from(
                                        400, "{\"error\":\"unsupported_response_type\"}"));

        assertEquals("14429", error.code());
        assertEquals("14429", error.details().get("code"));
        assertFalse(error.message().contains("http"));
        assertNotNull(error.correlationId());
    }

    @Test
    void unknownPathResponseNeverCarriesTheQueryString() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/missing");
        request.setQueryString("code=secret&state=abc");

        ApiError error =
                new ApiExceptionHandler()
                        .noResource(
                                new NoResourceFoundException(HttpMethod.GET, "/missing", "gone"),
                                request);

        assertEquals("No such endpoint", error.message());
        assertEquals("gone", error.details().get("path"));
        assertFalse(String.valueOf(error.details()).contains("secret"));
        assertNotNull(error.correlationId());
    }

    @Test
    void everyHandlerIncludesACorrelationId() {
        ApiExceptionHandler handler = new ApiExceptionHandler();
        assertNotNull(
                handler.nodeAgentUnreachable(
                                new dev.jahaziel.runtime.api.NodeAgentUnreachableException("x"))
                        .correlationId());
        assertNotNull(handler.unexpected(new RuntimeException("boom")).correlationId());
    }
}
