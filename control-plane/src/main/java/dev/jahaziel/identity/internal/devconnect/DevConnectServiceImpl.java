/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.internal.devconnect;

import dev.jahaziel.identity.api.DevConnectException;
import dev.jahaziel.identity.api.DevConnectService;
import dev.jahaziel.identity.api.JwtService;
import dev.jahaziel.identity.api.LoginRejectedException;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.identity.persistence.AppUserEntity;
import dev.jahaziel.identity.persistence.AppUserRepository;
import dev.jahaziel.identity.persistence.BffSessionEntity;
import dev.jahaziel.identity.persistence.BffSessionRepository;
import dev.jahaziel.identity.persistence.OidcPendingEntity;
import dev.jahaziel.identity.persistence.OidcPendingRepository;
import dev.jahaziel.identity.persistence.UserRoleEntity;
import dev.jahaziel.identity.persistence.UserRoleRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Database-backed DevConnect login. Transactional, so Spring proxies it. */
public class DevConnectServiceImpl implements DevConnectService {
  private static final String ROLE_USER = "ROLE_USER";
  private static final String GRANTOR = "devconnect";
  private static final String ACTIVE = "active";

  private final DevConnectProperties props;
  private final DevConnectClient client;
  private final OidcPendingRepository pending;
  private final BffSessionRepository sessions;
  private final AppUserRepository users;
  private final UserRoleRepository roles;
  private final JwtService tokens;
  private final Clock clock;

