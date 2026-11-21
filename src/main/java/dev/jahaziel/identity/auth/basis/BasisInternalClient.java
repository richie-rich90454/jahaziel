/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.basis;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Live user-state check against the trusted basis-auth internal API. Fails closed on any doubt. */
public final class BasisInternalClient {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final String internalUrl;
    private final String audience;
    private final String resourceSecret;
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public BasisInternalClient(BasisConfig config, HttpClient client) {
        this(config.internalUrl(), config.resource(), config.resourceSecret(), client);
    }

    public BasisInternalClient(
            String internalUrl, String audience, String resourceSecret, HttpClient client) {
        this.internalUrl = internalUrl;
        this.audience = audience;
        this.resourceSecret = resourceSecret;
        this.client = client;
    }

    /** Rejects disabled users and tokens issued at or before the user's validity barrier. */
    public void verify(String subject, long issuedAtSeconds) {
        if (subject == null || subject.length() == 0) {
            throw new BasisException("basis-auth internal lookup lacks a subject");
        }
        if (internalUrl == null
                || internalUrl.length() == 0
                || resourceSecret == null
                || resourceSecret.length() == 0) {
            throw new BasisException("basis-auth internal API is not configured");
        }
        String url = internalUrl + "/internal/users/" + BasisHttp.encode(subject);
        String body;
        try {
            HttpRequest request =
                    HttpRequest.newBuilder(URI.create(url))
                            .timeout(TIMEOUT)
                            .header("Authorization", "Bearer " + resourceSecret)
                            .header("X-Basis-Resource", BasisHttp.encode(audience))
                            .GET()
                            .build();
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new BasisException(
                        "basis-auth internal user lookup failed with HTTP " + status);
            }
            body = response.body();
        } catch (IllegalArgumentException failure) {
            throw new BasisException("basis-auth internal user URL is invalid", failure);
        } catch (IOException failure) {
            throw new BasisException("basis-auth internal user lookup could not complete", failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new BasisException("basis-auth internal user lookup was interrupted", failure);
        }
        JsonNode node;
        try {
            node = mapper.readTree(body);
        } catch (JacksonException failure) {
            throw new BasisException("basis-auth internal user response is not JSON", failure);
        }
        if (node == null || !node.isObject()) {
            throw new BasisException("basis-auth internal user response is not an object");
        }
        JsonNode disabled = node.get("disabled");
        JsonNode barrier = node.get("tokensValidAfter");
        if (disabled == null || disabled.isNull() || !disabled.isBoolean()) {
            throw new BasisException("basis-auth internal user response lacks disabled");
        }
        if (barrier == null || barrier.isNull() || !barrier.isNumber()) {
            throw new BasisException("basis-auth internal user response lacks tokensValidAfter");
        }
        if (disabled.asBoolean()) {
            throw new BasisException("basis-auth user is disabled");
        }
        if (issuedAtSeconds <= barrier.asLong()) {
            throw new BasisException("basis-auth token predates the user validity barrier");
        }
    }
}
