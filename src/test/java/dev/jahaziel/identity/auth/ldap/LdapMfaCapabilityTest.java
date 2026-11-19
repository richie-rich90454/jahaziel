/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.ldap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.identity.auth.AuthCapability;
import dev.jahaziel.identity.auth.AuthIdentity;
import dev.jahaziel.identity.auth.LdapPlugin;
import dev.jahaziel.plugin.PluginContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

final class LdapMfaCapabilityTest {
    @TempDir Path temp;

    private PluginContext context;
    private LdapPlugin plugin;

    @BeforeEach
    void setUp() {
        context = new PluginContext(temp, settings(true));
        context.putService(LdapPlugin.SERVICE_GATEWAY, new EvidenceGateway());
        plugin = new LdapPlugin();
        plugin.initialize(context);
    }

    @Test
    void descriptorAdvertisesMfaAssurance() {
        assertTrue(plugin.supports(AuthCapability.PASSWORD));
        assertTrue(plugin.supports(AuthCapability.MFA_ASSURANCE));
        assertFalse(plugin.supports(AuthCapability.PASSKEY));
    }

    @Test
    void mapperEvidenceDrivesMfaOnCallbackAndSession() {
        AuthIdentity identity = plugin.handleCallback(context, credentials());

        assertTrue(identity.mfaAssured());
        assertTrue(
                plugin.currentIdentity(context, identity.claims().get("session_id")).mfaAssured());
    }

    @Test
    void noConfiguredEvidenceMeansNoMfaClaim() {
        PluginContext plain = new PluginContext(temp.resolve("plain"), settings(false));
        plain.putService(LdapPlugin.SERVICE_GATEWAY, new EvidenceGateway());
        LdapPlugin plainPlugin = new LdapPlugin();
        plainPlugin.initialize(plain);

        AuthIdentity identity = plainPlugin.handleCallback(plain, credentials());

        assertFalse(identity.mfaAssured());
        assertFalse(
                plainPlugin
                        .currentIdentity(plain, identity.claims().get("session_id"))
                        .mfaAssured());
    }

    private static Map<String, String> settings(boolean withMfa) {
        Map<String, String> values = new HashMap<String, String>();
        values.put("ldap.url", "ldap://localhost:389");
        values.put("ldap.base-dn", "dc=example,dc=org");
        if (withMfa) {
            values.put("ldap.mfa-attribute", "employeeType");
            values.put("ldap.mfa-value", "mfa-verified");
        }
        return values;
    }

    private static Map<String, String> credentials() {
        Map<String, String> values = new HashMap<String, String>();
        values.put("username", "alice");
        values.put("password", "secret");
        return values;
    }

    /** Bound user whose directory entry carries the configured MFA evidence attribute. */
    private static final class EvidenceGateway extends LdapGateway {
        @Override
        public AuthIdentity authenticate(LdapConfig config, String username, String password) {
            if (!"alice".equals(username) || !"secret".equals(password)) {
                throw new LdapException("invalid credentials");
            }
            Map<String, String> attributes = new HashMap<String, String>();
            attributes.put(config.subjectAttribute(), "uuid-1234");
            attributes.put(config.emailAttribute(), "alice@example.org");
            attributes.put(config.displayNameAttribute(), "Alice");
            String mfaAttribute = config.mfaAttribute();
            if (mfaAttribute != null) {
                attributes.put(mfaAttribute, "mfa-verified");
            }
            return LdapIdentityMapper.map(config, attributes, "uid=alice,dc=example,dc=org");
        }
    }
}
