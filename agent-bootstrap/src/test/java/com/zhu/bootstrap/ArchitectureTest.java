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

    /**
     * 运行层不得依赖产品实现包（{@code com.zhu.product..}，勿与 {@code semantic.contract.product} 混淆）。
     * 产品经 {@code ResidentAgentBinding} 注册表挂入，由 bootstrap 扫进 classpath。
     */
    private static final ArchRule RUNTIME_MUST_NOT_DEPEND_ON_PRODUCT =
            noClasses().that().resideInAPackage("..runtime..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.zhu.product..");

    /**
     * 产品模块不得依赖接入层。HTTP / 身份 / 额度留在 access；产品只提供 Binding / 语义实现 / 业务工具。
     */
    private static final ArchRule PRODUCT_MUST_NOT_DEPEND_ON_ACCESS =
            noClasses().that().resideInAPackage("com.zhu.product..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..access..");

    /** 语义共享层不得依赖具体产品实现（S1：契约 + api + 共享存储）。 */
    private static final ArchRule SEMANTIC_MUST_NOT_DEPEND_ON_PRODUCT =
            noClasses().that().resideInAPackage("..semantic..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.zhu.product..");

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

    /**
     * access 全包不得依赖任何 {@code io.agentscope}（防腐③+⑤：Channel/PendingAsk/Wakeup 已下沉）。
     */
    private static final ArchRule ACCESS_MUST_NOT_TOUCH_AGENTSCOPE =
            noClasses()
                    .that()
                    .resideInAPackage("..access..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("io.agentscope..");

    /**
     * access 不得依赖 runtime.infrastructure（防腐④：Wakeup 经 {@code WakeupPort}，与 PendingAskFacade 对称）。
     */
    private static final ArchRule ACCESS_MUST_NOT_TOUCH_RUNTIME_INFRASTRUCTURE =
            noClasses()
                    .that()
                    .resideInAPackage("..access..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..runtime.infrastructure..");

    /** 控制器不能直接访问持久化包。 */
    private static final ArchRule CONTROLLERS_MUST_NOT_ACCESS_REPOSITORIES =
            noClasses().that().resideInAPackage("..access.api.controller..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..infrastructure.persistence..");

    /** 确认规则作用在真实类上，并且边界都成立。 */
    @Test
    void moduleBoundariesHold() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.zhu");
        assertThat(classes.stream().map(JavaClass::getName))
                .contains(
                        "com.zhu.semantic.contract.product.ProductSemanticProvider",
                        "com.zhu.runtime.api.agent.AgentExecutor",
                        "com.zhu.runtime.infrastructure.gateway.ChannelGateway",
                        "com.zhu.runtime.api.approval.PendingAskFacade",
                        "com.zhu.runtime.api.wakeup.WakeupPort",
                        "com.zhu.runtime.infrastructure.agent.ResidentAgentBinding",
                        "com.zhu.product.companion.binding.CompanionResidentAgentBinding",
                        "com.zhu.state.config.DistributedStoreConfig",
                        "com.zhu.state.config.StateMapperConfig",
                        "com.zhu.vector.api.VectorStore",
                        "com.zhu.vector.config.VectorConfig",
                        "com.zhu.objectstore.api.ObjectStore",
                        "com.zhu.objectstore.infrastructure.LocalObjectStore",
                        "com.zhu.objectstore.infrastructure.S3ObjectStore",
                        "com.zhu.access.api.controller.health.LivenessController");

        SEMANTIC_MUST_NOT_DEPEND_ON_RUNTIME_OR_ACCESS.check(classes);
        RUNTIME_ONLY_DEPENDS_ON_SEMANTIC_CONTRACT.check(classes);
        RUNTIME_MUST_NOT_DEPEND_ON_PRODUCT.check(classes);
        PRODUCT_MUST_NOT_DEPEND_ON_ACCESS.check(classes);
        SEMANTIC_MUST_NOT_DEPEND_ON_PRODUCT.check(classes);
        STATE_MUST_NOT_DEPEND_ON_BUSINESS.check(classes);
        VECTOR_MUST_NOT_DEPEND_ON_BUSINESS.check(classes);
        OBJECT_STORE_MUST_NOT_DEPEND_ON_BUSINESS.check(classes);
        ACCESS_MUST_NOT_TOUCH_AGENTSCOPE.check(classes);
        ACCESS_MUST_NOT_TOUCH_RUNTIME_INFRASTRUCTURE.check(classes);
        CONTROLLERS_MUST_NOT_ACCESS_REPOSITORIES.check(classes);
    }
}
