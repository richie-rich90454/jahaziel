/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.internal.devconnect;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DevConnect (basis-auth) settings, bound from the {@code BASIS_*} environment variables. Login
 * stays disabled until all five required values are present.
 */
@ConfigurationProperties(prefix = "basis")
public class DevConnectProperties {
  /** BASIS_AUTH_ISSUER: the IdP issuer URL. */
  private String authIssuer;

  /** BASIS_REDIRECT_URI: the registered callback URL, matched byte for byte. */
  private String redirectUri;

  /** BASIS_CLIENT_ID: the confidential client identifier. */
  private String clientId;

  /** BASIS_CLIENT_SECRET: the confidential client secret. */
  private String clientSecret;

  /** BASIS_RESOURCE: the API audience sent as the RFC 8707 resource parameter. */
  private String resource;

  /** How long a browser session lasts. */
  private Duration sessionTtl = Duration.ofDays(30);

  /** How long the script/API token cookie lasts. */
  private Duration tokenTtl = Duration.ofHours(1);

  /** How long a half-finished login may be resumed. */
  private Duration pendingTtl = Duration.ofMinutes(10);

  /** Required: the IdP issuer URL. */
  public String getAuthIssuer() {
    return authIssuer;
  }

  /** Sets the IdP issuer URL. */
  public void setAuthIssuer(String authIssuer) {
    this.authIssuer = authIssuer;
  }

  /** Required: the registered callback URL. */
  public String getRedirectUri() {
    return redirectUri;
  }

  /** Sets the registered callback URL. */
  public void setRedirectUri(String redirectUri) {
    this.redirectUri = redirectUri;
  }

  /** Required: the confidential client identifier. */
  public String getClientId() {
    return clientId;
  }

  /** Sets the confidential client identifier. */
  public void setClientId(String clientId) {
    this.clientId = clientId;
  }

  /** Required: the confidential client secret. */
  public String getClientSecret() {
    return clientSecret;
  }

  /** Sets the confidential client secret. */
  public void setClientSecret(String clientSecret) {
    this.clientSecret = clientSecret;
  }

  /** Required: the API audience. */
  public String getResource() {
    return resource;
  }

  /** Sets the API audience. */
  public void setResource(String resource) {
    this.resource = resource;
  }

  /** Returns the browser session lifetime. */
  public Duration getSessionTtl() {
    return sessionTtl;
  }

  /** Sets the browser session lifetime. */
  public void setSessionTtl(Duration sessionTtl) {
    this.sessionTtl = sessionTtl;
  }

  /** Returns the API token lifetime. */
  public Duration getTokenTtl() {
    return tokenTtl;
  }

  /** Sets the API token lifetime. */
  public void setTokenTtl(Duration tokenTtl) {
    this.tokenTtl = tokenTtl;
  }

  /** Returns the pending-login lifetime. */
  public Duration getPendingTtl() {
    return pendingTtl;
  }

  /** Sets the pending-login lifetime. */
  public void setPendingTtl(Duration pendingTtl) {
    this.pendingTtl = pendingTtl;
  }

  /**
   * Checks whether login can run.
   *
   * @return true when every required BASIS_* value is present
   */
  public boolean configured() {
    return filled(authIssuer)
        && filled(redirectUri)
        && filled(clientId)
        && filled(clientSecret)
        && filled(resource);
  }

  /**
   * Returns the issuer without a trailing slash.
   *
   * @return the base issuer URL
   */
  public String issuerBase() {
    String value = authIssuer == null ? "" : authIssuer.trim();
    while (value.endsWith("/")) {
      value = value.substring(0, value.length() - 1);
    }
    return value;
  }

  private static boolean filled(String value) {
    return value != null && !value.isBlank();
  }
}
