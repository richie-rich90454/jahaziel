/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import org.junit.jupiter.api.Test;

final class ArchitectureTest {
  @Test
  void slicesAreFreeOfCycles() {
    JavaClasses classes = new ClassFileImporter().importPackages("dev.jahaziel");
    SlicesRuleDefinition.slices()
        .matching("dev.jahaziel.(*)..")
        .should()
        .beFreeOfCycles()
        .check(classes);
  }
}
