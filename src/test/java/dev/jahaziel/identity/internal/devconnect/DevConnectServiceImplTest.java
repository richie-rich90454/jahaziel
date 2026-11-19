/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.internal.devconnect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.identity.api.DevConnectService;
import dev.jahaziel.identity.api.JwtService;
import dev.jahaziel.identity.api.LoginRejectedException;
import dev.jahaziel.identity.auth.AuthIdentity;
import dev.jahaziel.identity.auth.AuthPluginRegistry;
import dev.jahaziel.identity.auth.AuthProviderPlugin;
import dev.jahaziel.identity.auth.wiring.AuthProviderConfig;
import dev.jahaziel.identity.auth.wiring.AuthProviderDispatcher;
import dev.jahaziel.identity.auth.wiring.UserIdentityStore;
import dev.jahaziel.identity.persistence.AppUserEntity;
import dev.jahaziel.identity.persistence.AppUserRepository;
import dev.jahaziel.identity.persistence.BffSessionEntity;
import dev.jahaziel.identity.persistence.BffSessionRepository;
import dev.jahaziel.identity.persistence.OidcPendingRepository;
import dev.jahaziel.identity.persistence.UserRoleRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import dev.jahaziel.plugin.PluginContext;
import dev.jahaziel.plugin.PluginDescriptor;

