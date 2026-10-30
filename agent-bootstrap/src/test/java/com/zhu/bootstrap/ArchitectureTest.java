package com.zhu.bootstrap;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureTest {

    private static final ArchRule SEMANTIC_MUST_NOT_DEPEND_ON_RUNTIME_OR_ACCESS =
            noClasses().that().resideInAPackage("..semantic..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..runtime..", "..access..");

    private static final ArchRule RUNTIME_ONLY_DEPENDS_ON_SEMANTIC_CONTRACT =
            noClasses().that().resideInAPackage("..runtime..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..semantic.application..", "..semantic.infrastructure..");

    private static final ArchRule STATE_MUST_NOT_DEPEND_ON_BUSINESS =
            noClasses().that().resideInAPackage("..state..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..semantic..", "..runtime..", "..access..");

    private static final ArchRule ACCESS_MUST_NOT_TOUCH_HARNESS =
            noClasses().that().resideInAPackage("..access..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..harness..");

    private static final ArchRule CONTROLLERS_MUST_NOT_ACCESS_REPOSITORIES =
            noClasses().that().resideInAPackage("..access.api.controller..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..infrastructure.persistence..");

    @Test
    void moduleBoundariesHold() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.zhu");
        assertThat(classes.stream().map(JavaClass::getName))
                .contains(
                        "com.zhu.semantic.contract.ProductSemanticProvider",
                        "com.zhu.runtime.api.AgentExecutor",
                        "com.zhu.state.config.DistributedStoreConfig",
                        "com.zhu.access.api.controller.LivenessController");

        SEMANTIC_MUST_NOT_DEPEND_ON_RUNTIME_OR_ACCESS.check(classes);
        RUNTIME_ONLY_DEPENDS_ON_SEMANTIC_CONTRACT.check(classes);
        STATE_MUST_NOT_DEPEND_ON_BUSINESS.check(classes);
        ACCESS_MUST_NOT_TOUCH_HARNESS.check(classes);
        CONTROLLERS_MUST_NOT_ACCESS_REPOSITORIES.check(classes);
    }
}
