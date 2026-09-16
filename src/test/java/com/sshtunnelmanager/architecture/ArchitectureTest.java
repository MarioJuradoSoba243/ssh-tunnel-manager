package com.sshtunnelmanager.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "com.sshtunnelmanager",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
  @ArchTest
  static final ArchRule DOMAIN_IS_INDEPENDENT =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..ui..",
              "..infrastructure..",
              "javafx..",
              "java.sql..",
              "javax.sql..",
              "org.apache.sshd..");

  @ArchTest
  static final ArchRule APPLICATION_DOES_NOT_DEPEND_ON_UI =
      noClasses()
          .that()
          .resideInAPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..ui..");
}
