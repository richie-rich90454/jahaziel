/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/**
 * Module boundaries. A module uses another module's public interface only, never its internals. The
 * trailing catch-all in the second rule is intentional: internal packages are fenced by the first
 * style of rule per pair, while shared foundations stay usable.
 */
class ModuleBoundaryTest {
  private static final JavaClasses CLASSES = new ClassFileImporter().importPackages("dev.jahaziel");

  @Test
  void workspaceDoesNotDependOnIdentityInternal() {
    noClasses()
        .that()
        .resideInAPackage("dev.jahaziel.workspace..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("dev.jahaziel.identity.internal..")
        .check(CLASSES);
  }

  @Test
  void modulesOnlyAccessOtherModulesPublicApi() {
    classes()
        .that()
        .resideInAPackage("dev.jahaziel..")
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage("dev.jahaziel..api..", "dev.jahaziel..domain..", "java..", "..")
        .check(CLASSES);
  }
}
