/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api;

import dev.jahaziel.config.AppConfig;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Beans unique to the API layer. All shared agent, data-tool, club and ops services are declared
 * once in {@link AppConfig}; declaring them again here caused duplicate bean definitions.
 */
@Configuration
public class ApiModulesConfig {
    @Bean
    @ConditionalOnMissingBean
    public ClubRegistry clubRegistry() {
        return new ClubRegistry();
    }
}
