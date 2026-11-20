/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

/** Decides whether an extension may be installed. */
@JahazielSpi(version = 1)
public abstract class ExtensionPolicy {
    /**
     * Decides one extension request.
     *
     * @param request the install request
     * @return the decision
     */
    public abstract Decision decide(ExtensionRequest request);
}
