/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.wiring;

import dev.jahaziel.identity.auth.BasisAuthPlugin;
import dev.jahaziel.identity.auth.basis.BackchannelHandler;
import dev.jahaziel.identity.auth.basis.HttpResult;
import dev.jahaziel.identity.auth.basis.TokenVerifier.TokenRejectedException;
import dev.jahaziel.plugin.PluginContext;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

/** Exposes the basis-auth backchannel receiver published in the plugin context. */
@RestController
public final class BackchannelController {
    private final PluginContext context;

    public BackchannelController(PluginContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    /** Handles webhook verification, logout tokens, and access-token revocations. */
    @PostMapping("/api/auth/backchannel")
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
        return response.body(result.body());
    }
}
