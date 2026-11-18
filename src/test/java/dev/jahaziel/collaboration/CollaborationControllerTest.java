/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.collaboration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

final class CollaborationControllerTest {
    @Test
    void everyEndpointChecksWorkspaceRole() {
        final CollaborationFixture fixture = new CollaborationFixture(System.currentTimeMillis());
        final UUID workspace = UUID.fromString(CollaborationFixture.WORKSPACE);
        List<String> members =
                fixture.controller.members(workspace, CollaborationFixture.principal(CollaborationFixture.VIEWER));
        assertEquals(3, members.size());

        final Map<String, String> invite = new HashMap<String, String>();
        invite.put("invitee", "someone");
        invite.put("role", Roles.EDITOR);
        assertThrows(
                CollaborationAccessDeniedException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        fixture.controller.invite(
                                workspace,
                                CollaborationFixture.principal(CollaborationFixture.VIEWER),
                                invite);
                    }
                });
        assertTrue(
                fixture.controller
                        .invite(
                                workspace,
                                CollaborationFixture.principal(CollaborationFixture.OWNER),
                                invite)
                        .containsKey("invitationId"));
    }

    @Test
    void controllerIsAnnotatedRestController() {
        assertTrue(CollaborationController.class.isAnnotationPresent(RestController.class));
        RequestMapping mapping = CollaborationController.class.getAnnotation(RequestMapping.class);
        assertTrue(mapping != null && mapping.value().length == 1);
        assertEquals("/api/v1/workspaces/{id}", mapping.value()[0]);
    }
}
