/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.internal.devconnect;

import dev.jahaziel.identity.api.DevConnectException;
import dev.jahaziel.identity.api.DevConnectService;
import dev.jahaziel.identity.api.JwtService;
import dev.jahaziel.identity.api.LoginRejectedException;
import dev.jahaziel.identity.auth.AuthIdentity;
import dev.jahaziel.identity.auth.wiring.AuthProviderDispatcher;
import dev.jahaziel.identity.auth.wiring.UserIdentityStore;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
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
  private final AuthProviderDispatcher dispatcher;
  private final UserIdentityStore identities;

  /**
   * Creates the service without plugin dispatch, preserving the DevConnect-only entry point.
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
    this(props, client, pending, sessions, users, roles, tokens, clock, null, null);
  }

  /**
   * Creates the service with plugin dispatch. A null dispatcher selects the DevConnect flow.
   *
   * @param props the DevConnect settings
   * @param client the IdP client
   * @param pending the half-finished login store
   * @param sessions the browser session store
   * @param users the account store
   * @param roles the role-grant store
   * @param tokens the API token issuer
   * @param clock the clock
   * @param dispatcher the provider dispatcher, or null
   * @param identities the identity link store, or null
   */
  public DevConnectServiceImpl(
      DevConnectProperties props,
      DevConnectClient client,
      OidcPendingRepository pending,
      BffSessionRepository sessions,
      AppUserRepository users,
      UserRoleRepository roles,
      JwtService tokens,
      Clock clock,
      AuthProviderDispatcher dispatcher,
      UserIdentityStore identities) {
    this.props = Objects.requireNonNull(props, "props");
    this.client = Objects.requireNonNull(client, "client");
    this.pending = Objects.requireNonNull(pending, "pending");
    this.sessions = Objects.requireNonNull(sessions, "sessions");
    this.users = Objects.requireNonNull(users, "users");
    this.roles = Objects.requireNonNull(roles, "roles");
    this.tokens = Objects.requireNonNull(tokens, "tokens");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.dispatcher = dispatcher;
    this.identities = identities;
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
    if (dispatcher != null && dispatcher.handles()) {
      String url = dispatcher.startLogin(Pkce.state(), Pkce.nonce(), props.getRedirectUri());
      if (url != null && !url.isBlank()) {
        return url;
      }
    }
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
    if (dispatcher != null && dispatcher.handles()) {
      Map<String, String> parameters = new HashMap<String, String>();
      parameters.put("state", state);
      parameters.put("code", code);
      AuthIdentity identity = dispatcher.handleCallback(parameters);
      if (identity != null) {
        return completePluginLogin(identity);
      }
    }
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
    applyAdminBootstrap(user, info.emailVerified(), idToken);
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
    if (found.isPresent() && !found.get().getExpiresAt().isBefore(clock.instant())) {
      UUID userId = found.get().getUserId();
      Optional<AppUserEntity> user = users.findById(userId);
      if (user.isPresent() && ACTIVE.equals(user.get().getStatus())) {
        return Optional.of(new UserId(userId));
      }
    }
    if (dispatcher != null && dispatcher.handles() && identities != null) {
      AuthIdentity identity = dispatcher.currentIdentity(sessionId);
      if (identity != null) {
        UUID linked = identities.findUserId(identity.provider(), identity.subject());
        if (linked != null) {
          Optional<AppUserEntity> user = users.findById(linked);
          if (user.isPresent() && ACTIVE.equals(user.get().getStatus())) {
            return Optional.of(new UserId(linked));
          }
        }
      }
    }
    return Optional.empty();
  }

  @Override
  @Transactional
  public void logout(String sessionId) {
    if (dispatcher != null) {
      dispatcher.endSession(sessionId);
    }
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

  private LoginResult completePluginLogin(AuthIdentity identity) {
    if (identities == null) {
      throw new LoginRejectedException("identity linking is unavailable");
    }
    AppUserEntity user = provisionLinked(identity);
    applyPluginAdminBootstrap(user, identity);
    Instant now = clock.instant();
    String sessionId = identity.claims().get("session_id");
    if (sessionId == null || sessionId.isBlank()) {
      sessionId = Pkce.state();
    }
    BffSessionEntity session = new BffSessionEntity();
    session.setSessionId(sessionId);
    session.setUserId(user.getUserId());
    session.setCreatedAt(now);
    session.setExpiresAt(now.plus(props.getSessionTtl()));
    sessions.save(session);
    sessions.deleteByExpiresAtBefore(now);
    String apiToken =
        tokens.issue(new UserId(user.getUserId()), null, Collections.emptyList(), props.getTokenTtl());
    return new LoginResult(user.getUserId(), sessionId, apiToken);
  }

  private AppUserEntity provisionLinked(AuthIdentity identity) {
    UUID linked = identities.findUserId(identity.provider(), identity.subject());
    if (linked != null) {
      AppUserEntity user = users.findById(linked).orElse(null);
      if (user == null) {
        throw new LoginRejectedException("linked account is missing");
      }
      requireActive(user);
      return touch(user, identity.email(), identity.displayName());
    }
    // No (provider, subject) link exists. Never join on email: create a fresh local account.
    Instant now = clock.instant();
    AppUserEntity user = new AppUserEntity();
    user.setUserId(UUID.randomUUID());
    user.setExternalId(identity.linkKey());
    user.setEmail(identity.email());
    user.setDisplayName(identity.displayName());
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
    identities.link(
        identity.provider(),
        identity.subject(),
        saved.getUserId(),
        identity.email(),
        identity.displayName(),
        now);
    return saved;
  }

  private void applyPluginAdminBootstrap(AppUserEntity user, AuthIdentity identity) {
    String bootstrapEmail = props.getAdminBootstrapEmail();
    if (bootstrapEmail == null || bootstrapEmail.isBlank()) return;
    if (!identity.emailVerified()) return;
    if (!bootstrapEmail.trim().equalsIgnoreCase(user.getEmail())) return;
    Instant now = clock.instant();
    UserRoleEntity existing = null;
    try {
      existing = roles.findById(new UserRoleEntity.Id(user.getUserId(), "ROLE_ADMIN")).orElse(null);
    } catch (RuntimeException ignored) {
      existing = null;
    }
    if (existing == null) {
      UserRoleEntity role = new UserRoleEntity();
      role.setId(new UserRoleEntity.Id(user.getUserId(), "ROLE_ADMIN"));
      role.setGrantedAt(now);
      role.setGrantedBy("admin-bootstrap");
      roles.save(role);
    }
    boolean mfaAssured = identity.mfaAssured() || localTotpAssured(identity);
    if (mfaAssured != user.isMfaEnabled()) {
      user.setMfaEnabled(mfaAssured);
      users.save(user);
    }
  }

  private static boolean localTotpAssured(AuthIdentity identity) {
    if (!"local".equals(identity.provider())) {
      return false;
    }
    Map<String, String> claims = identity.claims();
    return "complete".equals(claims.get("mfa_enrollment"))
        || Boolean.parseBoolean(claims.get("totp"));
  }

  private void applyAdminBootstrap(AppUserEntity user, boolean emailVerified, Jwt idToken) {
    String bootstrapEmail = props.getAdminBootstrapEmail();
    if (bootstrapEmail == null || bootstrapEmail.isBlank()) return;
    if (!emailVerified) return;
    if (!bootstrapEmail.trim().equalsIgnoreCase(user.getEmail())) return;
    Instant now = clock.instant();
    UserRoleEntity existing = null;
    try {
      existing = roles.findById(new UserRoleEntity.Id(user.getUserId(), "ROLE_ADMIN")).orElse(null);
    } catch (RuntimeException ignored) {
      existing = null;
    }
    if (existing == null) {
      UserRoleEntity role = new UserRoleEntity();
      role.setId(new UserRoleEntity.Id(user.getUserId(), "ROLE_ADMIN"));
      role.setGrantedAt(now);
      role.setGrantedBy("admin-bootstrap");
      roles.save(role);
    }
    boolean mfaAssured = isMfaAssured(idToken);
    if (mfaAssured != user.isMfaEnabled()) {
      user.setMfaEnabled(mfaAssured);
      users.save(user);
    }
  }

  private boolean isMfaAssured(Jwt idToken) {
    if (idToken == null) return false;
    String requiredAcr = props.getAdminRequiredAcr();
    if (requiredAcr != null && !requiredAcr.isBlank()) {
      String acr = idToken.getClaimAsString("acr");
      if (requiredAcr.trim().equalsIgnoreCase(acr)) return true;
    }
    Object amrClaim = idToken.getClaims().get("amr");
    List<String> amr = new java.util.ArrayList<>();
    if (amrClaim instanceof List<?> list) {
      for (Object value : list) if (value != null) amr.add(String.valueOf(value));
    } else if (amrClaim instanceof String text && !text.isBlank()) {
      for (String part : text.split("[\\s,]+")) if (!part.isBlank()) amr.add(part);
    }
    for (String value : amr) {
      String normalized = value.toLowerCase(java.util.Locale.ROOT);
      if (normalized.equals("mfa")
          || normalized.equals("otp")
          || normalized.equals("totp")
          || normalized.contains("mfa")) {
        return true;
      }
    }
    // Without a configured ACR and no MFA AMR, do not assert MFA assurance.
    return false;
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
