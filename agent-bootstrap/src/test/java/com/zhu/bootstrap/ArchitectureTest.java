package com.zhu.bootstrap;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 把模块依赖方向做成可执行检查。导入时必须看到各模块的类，避免规则因为扫不到代码而空过。
 */
class ArchitectureTest {

    /** 产品语义不能反向依赖执行层或接入层。 */
    private static final ArchRule SEMANTIC_MUST_NOT_DEPEND_ON_RUNTIME_OR_ACCESS =
            noClasses().that().resideInAPackage("..semantic..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..runtime..", "..access..");

    /** 运行层只能碰语义契约，不能碰语义的实现包。 */
    private static final ArchRule RUNTIME_ONLY_DEPENDS_ON_SEMANTIC_CONTRACT =
            noClasses().that().resideInAPackage("..runtime..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..semantic.application..", "..semantic.infrastructure..");

    /** 状态层不认识产品语义、执行和接入。 */
    private static final ArchRule STATE_MUST_NOT_DEPEND_ON_BUSINESS =
            noClasses().that().resideInAPackage("..state..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..semantic..", "..runtime..", "..access..");

    /** 向量层不认识状态、产品语义、执行和接入。 */
    private static final ArchRule VECTOR_MUST_NOT_DEPEND_ON_BUSINESS =
            noClasses().that().resideInAPackage("..vector..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..state..", "..semantic..", "..runtime..", "..access..", "..admin..");

    /** 对象存储不认识状态、产品语义、执行、接入和审计。 */
    private static final ArchRule OBJECT_STORE_MUST_NOT_DEPEND_ON_BUSINESS =
            noClasses().that().resideInAPackage("..objectstore..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "..state..",
                            "..semantic..",
                            "..runtime..",
                            "..access..",
                            "..admin..",
                            "..audit..",
                            "..vector..");

    /** 接入层不能直接引用 Harness 类型。 */
    private static final ArchRule ACCESS_MUST_NOT_TOUCH_HARNESS =
            noClasses().that().resideInAPackage("..access..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..harness..");

    /** 控制器不能直接访问持久化包。 */
    private static final ArchRule CONTROLLERS_MUST_NOT_ACCESS_REPOSITORIES =
            noClasses().that().resideInAPackage("..access.api.controller..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..infrastructure.persistence..");

    /** 确认规则作用在真实类上，并且六条边界都成立。 */
    @Test
    void moduleBoundariesHold() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.zhu");
        assertThat(classes.stream().map(JavaClass::getName))
                .contains(
                        "com.zhu.semantic.contract.ProductSemanticProvider",
                        "com.zhu.runtime.api.AgentExecutor",
                        "com.zhu.state.config.DistributedStoreConfig",
                        "com.zhu.state.config.StateMapperConfig",
                        "com.zhu.vector.api.VectorStore",
                        "com.zhu.vector.config.VectorConfig",
                        "com.zhu.objectstore.api.ObjectStore",
                        "com.zhu.objectstore.infrastructure.LocalObjectStore",
                        "com.zhu.objectstore.infrastructure.S3ObjectStore",
                        "com.zhu.access.api.controller.LivenessController");

        SEMANTIC_MUST_NOT_DEPEND_ON_RUNTIME_OR_ACCESS.check(classes);
        RUNTIME_ONLY_DEPENDS_ON_SEMANTIC_CONTRACT.check(classes);
        STATE_MUST_NOT_DEPEND_ON_BUSINESS.check(classes);
        VECTOR_MUST_NOT_DEPEND_ON_BUSINESS.check(classes);
        OBJECT_STORE_MUST_NOT_DEPEND_ON_BUSINESS.check(classes);
        ACCESS_MUST_NOT_TOUCH_HARNESS.check(classes);
        CONTROLLERS_MUST_NOT_ACCESS_REPOSITORIES.check(classes);
    }
}
