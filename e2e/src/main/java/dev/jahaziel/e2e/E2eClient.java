/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.e2e;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

/**
 * Minimal REST client for acceptance. A user goes from login to workspace to IDE to terminal to an
 * open port in the browser.
 */
public final class E2eClient {
  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper mapper = new ObjectMapper();
  private final String baseUrl;
  private final String token;

  /**
   * Creates the client.
   *
   * @param baseUrl the platform base URL
   * @param token the bearer token minted out of band
   */
  public E2eClient(String baseUrl, String token) {
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("baseUrl must not be blank");
    }
    if (token == null || token.isBlank()) {
      throw new IllegalArgumentException("token must not be blank");
    }
    this.baseUrl = baseUrl;
    this.token = token;
  }

  /**
   * Reads the current user.
   *
   * @return the response body
   */
  public String me() {
    return get("/api/v1/me");
  }

  /**
   * Creates a workspace.
   *
   * @param name the workspace name
   * @return the workspace id from the response
   */
  public String createWorkspace(String name) {
    String body = post("/api/v1/workspaces", Map.of("name", name));
    return field(body, "workspaceId");
  }

  /**
   * Reads one workspace.
   *
   * @param workspaceId the workspace id
   * @return the response body
   */
  public String workspace(String workspaceId) {
    return get("/api/v1/workspaces/" + workspaceId);
  }

  /**
   * Lists the user's workspaces.
   *
   * @return the response body
   */
  public String myWorkspaces() {
    return get("/api/v1/me/workspaces");
  }

  /**
   * Lists a workspace's ports.
   *
   * @param workspaceId the workspace id
   * @return the response body
   */
  public String ports(String workspaceId) {
    return get("/api/v1/workspaces/" + workspaceId + "/ports");
  }

  /**
   * Deletes a workspace.
   *
   * @param workspaceId the workspace id
   */
  public void deleteWorkspace(String workspaceId) {
    delete("/api/v1/workspaces/" + workspaceId + "?confirm=true");
  }

  private String get(String path) {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer " + token)
            .GET()
            .build();
    return send(request, 200);
  }

  private String post(String path, Map<String, String> json) {
    String body;
    try {
      body = mapper.writeValueAsString(json);
    } catch (tools.jackson.core.JacksonException failure) {
      throw new IllegalArgumentException("cannot encode body", failure);
    }
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    return send(request, 201);
  }

  private void delete(String path) {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer " + token)
            .DELETE()
            .build();
    send(request, 204);
  }

  private String send(HttpRequest request, int expected) {
    try {
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != expected) {
        throw new IllegalStateException(
            request.method()
                + " "
                + request.uri()
                + " returned "
                + response.statusCode()
                + ": "
                + response.body());
      }
      return response.body();
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("request failed: " + request.uri(), failure);
    } catch (IOException failure) {
      throw new IllegalStateException("request failed: " + request.uri(), failure);
    }
  }

  private String field(String body, String name) {
    try {
      var node = mapper.readTree(body).get(name);
      if (node == null) {
        throw new IllegalArgumentException("response has no field " + name + ": " + body);
      }
      return node.asString();
    } catch (tools.jackson.core.JacksonException failure) {
      throw new IllegalArgumentException("response has no field " + name + ": " + body, failure);
    }
  }
}
