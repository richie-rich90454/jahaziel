/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point of the control plane. One process, one port, one database. */
@SpringBootApplication
public class JahazielApplication {
  /**
   * Starts the control plane.
   *
   * @param args command line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(JahazielApplication.class, args);
  }
}
