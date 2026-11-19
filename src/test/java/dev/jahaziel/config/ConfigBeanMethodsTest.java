/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;

import java.lang.reflect.Method;

/** Verifies the new services are exposed as explicit bean methods. */
class ConfigBeanMethodsTest {
    @Test
    void appConfigDeclaresThePlainServiceBeans() {
        assertBeanMethod(AppConfig.class, "dev.jahaziel.agent.AgentStore");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.agent.session.AgentSessionService");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.agent.prompt.PromptLibrary");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.agent.diff.DiffReviewService");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.agent.chat.AgentChatService");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.agent.model.ModelCatalog");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.agent.usage.UsageMeters");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.datatools.SqliteBrowser");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.datatools.CsvReader");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.datatools.CsvWriter");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.datatools.NotebookRunner");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.datatools.SearchIndex");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.datatools.FileHistoryService");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.club.api.ClubService");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.club.session.ClubSessionService");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.club.tutorial.TutorialParser");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.club.tutorial.StepVerifier");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.club.tutorial.ProgressStore");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.club.showcase.ShowcaseGallery");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.club.checkpoint.CheckpointService");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.club.moderation.ModerationService");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.club.export.SessionExporter");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.club.persistence.ClubSessionStore");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.ops.ConfigValidationService");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.ops.StructuredLogger");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.ops.SupportBundleService");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.ops.GracefulDrainService");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.ops.ScheduledBackupService");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.ops.RestoreDrillService");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.ops.QuotaService");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.ops.RateLimiter");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.ops.AuditQueryService");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.ops.GdprService");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.ops.ApprovalQueue");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.ops.OutboundWebhooks");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.security.CommandAllowlist");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.security.WebSocketOriginGuard");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.security.SessionHardening");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.security.DependencyAuditReport");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.spi.AuditSink");
    }

    @Test
    void appConfigAlsoDeclaresTheCollaborationBeans() {
        assertBeanMethod(AppConfig.class, "dev.jahaziel.collaboration.CollaborationStore");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.collaboration.CollaborationAudit");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.collaboration.MembershipService");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.collaboration.PresenceService");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.collaboration.CommentService");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.collaboration.LiveSessionService");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.collaboration.ShareLinkService");
        assertBeanMethod(AppConfig.class, "dev.jahaziel.collaboration.CollaborationController");
    }

    @Test
    void pluginConfigDeclaresEveryPluginAndTheRegistry() {
        assertBeanMethod(PluginWiringConfig.class, "dev.jahaziel.plugin.PluginContext");
        assertBeanMethod(
                PluginWiringConfig.class, "dev.jahaziel.platform.spi.LinuxProcessEnvironment");
        assertBeanMethod(
                PluginWiringConfig.class, "dev.jahaziel.platform.spi.MacProcessEnvironment");
        assertBeanMethod(
                PluginWiringConfig.class, "dev.jahaziel.platform.spi.WindowsProcessEnvironment");
        assertBeanMethod(
                PluginWiringConfig.class, "dev.jahaziel.networking.internal.LinuxPortDiscovery");
        assertBeanMethod(
                PluginWiringConfig.class, "dev.jahaziel.networking.internal.MacPortDiscovery");
        assertBeanMethod(
                PluginWiringConfig.class, "dev.jahaziel.networking.internal.WindowsPortDiscovery");
        assertBeanMethod(PluginWiringConfig.class, "dev.jahaziel.identity.auth.BasisAuthPlugin");
        assertBeanMethod(
                PluginWiringConfig.class, "dev.jahaziel.identity.auth.MicrosoftAuthPlugin");
        assertBeanMethod(PluginWiringConfig.class, "dev.jahaziel.identity.auth.GoogleAuthPlugin");
        assertBeanMethod(PluginWiringConfig.class, "dev.jahaziel.identity.auth.GitHubAuthPlugin");
        assertBeanMethod(PluginWiringConfig.class, "dev.jahaziel.identity.auth.GenericOidcPlugin");
        assertBeanMethod(
                PluginWiringConfig.class, "dev.jahaziel.identity.auth.LocalPasswordPlugin");
        assertBeanMethod(PluginWiringConfig.class, "dev.jahaziel.identity.auth.PasskeyPlugin");
        assertBeanMethod(PluginWiringConfig.class, "dev.jahaziel.identity.auth.ApiTokenPlugin");
        assertBeanMethod(PluginWiringConfig.class, "dev.jahaziel.identity.auth.LdapPlugin");
        assertBeanMethod(PluginWiringConfig.class, "dev.jahaziel.identity.auth.AuthPluginRegistry");
        assertBeanMethod(PluginWiringConfig.class, "dev.jahaziel.ops.OpsShutdownHook");
        assertBeanMethod(PluginWiringConfig.class, "dev.jahaziel.config.OpsShutdownHookRegistrar");
    }

    private static void assertBeanMethod(Class<?> owner, String returnTypeName) {
        boolean found = false;
        Method[] methods = owner.getMethods();
        for (int index = 0; index < methods.length; index++) {
            Method method = methods[index];
            if (method.isAnnotationPresent(Bean.class)
                    && method.getReturnType().getName().equals(returnTypeName)) {
                found = true;
            }
        }
        assertTrue(
                found,
                "missing @Bean method returning " + returnTypeName + " on " + owner.getName());
    }
}
