/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.basis;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/** Bounded HTTP calls. Response bodies are parsed by callers; failures never log payloads. */
public final class BasisHttp {
    private final HttpClient client;

    public BasisHttp(HttpClient client) {
        this.client = client;
    }

    public static String form(Map<String, String> values) {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (body.length() > 0) {
                body.append('&');
            }
            body.append(encode(entry.getKey()));
            body.append('=');
            body.append(encode(entry.getValue() == null ? "" : entry.getValue()));
        }
        return body.toString();
    }

    public static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public String get(String url, Duration timeout) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .GET()
                .build();
        return send(request, url);
    }

    public String postForm(String url, String body, String authorization, Duration timeout) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        return send(builder.build(), url);
    }

    public String postJson(String url, String body, Map<String, String> headers, Duration timeout) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }
        return send(builder.build(), url);
    }

    private String send(HttpRequest request, String url) {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new BasisException("basis-auth request failed with HTTP " + status + " at " + url);
            }
            return response.body();
        } catch (IOException failure) {
            throw new BasisException("basis-auth request could not reach " + url, failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new BasisException("basis-auth request was interrupted", failure);
        }
    }
}
