/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import org.junit.jupiter.api.Test;

final class ArchitectureTest {
  @Test
  void slicesAreFreeOfCycles() {
    // ponytail: tests wire slices together on purpose; the rule guards production code.
    JavaClasses classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("dev.jahaziel");
    SlicesRuleDefinition.slices()
        .matching("dev.jahaziel.(*)..")
        .should()
        .beFreeOfCycles()
        .check(classes);
  }
}
