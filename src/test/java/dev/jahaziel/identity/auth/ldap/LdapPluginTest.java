/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.ldap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.jahaziel.identity.auth.AuthCapability;
import dev.jahaziel.identity.auth.AuthIdentity;
import dev.jahaziel.identity.auth.LdapPlugin;
import dev.jahaziel.plugin.PluginContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

final class LdapPluginTest {
    @TempDir Path temp;

    private Map<String, String> settings;
    private PluginContext context;
    private LdapPlugin plugin;

    @BeforeEach
    void setUp() {
        settings = new HashMap<String, String>();
        settings.put("ldap.url", "ldap://localhost:389");
        settings.put("ldap.base-dn", "dc=example,dc=org");
        context = new PluginContext(temp, settings);
        context.putService(LdapPlugin.SERVICE_GATEWAY, new FakeGateway());
        plugin = new LdapPlugin();
        plugin.initialize(context);
    }

    @Test
    void descriptorAdvertisesPasswordAndMfaAssurance() {
        assertEquals("ldap", plugin.descriptor().name());
        assertTrue(plugin.supports(AuthCapability.PASSWORD));
        assertTrue(plugin.supports(AuthCapability.MFA_ASSURANCE));
        assertFalse(plugin.supports(AuthCapability.PASSKEY));
    }

    @Test
    void configValidationRejectsMissingOrInvalidSettings() {
        Map<String, String> none = new HashMap<String, String>();
        try {
            new LdapConfig(new PluginContext(temp.resolve("none"), none));
            fail("missing url must be rejected");
        } catch (LdapException expected) {
            assertTrue(expected.getMessage().contains("url"));
        }

        Map<String, String> badScheme = new HashMap<String, String>();
        badScheme.put("ldap.url", "http://localhost");
        badScheme.put("ldap.base-dn", "dc=example,dc=org");
        try {
            new LdapConfig(new PluginContext(temp.resolve("scheme"), badScheme));
            fail("invalid scheme must be rejected");
        } catch (LdapException expected) {
            assertTrue(expected.getMessage().contains("ldap://"));
        }

        Map<String, String> noBase = new HashMap<String, String>();
        noBase.put("ldap.url", "ldap://localhost:389");
        try {
            new LdapConfig(new PluginContext(temp.resolve("base"), noBase));
            fail("missing base DN must be rejected");
        } catch (LdapException expected) {
            assertTrue(expected.getMessage().contains("base"));
        }
    }

    @Test
    void mappingUsesConfiguredAttributeAndFallsBack() {
        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put("entryUUID", "uuid-1234");
        attributes.put("objectGUID", "guid-5678");
        attributes.put("mail", "alice@example.org");
        attributes.put("displayName", "Alice");
        LdapConfig config = new LdapConfig(context);

        AuthIdentity identity =
                LdapIdentityMapper.map(config, attributes, "uid=alice,dc=example,dc=org");
        assertEquals("ldap", identity.provider());
        assertEquals("uuid-1234", identity.subject());
        assertEquals("alice@example.org", identity.email());
        assertEquals("Alice", identity.displayName());
        assertFalse(identity.emailVerified());
        assertFalse(identity.mfaAssured());

        Map<String, String> withoutEntry = new HashMap<String, String>(attributes);
        withoutEntry.remove("entryUUID");
        assertEquals(
                "guid-5678", LdapIdentityMapper.map(config, withoutEntry, "uid=alice").subject());

        Map<String, String> bare = new HashMap<String, String>();
        assertEquals("uid=alice", LdapIdentityMapper.map(config, bare, "uid=alice").subject());
    }

    @Test
    void mfaIsClaimedOnlyWhenConfiguredAttributeProvesIt() {
        Map<String, String> mfaSettings = new HashMap<String, String>(settings);
        mfaSettings.put("ldap.mfa-attribute", "employeeType");
        mfaSettings.put("ldap.mfa-value", "mfa-verified");
        LdapConfig mfaConfig = new LdapConfig(new PluginContext(temp.resolve("mfa"), mfaSettings));

        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put("entryUUID", "uuid-1234");
        attributes.put("employeeType", "mfa-verified");
        assertTrue(LdapIdentityMapper.map(mfaConfig, attributes, "uid=alice").mfaAssured());

        attributes.put("employeeType", "regular");
        assertFalse(LdapIdentityMapper.map(mfaConfig, attributes, "uid=alice").mfaAssured());

        LdapConfig plain = new LdapConfig(new PluginContext(temp.resolve("plain"), settings));
        attributes.put("employeeType", "mfa-verified");
        assertFalse(LdapIdentityMapper.map(plain, attributes, "uid=alice").mfaAssured());
    }

    @Test
    void callbackBindsAndManagesSessionUnderDataDir() throws IOException {
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("username", "alice");
        parameters.put("password", "secret");
        AuthIdentity identity = plugin.handleCallback(context, parameters);

        assertEquals("ldap", identity.provider());
        assertEquals("uuid-1234", identity.subject());
        assertEquals("alice@example.org", identity.email());
        assertFalse(identity.mfaAssured());
        String sessionId = identity.claims().get("session_id");
        assertNotNull(sessionId);
        assertEquals("uuid-1234", plugin.currentIdentity(context, sessionId).subject());

        Path store = temp.resolve("ldap").resolve("ldap-store.json");
        assertTrue(Files.exists(store));
        String stored = new String(Files.readAllBytes(store), "UTF-8");
        assertFalse(stored.contains("secret"));

        plugin.endSession(context, sessionId);
        assertNull(plugin.currentIdentity(context, sessionId));
    }

    @Test
    void missingOrInvalidCredentialsAreRejected() {
        Map<String, String> missing = new HashMap<String, String>();
        try {
            plugin.handleCallback(context, missing);
            fail("missing credentials must be rejected");
        } catch (LdapException expected) {
            assertTrue(expected.getMessage().contains("credentials"));
        }

        Map<String, String> wrong = new HashMap<String, String>();
        wrong.put("username", "alice");
        wrong.put("password", "wrong");
        try {
            plugin.handleCallback(context, wrong);
            fail("bad password must be rejected");
        } catch (LdapException expected) {
            assertEquals("invalid credentials", expected.getMessage());
        }
    }

    /** In-memory fake: no network, no javax.naming context required. */
    private static final class FakeGateway extends LdapGateway {
        @Override
        public AuthIdentity authenticate(LdapConfig config, String username, String password) {
            if (!"alice".equals(username) || !"secret".equals(password)) {
                throw new LdapException("invalid credentials");
            }
            Map<String, String> attributes = new HashMap<String, String>();
            attributes.put(config.subjectAttribute(), "uuid-1234");
            attributes.put(config.emailAttribute(), "alice@example.org");
            attributes.put(config.displayNameAttribute(), "Alice");
            return LdapIdentityMapper.map(config, attributes, "uid=alice,dc=example,dc=org");
        }
    }
}
