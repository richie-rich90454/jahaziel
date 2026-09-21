/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import java.util.Optional;

/**
 * Reads secrets from the environment or an external manager. Secrets never live in configuration
 * files.
 */
@JahazielSpi(version = 1)
public interface SecretProvider {
  /**
   * Reads one secret.
   *
   * @param key the secret key
   * @return the secret value, empty when unset
   */
  Optional<String> get(String key);
}
