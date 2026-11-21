/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import org.junit.jupiter.api.Test;

/**
 * Module boundaries. One rule fences a specific internal package per feature pair; the other pins
 * the exact set of packages application classes may depend on. The list is intentionally explicit
 * (no catch-all) so a new module or dependency cannot slip in unnoticed. Extend it only for a real,
 * reviewed dependency, keeping the composition root ({@code dev.jahaziel.config}) and integration
 * seams in mind.
 */
class ModuleBoundaryTest {
    private static final JavaClasses CLASSES =
            new ClassFileImporter().importPackages("dev.jahaziel");

    /** Packages that are currently depended on and have been reviewed as allowed. */
    private static final String[] ALLOWED_PACKAGES = {
        "ch..",
        "com..",
        "io..",
        "jakarta..",
        "java..",
        "javax..",
        "jdk..",
        "junit..",
        "net..",
        "org..",
        "sun..",
        "tools..",
        "dev.jahaziel",
        "dev.jahaziel.admin.api",
        "dev.jahaziel.admin.internal",
        "dev.jahaziel.administration",
        "dev.jahaziel.administration.api",
        "dev.jahaziel.administration.internal",
        "dev.jahaziel.administration.persistence",
        "dev.jahaziel.agent",
        "dev.jahaziel.agent.chat",
        "dev.jahaziel.agent.diff",
        "dev.jahaziel.agent.model",
        "dev.jahaziel.agent.prompt",
        "dev.jahaziel.agent.session",
        "dev.jahaziel.agent.support",
        "dev.jahaziel.agent.usage",
        "dev.jahaziel.agenttools.api",
        "dev.jahaziel.agenttools.internal",
        "dev.jahaziel.api",
        "dev.jahaziel.api.controller",
        "dev.jahaziel.api.dto",
        "dev.jahaziel.arch",
        "dev.jahaziel.audit.api",
        "dev.jahaziel.audit.domain",
        "dev.jahaziel.audit.internal",
        "dev.jahaziel.audit.persistence",
        "dev.jahaziel.authorization",
        "dev.jahaziel.authorization.api",
        "dev.jahaziel.authorization.internal",
        "dev.jahaziel.cli",
        "dev.jahaziel.club",
        "dev.jahaziel.club.api",
        "dev.jahaziel.club.checkpoint",
        "dev.jahaziel.club.domain",
        "dev.jahaziel.club.export",
        "dev.jahaziel.club.moderation",
        "dev.jahaziel.club.persistence",
        "dev.jahaziel.club.session",
        "dev.jahaziel.club.showcase",
        "dev.jahaziel.club.support",
        "dev.jahaziel.club.tutorial",
        "dev.jahaziel.collaboration",
        "dev.jahaziel.config",
        "dev.jahaziel.datatools",
        "dev.jahaziel.debug.api",
        "dev.jahaziel.debug.internal",
        "dev.jahaziel.dist",
        "dev.jahaziel.dist.supervisor",
        "dev.jahaziel.e2e",
        "dev.jahaziel.extension",
        "dev.jahaziel.extension.api",
        "dev.jahaziel.extension.internal",
        "dev.jahaziel.extension.persistence",
        "dev.jahaziel.gateway",
        "dev.jahaziel.gateway.auth",
        "dev.jahaziel.gateway.proxy",
        "dev.jahaziel.gateway.routing",
        "dev.jahaziel.identity",
        "dev.jahaziel.identity.api",
        "dev.jahaziel.identity.auth",
        "dev.jahaziel.identity.auth.basis",
        "dev.jahaziel.identity.auth.credential",
        "dev.jahaziel.identity.auth.github",
        "dev.jahaziel.identity.auth.ldap",
        "dev.jahaziel.identity.auth.local",
        "dev.jahaziel.identity.auth.oidc",
        "dev.jahaziel.identity.auth.passkey",
        "dev.jahaziel.identity.auth.token",
        "dev.jahaziel.identity.auth.wiring",
        "dev.jahaziel.identity.domain",
        "dev.jahaziel.identity.internal",
        "dev.jahaziel.identity.internal.devconnect",
        "dev.jahaziel.identity.persistence",
        "dev.jahaziel.language.api",
        "dev.jahaziel.language.internal",
        "dev.jahaziel.monitoring",
        "dev.jahaziel.monitoring.api",
        "dev.jahaziel.monitoring.internal",
        "dev.jahaziel.monitoring.persistence",
        "dev.jahaziel.networking",
        "dev.jahaziel.networking.api",
        "dev.jahaziel.networking.internal",
        "dev.jahaziel.networking.persistence",
        "dev.jahaziel.node.api",
        "dev.jahaziel.node.domain",
        "dev.jahaziel.node.internal",
        "dev.jahaziel.node.persistence",
        "dev.jahaziel.nodeagent",
        "dev.jahaziel.nodeagent.audit",
        "dev.jahaziel.nodeagent.cgroup",
        "dev.jahaziel.nodeagent.config",
        "dev.jahaziel.nodeagent.firecracker",
        "dev.jahaziel.nodeagent.jail",
        "dev.jahaziel.nodeagent.service",
        "dev.jahaziel.nodeagent.storage",
        "dev.jahaziel.nodeagent.sync",
        "dev.jahaziel.ops",
        "dev.jahaziel.perf",
        "dev.jahaziel.platform",
        "dev.jahaziel.platform.api",
        "dev.jahaziel.platform.domain",
        "dev.jahaziel.platform.internal",
        "dev.jahaziel.platform.spi",
        "dev.jahaziel.plugin",
        "dev.jahaziel.policy",
        "dev.jahaziel.policy.api",
        "dev.jahaziel.policy.internal",
        "dev.jahaziel.policy.persistence",
        "dev.jahaziel.runtime",
        "dev.jahaziel.runtime.api",
        "dev.jahaziel.runtime.domain",
        "dev.jahaziel.runtime.internal",
        "dev.jahaziel.runtime.persistence",
        "dev.jahaziel.security",
        "dev.jahaziel.security.api",
        "dev.jahaziel.smoke",
        "dev.jahaziel.snapshot",
        "dev.jahaziel.snapshot.api",
        "dev.jahaziel.snapshot.diff",
        "dev.jahaziel.snapshot.domain",
        "dev.jahaziel.snapshot.internal",
        "dev.jahaziel.snapshot.persistence",
        "dev.jahaziel.spi",
        "dev.jahaziel.storage",
        "dev.jahaziel.storage.internal",
        "dev.jahaziel.storage.persistence",
        "dev.jahaziel.systemconfig",
        "dev.jahaziel.terminal",
        "dev.jahaziel.terminal.api",
        "dev.jahaziel.terminal.domain",
        "dev.jahaziel.terminal.internal",
        "dev.jahaziel.workspace.api",
        "dev.jahaziel.workspace.domain",
        "dev.jahaziel.workspace.internal",
        "dev.jahaziel.workspace.persistence",
        "dev.jahaziel.workspace.safety"
    };

    @Test
    void workspaceDoesNotDependOnIdentityInternal() {
        noClasses()
                .that()
                .resideInAPackage("dev.jahaziel.workspace..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("dev.jahaziel.identity.internal..")
                .check(CLASSES);
    }

    @Test
    void modulesOnlyAccessOtherModulesPublicApi() {
        classes()
                .that()
                .resideInAPackage("dev.jahaziel..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(ALLOWED_PACKAGES)
                .check(CLASSES);
    }
}
