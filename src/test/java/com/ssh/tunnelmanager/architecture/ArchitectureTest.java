package com.ssh.tunnelmanager.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ArchitectureTest {
  static final ArchRule domainDoesNotDependOnUi =
      noClasses()
          .that()
          .resideInAnyPackage("com.ssh.tunnelmanager.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("com.ssh.tunnelmanager.ui..");

  static final ArchRule domainDoesNotDependOnInfrastructure =
      noClasses()
          .that()
          .resideInAnyPackage("com.ssh.tunnelmanager.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("com.ssh.tunnelmanager.infrastructure..");

  static final ArchRule domainDoesNotDependOnJavaFx =
      noClasses()
          .that()
          .resideInAnyPackage("com.ssh.tunnelmanager.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("javafx..");

  static final ArchRule domainDoesNotDependOnJdbc =
      noClasses()
          .that()
          .resideInAnyPackage("com.ssh.tunnelmanager.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("java.sql..", "org.sqlite..");

  static final ArchRule applicationDoesNotDependOnUi =
      noClasses()
          .that()
          .resideInAnyPackage("com.ssh.tunnelmanager.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("com.ssh.tunnelmanager.ui..");

  private JavaClasses classes;

  @BeforeEach
  void importApplicationClasses() {
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.ssh.tunnelmanager");
  }

  @Test
  void domainDoesNotDependOnUi() {
    domainDoesNotDependOnUi.check(classes);
  }

  @Test
  void domainDoesNotDependOnInfrastructure() {
    domainDoesNotDependOnInfrastructure.check(classes);
  }

  @Test
  void domainDoesNotDependOnJavaFx() {
    domainDoesNotDependOnJavaFx.check(classes);
  }

  @Test
  void domainDoesNotDependOnJdbc() {
    domainDoesNotDependOnJdbc.check(classes);
  }

  @Test
  void applicationDoesNotDependOnUi() {
    applicationDoesNotDependOnUi.check(classes);
  }
}
