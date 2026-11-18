/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.plugin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PluginRegistryTest {
    @Test
    void initializesDependenciesBeforeDependants() {
        List<String> events = new ArrayList<String>();
        TestPlugin base = new TestPlugin("base", Collections.<String>emptyList(), events);
        TestPlugin feature = new TestPlugin("feature", Collections.singletonList("base"), events);
        PluginRegistry registry = new PluginRegistry();
        registry.register(feature);
        registry.register(base);

        registry.initializeAll(new PluginContext(Paths.get("data"), Collections.<String, String>emptyMap()));

        assertEquals(Arrays.asList("init:base", "init:feature"), events);
        assertTrue(registry.isInitialized());
    }

    @Test
    void shutsDownInReverseOrder() {
        List<String> events = new ArrayList<String>();
        TestPlugin base = new TestPlugin("base", Collections.<String>emptyList(), events);
        TestPlugin feature = new TestPlugin("feature", Collections.singletonList("base"), events);
        PluginRegistry registry = new PluginRegistry();
        registry.register(feature);
        registry.register(base);
        registry.initializeAll(new PluginContext(Paths.get("data"), Collections.<String, String>emptyMap()));

        registry.shutdownAll();

        assertEquals(
                Arrays.asList("init:base", "init:feature", "shutdown:feature", "shutdown:base"), events);
        assertFalse(registry.isInitialized());
    }

    @Test
    void rejectsDuplicateNames() {
        final PluginRegistry registry = new PluginRegistry();
        registry.register(new TestPlugin("base", Collections.<String>emptyList()));
        assertThrows(
                IllegalStateException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        registry.register(new TestPlugin("base", Collections.<String>emptyList()));
                    }
                });
    }

    @Test
    void rejectsMissingDependencies() {
        final PluginRegistry registry = new PluginRegistry();
        registry.register(new TestPlugin("feature", Collections.singletonList("missing")));
        assertThrows(
                IllegalStateException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        registry.initializeAll(
                                new PluginContext(Paths.get("data"), Collections.<String, String>emptyMap()));
                    }
                });
    }

    @Test
    void rejectsDependencyCycles() {
        final PluginRegistry registry = new PluginRegistry();
        registry.register(new TestPlugin("a", Collections.singletonList("b")));
        registry.register(new TestPlugin("b", Collections.singletonList("a")));
        assertThrows(
                IllegalStateException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        registry.initializeAll(
                                new PluginContext(Paths.get("data"), Collections.<String, String>emptyMap()));
                    }
                });
    }

    @Test
    void descriptorExposesCapabilities() {
        PluginDescriptor descriptor =
                new PluginDescriptor(
                        "p", "1", Collections.<String>emptyList(), Collections.singletonList("oidc"));
        assertTrue(descriptor.provides("oidc"));
        assertFalse(descriptor.provides("totp"));
        assertEquals("p@1", descriptor.toString());
    }

    private static final class TestPlugin extends Plugin {
        private final List<String> events;

        private TestPlugin(String name, List<String> requires) {
            this(name, requires, new ArrayList<String>());
        }

        private TestPlugin(String name, List<String> requires, List<String> events) {
            super(new PluginDescriptor(name, "1", requires, Collections.<String>emptyList()));
            this.events = events;
        }

        @Override
        public void initialize(PluginContext context) {
            events.add("init:" + descriptor().name());
        }

        @Override
        public void shutdown() {
            events.add("shutdown:" + descriptor().name());
        }
    }
}