final class DevConnectServiceImplTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
  private static final String REDIRECT = "https://app.example.org/cb";

  private DevConnectProperties props;
  private DevConnectClient client;
  private OidcPendingRepository pending;
  private BffSessionRepository sessions;
  private AppUserRepository users;
  private UserRoleRepository roles;
  private JwtService tokens;
  private UserIdentityStore identities;

  @BeforeEach
  void setUp() {
    props = new DevConnectProperties();
    props.setAuthIssuer("https://idp.example.org");
    props.setRedirectUri(REDIRECT);
    props.setClientId("client");
    props.setClientSecret("secret");
    props.setResource("urn:example:api");
    client = mock(DevConnectClient.class);
    pending = mock(OidcPendingRepository.class);
    sessions = mock(BffSessionRepository.class);
    users = mock(AppUserRepository.class);
    roles = mock(UserRoleRepository.class);
    tokens = mock(JwtService.class);
    identities = mock(UserIdentityStore.class);
    when(tokens.issue(any(), any(), any(), any())).thenReturn("api-token");
    when(users.save(any(AppUserEntity.class)))
        .thenAnswer(
            new Answer<AppUserEntity>() {
              @Override
              public AppUserEntity answer(org.mockito.invocation.InvocationOnMock invocation) {
                return invocation.getArgument(0);
              }
            });
  }

  @Test
  void pluginLoginLinksByProviderAndSubjectAndNeverByEmail() {
    AuthIdentity identity = identity("google", "sub-1", false);
    AppUserEntity emailMatch = activeUser(UUID.randomUUID(), "user@example.org");
    when(users.findByEmailIgnoreCase("user@example.org")).thenReturn(List.of(emailMatch));

    DevConnectServiceImpl service = service(dispatcher(identity));
    DevConnectService.LoginResult result =
        service.completeLogin("state-1", "plugin-code");

    assertNotNull(result.userId());
    assertNotSame(emailMatch.getUserId(), result.userId());
    assertEquals("plugin-session-1", result.sessionId());
    verify(identities).link(
        eq("google"),
        eq("sub-1"),
        eq(result.userId()),
        eq("user@example.org"),
        eq("Example User"),
        any(Instant.class));
    verify(users, never()).findByEmailIgnoreCase(anyString());
    verify(sessions).save(any(BffSessionEntity.class));
  }

  @Test
  void pluginLoginReusesExistingIdentityLink() {
    AuthIdentity identity = identity("google", "sub-1", false);
    UUID linkedId = UUID.randomUUID();
    AppUserEntity linked = activeUser(linkedId, "user@example.org");
    when(identities.findUserId("google", "sub-1")).thenReturn(linkedId);
    when(users.findById(linkedId)).thenReturn(Optional.of(linked));

    DevConnectService.LoginResult result =
        service(dispatcher(identity)).completeLogin("state-1", "plugin-code");

    assertEquals(linkedId, result.userId());
    verify(identities, never())
        .link(anyString(), anyString(), any(UUID.class), anyString(), anyString(), any(Instant.class));
  }

  @Test
  void identityMfaAssuranceWritesMfaFlag() {
    props.setAdminBootstrapEmail("user@example.org");
    AuthIdentity assured = identity("google", "sub-1", true);

    service(dispatcher(assured)).completeLogin("state-1", "plugin-code");

    ArgumentCaptor<AppUserEntity> saved = ArgumentCaptor.forClass(AppUserEntity.class);
    verify(users, atLeastOnce()).save(saved.capture());
    List<AppUserEntity> values = saved.getAllValues();
    assertTrue(values.get(values.size() - 1).isMfaEnabled());

    verify(roles)
        .save(
            org.mockito.ArgumentMatchers.argThat(
                new org.mockito.ArgumentMatcher<dev.jahaziel.identity.persistence.UserRoleEntity>() {
                  @Override
                  public boolean matches(
                      dev.jahaziel.identity.persistence.UserRoleEntity role) {
                    return "ROLE_ADMIN".equals(role.getId().getRole());
                  }
                }));
  }

  @Test
  void weakerReloginWritesMfaFlagFalse() {
    props.setAdminBootstrapEmail("user@example.org");
    UUID linkedId = UUID.randomUUID();
    AppUserEntity linked = activeUser(linkedId, "user@example.org");
    linked.setMfaEnabled(true);
    when(identities.findUserId("google", "sub-1")).thenReturn(linkedId);
    when(users.findById(linkedId)).thenReturn(Optional.of(linked));

    service(dispatcher(identity("google", "sub-1", false)))
        .completeLogin("state-1", "plugin-code");

    ArgumentCaptor<AppUserEntity> saved = ArgumentCaptor.forClass(AppUserEntity.class);
    verify(users, atLeastOnce()).save(saved.capture());
    List<AppUserEntity> values = saved.getAllValues();
    assertFalse(values.get(values.size() - 1).isMfaEnabled());
  }

  @Test
  void localTotpAssuranceWritesMfaFlag() {
    props.setAdminBootstrapEmail("user@example.org");
    Map<String, String> claims = new HashMap<String, String>();
    claims.put("session_id", "plugin-session-1");
    claims.put("mfa_enrollment", "complete");
    AuthIdentity local =
        new AuthIdentity(
            "local",
            "alice",
            "user@example.org",
            "Example User",
            true,
            false,
            claims);

    service(dispatcher(local)).completeLogin("state-1", "plugin-code");

    ArgumentCaptor<AppUserEntity> saved = ArgumentCaptor.forClass(AppUserEntity.class);
    verify(users, atLeastOnce()).save(saved.capture());
    List<AppUserEntity> values = saved.getAllValues();
    assertTrue(values.get(values.size() - 1).isMfaEnabled());
  }

  @Test
  void fallsBackToDevConnectWhenNoProviderIsConfigured() {
    when(client.authorizationUrl(anyString(), anyString(), anyString()))
        .thenReturn("https://idp.example.org/authorize");
    AuthPluginRegistry registry = new AuthPluginRegistry();
    DevConnectServiceImpl service =
        new DevConnectServiceImpl(
            props,
            client,
            pending,
            sessions,
            users,
            roles,
            tokens,
            CLOCK,
            new AuthProviderDispatcher(
                registry, new AuthProviderConfig(Collections.<String>emptyList(), null)),
            identities);

    assertEquals("https://idp.example.org/authorize", service.startLogin());
    assertThrows(
        LoginRejectedException.class,
        new Executable() {
          @Override
          public void execute() {
            service.completeLogin("state-1", "plugin-code");
          }
        });
  }

  @Test
  void logoutEndsPluginAndBrowserSessions() {
    StubPlugin plugin = new StubPlugin(identity("google", "sub-1", false));
    BffSessionEntity session = new BffSessionEntity();
    session.setSessionId("plugin-session-1");
    when(sessions.findById("plugin-session-1")).thenReturn(Optional.of(session));

    service(dispatcher(plugin)).logout("plugin-session-1");

    assertEquals("plugin-session-1", plugin.endedSession);
    verify(sessions).delete(session);
  }

  private DevConnectServiceImpl service(AuthProviderDispatcher dispatcher) {
    return new DevConnectServiceImpl(
        props, client, pending, sessions, users, roles, tokens, CLOCK, dispatcher, identities);
  }

  private static AuthProviderDispatcher dispatcher(StubPlugin plugin) {
    AuthPluginRegistry registry = new AuthPluginRegistry();
    registry.register(plugin);
    return new AuthProviderDispatcher(
        registry,
        new AuthProviderConfig(
            Arrays.asList(plugin.identity.provider()), (PluginContext) null));
  }

  private static AuthProviderDispatcher dispatcher(AuthIdentity identity) {
    return dispatcher(new StubPlugin(identity));
  }

  private static AuthIdentity identity(String provider, String subject, boolean mfaAssured) {
    Map<String, String> claims = new HashMap<String, String>();
    claims.put("session_id", "plugin-session-1");
    return new AuthIdentity(
        provider,
        subject,
        "user@example.org",
        "Example User",
        true,
        mfaAssured,
        claims);
  }

  private static AppUserEntity activeUser(UUID userId, String email) {
    AppUserEntity user = new AppUserEntity();
    user.setUserId(userId);
    user.setEmail(email);
    user.setStatus("active");
    return user;
  }

  private static final class StubPlugin extends AuthProviderPlugin {
    private final AuthIdentity identity;
    private String endedSession;

    private StubPlugin(AuthIdentity identity) {
      super(
          new PluginDescriptor(
              identity.provider(),
              "1",
              Collections.<String>emptyList(),
              Collections.<String>emptyList()));
      this.identity = identity;
    }

    @Override
    public void initialize(PluginContext context) {}

    @Override
    public void shutdown() {}

    @Override
    public String startLogin(
        PluginContext context, String state, String nonce, String redirectUri) {
      return "https://idp.example.org/authorize";
    }

    @Override
    public AuthIdentity handleCallback(PluginContext context, Map<String, String> parameters) {
      return identity;
    }

    @Override
    public AuthIdentity currentIdentity(PluginContext context, String sessionId) {
      return identity;
    }

    @Override
    public void endSession(PluginContext context, String sessionId) {
      endedSession = sessionId;
    }
  }
}
