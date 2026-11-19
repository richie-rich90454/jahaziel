/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.credential;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.identity.api.DevConnectService;
import dev.jahaziel.identity.api.LoginRejectedException;
import dev.jahaziel.identity.auth.AuthIdentity;
import dev.jahaziel.identity.auth.AuthPluginRegistry;
import dev.jahaziel.identity.auth.AuthProviderPlugin;
import dev.jahaziel.identity.auth.wiring.AuthProviderConfig;
import dev.jahaziel.identity.auth.wiring.AuthProviderDispatcher;
import dev.jahaziel.identity.internal.devconnect.DevConnectServiceImpl;
import dev.jahaziel.plugin.PluginContext;
import dev.jahaziel.plugin.PluginDescriptor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Credential endpoints route only to the active provider and pass only fixed fields. */
final class CredentialAuthServiceTest {
    @TempDir Path temp;

    private PluginContext context;
    private DevConnectServiceImpl login;
    private DevConnectService.LoginResult result;
    private String[] capturedState;

    @BeforeEach
    void setUp() {
        context = new PluginContext(temp, new HashMap<String, String>());
        login = mock(DevConnectServiceImpl.class);
        result = new DevConnectService.LoginResult(java.util.UUID.randomUUID(), "session", "token");
        when(login.completeCredentialLogin(any(AuthIdentity.class))).thenReturn(result);
        capturedState = new String[1];
    }

    @Test
    void localSubmissionReachesTheLocalProviderWithItsCredentials() {
        StubPlugin plugin =
                new StubPlugin(
                        "local", "local-sub", new String[] {"password", "totp", "mfa-assurance"});
        CredentialAuthService service = service(plugin);
        CredentialSubmission submission = new CredentialSubmission();
        submission.setUsername("alice");
        submission.setPassword("secret");
        submission.setTotp("123456");

        assertEquals(result, service.local(submission));

        assertEquals("alice", plugin.parameters.get("username"));
        assertEquals("secret", plugin.parameters.get("password"));
        assertEquals("123456", plugin.parameters.get("totp"));
        assertNotNull(plugin.parameters.get("state"));
        assertEquals(capturedState[0], plugin.parameters.get("state"));
        verify(login).completeCredentialLogin(any(AuthIdentity.class));
    }

    @Test
    void apiTokenSubmissionPassesTheTokenAndNotAnArbitraryUrl() {
        StubPlugin plugin = new StubPlugin("api-token", "token-sub", new String[] {"api-token"});
        CredentialSubmission submission = new CredentialSubmission();
        submission.setToken("issued-token");
        submission.setProvider("https://evil.example/cmd");

        assertEquals(result, service(plugin).token(submission));

        assertEquals("issued-token", plugin.parameters.get("token"));
        assertEquals(false, plugin.parameters.containsKey("provider"));
    }

    @Test
    void aDifferentCredentialEndpointCannotDriveTheActiveProvider() {
        StubPlugin ldap = new StubPlugin("ldap", "ldap-sub", new String[] {"password"});
        CredentialAuthService service = service(ldap);
        CredentialSubmission submission = new CredentialSubmission();
        submission.setUsername("alice");
        submission.setPassword("secret");

        assertThrows(
                LoginRejectedException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        service.local(submission);
                    }
                });
        assertThrows(
                LoginRejectedException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        service.passkey(submission);
                    }
                });
    }

    @Test
    void verificationRequiresAKnownProviderAndCapability() {
        final CredentialAuthService service =
                service(new StubPlugin("local", "local-sub", new String[] {"password", "totp"}));
        final CredentialSubmission unknown = new CredentialSubmission();
        unknown.setProvider("bogus");
        assertThrows(
                LoginRejectedException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        service.verify(unknown);
                    }
                });

        final CredentialSubmission passkey = new CredentialSubmission();
        passkey.setProvider("passkey");
        assertThrows(
                LoginRejectedException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        service.verify(passkey);
                    }
                });
    }

    @Test
    void passkeyOptionsComeFromTheActivePasskeyProvider() {
        StubPlugin plugin =
                new StubPlugin("passkey", "credential", new String[] {"passkey", "mfa-assurance"});
        plugin.startResult = "{\"challenge\":\"abc\",\"state\":\"state-1\"}";

        String options = service(plugin).passkeyOptions();

        assertEquals(plugin.startResult, options);
        assertTrue(capturedState[0].length() > 0);
    }

    private CredentialAuthService service(StubPlugin plugin) {
        AuthPluginRegistry registry = new AuthPluginRegistry();
        registry.register(plugin);
        AuthProviderDispatcher dispatcher =
                new AuthProviderDispatcher(
                        registry,
                        new AuthProviderConfig(
                                Arrays.asList(plugin.descriptor().name()), context));
        return new CredentialAuthService(dispatcher, login, context, "https://app.example/cb");
    }

    private final class StubPlugin extends AuthProviderPlugin {
        private final String subject;
        private Map<String, String> parameters = new HashMap<String, String>();
        private String startResult = "/login/local";

        private StubPlugin(String name, String subject, String[] capabilities) {
            super(
                    new PluginDescriptor(
                            name, "1", Collections.<String>emptyList(), Arrays.asList(capabilities)));
            this.subject = subject;
        }

        @Override
        public void initialize(PluginContext pluginContext) {}

        @Override
        public void shutdown() {}

        @Override
        public String startLogin(
                PluginContext pluginContext, String state, String nonce, String redirectUri) {
            capturedState[0] = state;
            return startResult;
        }

        @Override
        public AuthIdentity handleCallback(
                PluginContext pluginContext, Map<String, String> submitted) {
            parameters = new HashMap<String, String>(submitted);
            Map<String, String> claims = new HashMap<String, String>();
            claims.put("session_id", "provider-session");
            return new AuthIdentity(
                    descriptor().name(), subject, "user@example.org", "User", true, false, claims);
        }

        @Override
        public AuthIdentity currentIdentity(PluginContext pluginContext, String sessionId) {
            return null;
        }

        @Override
        public void endSession(PluginContext pluginContext, String sessionId) {}
    }
}
