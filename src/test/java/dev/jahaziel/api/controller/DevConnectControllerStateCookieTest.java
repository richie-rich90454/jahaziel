/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.identity.api.DevConnectService;
import dev.jahaziel.identity.api.UserService;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** The login state is bound to the browser that started the flow. */
final class DevConnectControllerStateCookieTest {
    private final DevConnectService devconnect = mock(DevConnectService.class);
    private final UserService users = mock(UserService.class);
    private final DevConnectController controller = new DevConnectController(devconnect, users);

    @Test
    void loginSetsStateCookieFromTheAuthorizationUrl() {
        when(devconnect.startLogin())
                .thenReturn("https://idp.example/authorize?state=abc123&nonce=n");
        ResponseEntity<Void> response = controller.login();
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertEquals(302, response.getStatusCode().value());
        assertTrue(cookies.get(0).contains(DevConnectController.STATE_COOKIE + "=abc123"));
        assertTrue(cookies.get(0).contains("SameSite=Lax"));
    }

    @Test
    void callbackConsumesStateOnlyWithAMatchingCookie() {
        when(devconnect.sessionTtl()).thenReturn(Duration.ofDays(1));
        when(devconnect.tokenTtl()).thenReturn(Duration.ofHours(1));
        when(devconnect.completeLogin("abc123", "code"))
                .thenReturn(
                        new DevConnectService.LoginResult(UUID.randomUUID(), "session", "token"));

        ResponseEntity<Void> accepted = controller.callback("abc123", "code", null, "abc123");
        assertEquals(302, accepted.getStatusCode().value());
        assertEquals("/", accepted.getHeaders().getLocation().toString());
        verify(devconnect).completeLogin("abc123", "code");

        assertEquals(
                "/?login=failed",
                controller
                        .callback("abc123", "code", null, null)
                        .getHeaders()
                        .getLocation()
                        .toString());
        assertEquals(
                "/?login=failed",
                controller
                        .callback("abc123", "code", null, "other")
                        .getHeaders()
                        .getLocation()
                        .toString());
        verify(devconnect, never()).completeLogin("abc123", "bad-code");
        assertFalse(accepted.getHeaders().get(HttpHeaders.SET_COOKIE).isEmpty());
    }
}
