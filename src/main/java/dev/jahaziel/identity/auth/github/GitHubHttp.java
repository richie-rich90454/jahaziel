/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.github;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Bounded HTTP calls; GitHub returns JSON only when Accept asks for it. */
public final class GitHubHttp {
    private final HttpClient client;

    public GitHubHttp(HttpClient client) {
        this.client = client;
    }

    public Response postForm(String url, String body, Duration timeout) {
        HttpRequest request =
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(timeout)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();
        return send(request, url);
    }

    public Response getJson(String url, String bearerToken, Duration timeout) {
        HttpRequest request =
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(timeout)
                        .header("Accept", "application/json")
                        .header("Authorization", "Bearer " + bearerToken)
                        .GET()
                        .build();
        return send(request, url);
    }

    private Response send(HttpRequest request, String url) {
        try {
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new GitHubException(
                        "github request failed with HTTP " + status + " at " + url);
            }
            Map<String, String> headers = new HashMap<String, String>();
            Map<String, List<String>> raw = response.headers().map();
            for (String name : raw.keySet()) {
                List<String> values = raw.get(name);
                if (values.size() > 0) {
                    headers.put(name.toLowerCase(Locale.ROOT), values.get(0));
                }
            }
            return new Response(response.body(), headers);
        } catch (IOException failure) {
            throw new GitHubException("github request could not reach " + url, failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new GitHubException("github request was interrupted", failure);
        }
    }

    /** One HTTP response: body plus headers keyed by lower-case names. */
    public static final class Response {
        private final String body;
        private final Map<String, String> headers;

        private Response(String body, Map<String, String> headers) {
            this.body = body;
            this.headers = headers;
        }

        public String body() {
            return body;
        }

        public String header(String name) {
            return name == null ? null : headers.get(name.toLowerCase(Locale.ROOT));
        }
    }
}
