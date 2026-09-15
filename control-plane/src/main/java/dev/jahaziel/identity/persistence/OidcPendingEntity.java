/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/** Persistence DTO for the oidc_pending table: PKCE verifier, state, and nonce of one login. */
@Entity
@Table(name = "oidc_pending")
public class OidcPendingEntity {

  /** Opaque login state, the primary key. */
  @Id
  @Column(name = "state")
  private String state;

  /** ID token nonce. */
  @Column(name = "nonce")
  private String nonce;

  /** PKCE code verifier. */
  @Column(name = "code_verifier")
  private String codeVerifier;

  /** Redirect URI used to start login. */
  @Column(name = "redirect_uri")
  private String redirectUri;

  /** Creation timestamp. */
  @Column(name = "created_at")
  private Instant createdAt;

  /** Expiry, after which the login must be restarted. */
  @Column(name = "expires_at")
  private Instant expiresAt;

  /** Creates an empty instance for JPA. */
  public OidcPendingEntity() {}

  /** Gets the login state. */
  public String getState() {
    return state;
  }

  /** Sets the login state. */
  public void setState(String state) {
    this.state = state;
  }

  /** Gets the ID token nonce. */
  public String getNonce() {
    return nonce;
  }

  /** Sets the ID token nonce. */
  public void setNonce(String nonce) {
    this.nonce = nonce;
  }

  /** Gets the PKCE code verifier. */
  public String getCodeVerifier() {
    return codeVerifier;
  }

  /** Sets the PKCE code verifier. */
  public void setCodeVerifier(String codeVerifier) {
    this.codeVerifier = codeVerifier;
  }

  /** Gets the redirect URI. */
  public String getRedirectUri() {
    return redirectUri;
  }

  /** Sets the redirect URI. */
  public void setRedirectUri(String redirectUri) {
    this.redirectUri = redirectUri;
  }

  /** Gets the creation timestamp. */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /** Sets the creation timestamp. */
  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  /** Gets the expiry. */
  public Instant getExpiresAt() {
    return expiresAt;
  }

  /** Sets the expiry. */
  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  /** Compares by class and primary key. */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    OidcPendingEntity that = (OidcPendingEntity) o;
    return Objects.equals(state, that.state);
  }

  /** Returns the hash code derived from the primary key. */
  @Override
  public int hashCode() {
    return Objects.hash(state);
  }
}