  /**
   * Creates the service.
   *
   * @param props the DevConnect settings
   * @param client the IdP client
   * @param pending the half-finished login store
   * @param sessions the browser session store
   * @param users the account store
   * @param roles the role-grant store
   * @param tokens the API token issuer
   * @param clock the clock
   */
  public DevConnectServiceImpl(
      DevConnectProperties props,
      DevConnectClient client,
      OidcPendingRepository pending,
      BffSessionRepository sessions,
      AppUserRepository users,
      UserRoleRepository roles,
      JwtService tokens,
      Clock clock) {
    this.props = Objects.requireNonNull(props, "props");
    this.client = Objects.requireNonNull(client, "client");
    this.pending = Objects.requireNonNull(pending, "pending");
    this.sessions = Objects.requireNonNull(sessions, "sessions");
    this.users = Objects.requireNonNull(users, "users");
    this.roles = Objects.requireNonNull(roles, "roles");
    this.tokens = Objects.requireNonNull(tokens, "tokens");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  public boolean enabled() {
    return props.configured();
  }

  @Override
  public Duration sessionTtl() {
    return props.getSessionTtl();
  }

  @Override
  public Duration tokenTtl() {
    return props.getTokenTtl();
  }

  @Override
  @Transactional
  public String startLogin() {
    requireConfigured();
    Instant now = clock.instant();
    pending.deleteByExpiresAtBefore(now);
    OidcPendingEntity row = new OidcPendingEntity();
    row.setState(Pkce.state());
    row.setNonce(Pkce.nonce());
    row.setCodeVerifier(Pkce.verifier());
    row.setRedirectUri(props.getRedirectUri());
    row.setCreatedAt(now);
    row.setExpiresAt(now.plus(props.getPendingTtl()));
    pending.save(row);
    return client.authorizationUrl(
        row.getState(), row.getNonce(), Pkce.challenge(row.getCodeVerifier()));
  }

  @Override
  @Transactional
  public LoginResult completeLogin(String state, String code) {
    requireConfigured();
    if (state == null || state.isBlank() || code == null || code.isBlank()) {
      throw new LoginRejectedException("login state or code is missing");
    }
    OidcPendingEntity row =
        pending
            .findById(state)
            .orElseThrow(
                () -> new LoginRejectedException("login state is unknown or already used"));
    pending.delete(row);
    if (row.getExpiresAt().isBefore(clock.instant())) {
      throw new LoginRejectedException("login state has expired; start again");
    }

    DevConnectClient.Tokens exchanged =
        client.exchange(code, row.getCodeVerifier(), row.getRedirectUri());
    var idToken = client.verifyIdToken(exchanged.idToken(), row.getNonce());
    DevConnectClient.UserInfo info = client.userinfo(exchanged.accessToken());
    String subject = idToken.getSubject();
    if (subject == null || !subject.equals(info.sub())) {
      throw new LoginRejectedException("identity mismatch between ID token and UserInfo");
    }

    AppUserEntity user = provision(info.sub(), info.email(), info.name());
    Instant now = clock.instant();
    BffSessionEntity session = new BffSessionEntity();
    session.setSessionId(Pkce.state());
    session.setUserId(user.getUserId());
    session.setCreatedAt(now);
    session.setExpiresAt(now.plus(props.getSessionTtl()));
    sessions.save(session);
    sessions.deleteByExpiresAtBefore(now);

    String apiToken =
        tokens.issue(new UserId(user.getUserId()), null, List.of(), props.getTokenTtl());
    return new LoginResult(user.getUserId(), session.getSessionId(), apiToken);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UserId> activeUser(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      return Optional.empty();
    }
    Optional<BffSessionEntity> found = sessions.findById(sessionId);
    if (found.isEmpty() || found.get().getExpiresAt().isBefore(clock.instant())) {
      return Optional.empty();
    }
    UUID userId = found.get().getUserId();
    return users
        .findById(userId)
        .filter(user -> ACTIVE.equals(user.getStatus()))
        .map(user -> new UserId(userId));
  }

  @Override
  @Transactional
  public void logout(String sessionId) {
    if (sessionId != null && !sessionId.isBlank()) {
      sessions.findById(sessionId).ifPresent(sessions::delete);
    }
  }

  private void requireConfigured() {
    if (!props.configured()) {
      throw new DevConnectException(
          "DevConnect is not configured: set BASIS_AUTH_ISSUER, BASIS_REDIRECT_URI,"
              + " BASIS_CLIENT_ID, BASIS_CLIENT_SECRET, and BASIS_RESOURCE");
    }
  }

  private AppUserEntity provision(String subject, String email, String displayName) {
    Optional<AppUserEntity> linked = users.findByExternalId(subject);
    if (linked.isPresent()) {
      AppUserEntity user = linked.get();
      requireActive(user);
      return touch(user, email, displayName);
    }
    List<AppUserEntity> byEmail = users.findByEmailIgnoreCase(email);
    if (byEmail.size() > 1) {
      throw new LoginRejectedException(
          "email " + email + " matches multiple accounts; an operator must resolve it");
    }
    if (byEmail.size() == 1) {
      AppUserEntity user = byEmail.get(0);
      if (user.getExternalId() != null
          && !user.getExternalId().isBlank()
          && !subject.equals(user.getExternalId())) {
        throw new LoginRejectedException(
            "email "
                + email
                + " is already linked to a different identity; an operator must resolve it");
      }
      requireActive(user);
      user.setExternalId(subject);
      return touch(user, email, displayName);
    }
    return create(subject, email, displayName);
  }

  private AppUserEntity create(String subject, String email, String displayName) {
    Instant now = clock.instant();
    AppUserEntity user = new AppUserEntity();
    user.setUserId(UUID.randomUUID());
    user.setExternalId(subject);
    user.setEmail(email);
    user.setDisplayName(displayName);
    user.setCreatedAt(now);
    user.setLastLoginAt(now);
    user.setStatus(ACTIVE);
    user.setMfaEnabled(false);
    AppUserEntity saved = users.save(user);

    UserRoleEntity role = new UserRoleEntity();
    role.setId(new UserRoleEntity.Id(saved.getUserId(), ROLE_USER));
    role.setGrantedAt(now);
    role.setGrantedBy(GRANTOR);
    roles.save(role);
    return saved;
  }

  private AppUserEntity touch(AppUserEntity user, String email, String displayName) {
    user.setEmail(email);
    user.setDisplayName(displayName);
    user.setLastLoginAt(clock.instant());
    return users.save(user);
  }

  private static void requireActive(AppUserEntity user) {
    if (!ACTIVE.equals(user.getStatus())) {
      throw new LoginRejectedException(
          "account is " + user.getStatus() + "; an operator must re-enable it");
    }
  }
}
