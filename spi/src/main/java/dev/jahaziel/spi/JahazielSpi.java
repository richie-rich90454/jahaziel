/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a plugin interface and declares its version. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JahazielSpi {
  /**
   * Returns the interface version. Breaking changes require a new version.
   *
   * @return the interface version
   */
  int version();

  /**
   * Returns the stability of the interface.
   *
   * @return the stability, stable by default
   */
  Stability stability() default Stability.STABLE;
}
