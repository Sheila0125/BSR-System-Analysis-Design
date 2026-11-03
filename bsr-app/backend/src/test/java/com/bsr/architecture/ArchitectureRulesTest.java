package com.bsr.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ArchitectureRulesTest {

    private final JavaClasses classes = new ClassFileImporter().importPackages("com.bsr");

    @Test
    @DisplayName("S01 domain code does not depend on frameworks")
    void domainCodeDoesNotDependOnFrameworks() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "jakarta.servlet..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    @DisplayName("S01 web code does not depend on repositories")
    void webCodeDoesNotDependOnRepositories() {
        noClasses()
                .that().resideInAPackage("..web..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..infrastructure.persistence..",
                        "org.springframework.data.repository..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    @DisplayName("S01 business modules do not depend on another module internals")
    void modulesDoNotDependOnAnotherModuleInternals() {
        String[] modules = {
            "identity",
            "customerrequest",
            "rental",
            "equipment",
            "repair",
            "storetransfer",
            "scheduling",
            "audit"
        };
        for (String source : modules) {
            for (String target : modules) {
                if (!source.equals(target)) {
                    noClasses()
                            .that().resideInAPackage("com.bsr." + source + "..")
                            .should().dependOnClassesThat().resideInAnyPackage(
                                    "com.bsr." + target + ".application..",
                                    "com.bsr." + target + ".domain..",
                                    "com.bsr." + target + ".infrastructure..",
                                    "com.bsr." + target + ".web..")
                            .allowEmptyShould(true)
                            .check(classes);
                }
            }
        }
    }
}
