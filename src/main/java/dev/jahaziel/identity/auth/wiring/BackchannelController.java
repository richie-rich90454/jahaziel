/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.wiring;

import dev.jahaziel.identity.api.DevConnectService;
import dev.jahaziel.identity.auth.BasisAuthPlugin;
import dev.jahaziel.identity.auth.basis.BackchannelHandler;
import dev.jahaziel.identity.auth.basis.HttpResult;
import dev.jahaziel.identity.auth.basis.TokenVerifier.TokenRejectedException;
import dev.jahaziel.identity.internal.devconnect.DevConnectServiceImpl;
import dev.jahaziel.plugin.PluginContext;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/** Exposes the basis-auth backchannel receiver published in the plugin context. */
@RestController
public final class BackchannelController {
    private static final String PROVIDER = "basis-auth";

    private final PluginContext context;
    private final DevConnectService devconnect;

    public BackchannelController(PluginContext context) {
        this(context, null);
    }

    /** Creates the receiver with the BFF service used to end sessions on a verified logout. */
    @Autowired
    public BackchannelController(PluginContext context, DevConnectService devconnect) {
        this.context = Objects.requireNonNull(context, "context");
        this.devconnect = devconnect;
    }

    /** Handles webhook verification, logout tokens, and access-token revocations. */
    @PostMapping({"/api/auth/basis/backchannel", "/api/auth/backchannel"})
    public ResponseEntity<String> handle(@RequestParam Map<String, String> form) {
        Object service = context.service(BasisAuthPlugin.SERVICE_BACKCHANNEL);
        if (!(service instanceof BackchannelHandler)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        BackchannelHandler handler = (BackchannelHandler) service;
        HttpResult result;
        try {
            result = handler.handle(form);
        } catch (TokenRejectedException rejected) {
            result = HttpResult.badRequest();
        }
        ResponseEntity.BodyBuilder response = ResponseEntity.status(result.status());
        for (Map.Entry<String, String> header : result.headers().entrySet()) {
            response.header(header.getKey(), header.getValue());
        }
        if (result.status() == 204 && result.subject() != null) {
            if (devconnect instanceof DevConnectServiceImpl) {
                ((DevConnectServiceImpl) devconnect)
                        .endSessionsForSubject(PROVIDER, result.subject());
            }
            response.header(
                    HttpHeaders.SET_COOKIE,
                    cleared(DevConnectService.SESSION_COOKIE).toString(),
                    cleared(DevConnectService.TOKEN_COOKIE).toString());
        }
        return response.body(result.body());
    }

    private static ResponseCookie cleared(String name) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }
}
