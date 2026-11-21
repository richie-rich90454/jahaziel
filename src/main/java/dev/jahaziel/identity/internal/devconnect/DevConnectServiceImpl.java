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
import dev.jahaziel.identity.auth.AuthProviderPlugin;
import dev.jahaziel.identity.auth.BasisAuthPlugin;
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
import dev.jahaziel.plugin.PluginContext;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;

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
import java.util.concurrent.ConcurrentHashMap;

/** Database-backed DevConnect login. Transactional, so Spring proxies it. */
public class DevConnectServiceImpl extends DevConnectService {
    private static final Duration DEFAULT_PLUGIN_TTL = Duration.ofMinutes(15L);
    private static final String ROLE_USER = "ROLE_USER";
    private static final String GRANTOR = "devconnect";
    private static final String ACTIVE = "active";
    private static final String PROVIDER_BASIS = "basis-auth";

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
    private final Duration pluginIdentityTtl;
    private final Map<String, TimedIdentity> pluginIdentities =
            new ConcurrentHashMap<String, TimedIdentity>();

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
     * Creates the service with plugin dispatch and a configured plugin identity lifetime.
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
     * @param pluginIdentityTtl how long an unexpiring plugin identity may back a browser session
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
            UserIdentityStore identities,
            Duration pluginIdentityTtl) {
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
        this.pluginIdentityTtl = Objects.requireNonNull(pluginIdentityTtl, "pluginIdentityTtl");
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
        this.pluginIdentityTtl = DEFAULT_PLUGIN_TTL;
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
        Optional<OidcPendingEntity> pendingRow = pending.findById(state);
        if (!pendingRow.isPresent()) {
            throw new LoginRejectedException("login state is unknown or already used");
        }
        OidcPendingEntity row = pendingRow.get();
        pending.delete(row);
        if (row.getExpiresAt().isBefore(clock.instant())) {
            throw new LoginRejectedException("login state has expired; start again");
        }

        DevConnectClient.Tokens exchanged =
                client.exchange(code, row.getCodeVerifier(), row.getRedirectUri());
        Jwt idToken = client.verifyIdToken(exchanged.idToken(), row.getNonce());
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
        if (found.isPresent()) {
            BffSessionEntity session = found.get();
            if (session.getExpiresAt().isBefore(clock.instant())) {
                return Optional.empty();
            }
            UUID userId = session.getUserId();
            Optional<AppUserEntity> user = users.findById(userId);
            if (user.isPresent() && ACTIVE.equals(user.get().getStatus())) {
                return Optional.of(new UserId(userId));
            }
            return Optional.empty();
        }
        // A missing BFF row is the only case where a provider session may back a browser session.
        AuthIdentity identity = freshPluginIdentity(sessionId);
        if (identity != null && identities != null) {
            UUID linked = identities.findUserId(identity.provider(), identity.subject());
            if (linked != null) {
                Optional<AppUserEntity> user = users.findById(linked);
                if (user.isPresent() && ACTIVE.equals(user.get().getStatus())) {
                    return Optional.of(new UserId(linked));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Plugin stores never expire their sessions, so the first observation starts a bounded window.
     * The window is recorded per session id and never refreshed by later calls.
     */
    private AuthIdentity freshPluginIdentity(String sessionId) {
        if (dispatcher == null || !dispatcher.handles()) {
            return null;
        }
        Instant now = clock.instant();
        TimedIdentity cached = pluginIdentities.get(sessionId);
        if (cached != null) {
            if (cached.expired(now)) {
                pluginIdentities.remove(sessionId);
                return null;
            }
            return cached.identity();
        }
        AuthIdentity identity = dispatcher.currentIdentity(sessionId);
        if (identity == null) {
            return null;
        }
        pluginIdentities.put(sessionId, new TimedIdentity(identity, now, pluginIdentityTtl));
        return identity;
    }

    @Override
    @Transactional
    public void logout(String sessionId) {
        if (sessionId != null) {
            pluginIdentities.remove(sessionId);
        }
        if (dispatcher != null) {
            dispatcher.endSession(sessionId);
        }
        if (sessionId != null && !sessionId.isBlank()) {
            Optional<BffSessionEntity> existing = sessions.findById(sessionId);
            if (existing.isPresent()) {
                sessions.delete(existing.get());
            }
        }
    }

    private void requireConfigured() {
        if (!props.configured()) {
            throw new DevConnectException(
                    "basis-auth is not configured: set BASIS_AUTH_ISSUER,"
                            + " BASIS_AUTH_CLIENT_ID, BASIS_AUTH_CLIENT_SECRET,"
                            + " BASIS_AUTH_RESOURCE, and CURRENT_URL_ORIGIN"
                            + " (legacy BASIS_* names are accepted aliases)");
        }
    }

    private LoginResult completePluginLogin(AuthIdentity identity) {
        if (identities == null) {
            throw new LoginRejectedException("identity linking is unavailable");
        }
        AppUserEntity user = provisionLinked(identity);
        applyMfaStatus(user, identity.mfaAssured() || localTotpAssured(identity));
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
        refreshProviderSession(sessionId);
        String apiToken =
                tokens.issue(
                        new UserId(user.getUserId()),
                        null,
                        Collections.emptyList(),
                        props.getTokenTtl());
        return new LoginResult(user.getUserId(), sessionId, apiToken);
    }

    /**
     * Completes a non-redirect (credential) login with an identity the provider already verified.
     */
    public LoginResult completeCredentialLogin(AuthIdentity identity) {
        if (dispatcher == null || !dispatcher.handles() || identity == null) {
            throw new LoginRejectedException("no credential identity to complete");
        }
        return completePluginLogin(identity);
    }

    /** Attaches a verified second identity to the signed-in account; email is never a join key. */
    public void linkCredentialIdentity(UUID userId, AuthIdentity identity) {
        if (identities == null || userId == null || identity == null) {
            throw new LoginRejectedException("identity linking is unavailable");
        }
        Optional<AppUserEntity> user = users.findById(userId);
        if (!user.isPresent() || !ACTIVE.equals(user.get().getStatus())) {
            throw new LoginRejectedException("account is not active");
        }
        UUID owner = identities.findUserId(identity.provider(), identity.subject());
        if (owner != null && !owner.equals(userId)) {
            throw new LoginRejectedException("identity is already linked to another user");
        }
        identities.linkToExistingUser(
                identity.provider(),
                identity.subject(),
                userId,
                identity.email(),
                identity.displayName(),
                clock.instant());
    }

    /** Rotates the provider tokens before handing the browser an API token. */
    private void refreshProviderSession(String sessionId) {
        if (dispatcher == null || sessionId == null) {
            return;
        }
        AuthProviderPlugin provider = dispatcher.active();
        if (provider == null || !PROVIDER_BASIS.equals(provider.descriptor().name())) {
            return;
        }
        PluginContext pluginContext =
                provider instanceof BasisAuthPlugin ? ((BasisAuthPlugin) provider).context() : null;
        provider.refresh(pluginContext, sessionId);
    }

    /** Ends every BFF session for the local user linked to a verified provider subject. */
    public void endSessionsForSubject(String provider, String subject) {
        if (identities == null || provider == null || subject == null) {
            return;
        }
        UUID userId = identities.findUserId(provider, subject);
        if (userId == null) {
            return;
        }
        List<BffSessionEntity> existing = sessions.findAll();
        for (int index = 0; index < existing.size(); index++) {
            BffSessionEntity session = existing.get(index);
            if (userId.equals(session.getUserId())) {
                sessions.delete(session);
            }
        }
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
            existing =
                    roles.findById(new UserRoleEntity.Id(user.getUserId(), "ROLE_ADMIN"))
                            .orElse(null);
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
    }

    /**
     * Persists the latest login's assurance for every plugin, so a weaker re-login fails closed.
     */
    private void applyMfaStatus(AppUserEntity user, boolean mfaAssured) {
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
            existing =
                    roles.findById(new UserRoleEntity.Id(user.getUserId(), "ROLE_ADMIN"))
                            .orElse(null);
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
        if (amrClaim instanceof List<?>) {
            List<?> list = (List<?>) amrClaim;
            for (Object value : list) if (value != null) amr.add(String.valueOf(value));
        } else if (amrClaim instanceof String) {
            String text = (String) amrClaim;
            if (!text.isBlank()) {
                for (String part : text.split("[\\s,]+")) if (!part.isBlank()) amr.add(part);
            }
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

    /** An AuthIdentity plus the time it was first seen, so plugin sessions cannot live forever. */
    private static final class TimedIdentity {
        private final AuthIdentity identity;
        private final Instant issuedAt;
        private final Duration lifetime;

        private TimedIdentity(AuthIdentity identity, Instant issuedAt, Duration lifetime) {
            this.identity = identity;
            this.issuedAt = issuedAt;
            this.lifetime = lifetime;
        }

        private AuthIdentity identity() {
            return identity;
        }

        private boolean expired(Instant now) {
            return !issuedAt.plus(lifetime).isAfter(now);
        }
    }
}
