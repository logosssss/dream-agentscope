package com.zhu.bootstrap;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
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
     * 运行层只能碰产品契约（{@code com.zhu.product.api}），不得依赖任何具体产品实现。
     * 契约是 runtime 与产品的唯一接缝；具体产品（{@code com.zhu.product.companion..} 等）由 bootstrap 扫进 classpath。
     */
    private static final ArchRule RUNTIME_MAY_ONLY_SEE_PRODUCT_API =
            noClasses().that().resideInAPackage("..runtime..")
                    .should().dependOnClassesThat(JavaClass.Predicates.resideInAPackage("com.zhu.product..")
                            .and(JavaClass.Predicates.resideOutsideOfPackage("com.zhu.product.api..")));

    /**
     * 产品模块不得依赖接入层包。文档 HTTP 入口可以随产品声明搬走，但仍不能引用 {@code com.zhu.access}。
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

    /**
     * 产品语义只能写在产品包里。多个 ProductModule 可以并存，但单点注入
     * ProductSemanticProvider 必须落到 {@code @Primary} 的 main 产品，不能再另写一个不实现 ProductModule 的语义 Bean。
     *
     * <p>测试替身（{@code com.zhu.testsupport..}）不算产品实现：它只在测试类路径上，进程里拿不到。
     */
    private static final ArchRule PRODUCT_SEMANTIC_IMPLEMENTED_ONLY_IN_PRODUCTS =
            classes().that().implement("com.zhu.semantic.contract.product.ProductSemanticProvider")
                    .and().resideOutsideOfPackage("com.zhu.bootstrap..")
                    .and().resideOutsideOfPackage("com.zhu.testsupport..")
                    .should().resideInAPackage("com.zhu.product..");

    /** 状态层不认识产品语义、执行、接入和缓存。 */
    private static final ArchRule STATE_MUST_NOT_DEPEND_ON_BUSINESS =
            noClasses().that().resideInAPackage("..state..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..semantic..", "..runtime..", "..access..", "com.zhu.cache..");

    /** 向量层不认识状态、产品语义、执行、接入和缓存。 */
    private static final ArchRule VECTOR_MUST_NOT_DEPEND_ON_BUSINESS =
            noClasses().that().resideInAPackage("..vector..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "..state..",
                            "..semantic..",
                            "..runtime..",
                            "..access..",
                            "..admin..",
                            "com.zhu.cache..");

    /** 对象存储不认识状态、产品语义、执行、接入和管理进程与缓存。 */
    private static final ArchRule OBJECT_STORE_MUST_NOT_DEPEND_ON_BUSINESS =
            noClasses().that().resideInAPackage("..objectstore..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "..state..",
                            "..semantic..",
                            "..runtime..",
                            "..access..",
                            "..admin..",
                            "..vector..",
                            "com.zhu.cache..");

    /** 语义共享层不得依赖缓存层（只留契约 / api / 共享存储）。 */
    private static final ArchRule SEMANTIC_MUST_NOT_DEPEND_ON_CACHE =
            noClasses().that().resideInAPackage("..semantic..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.zhu.cache..");

    /**
     * 业务侧不得依赖 Redis 族实现包；只许依赖 {@code agent-cache-api}。
     * admin / bootstrap 装配可引入实现；access/runtime/product 源码不碰 redis 包。
     */
    private static final ArchRule BUSINESS_MUST_NOT_DEPEND_ON_CACHE_REDIS =
            noClasses()
                    .that()
                    .resideInAnyPackage("..access..", "..runtime..", "com.zhu.product..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("com.zhu.cache.redis..");

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

    /**
     * readmodel 是共享数据形状，不得反向依赖任何业务模块、组件实现或中间件客户端。
     * 它只允许 mybatis-plus 与 agent-common 的纯类型。
     */
    private static final ArchRule READMODEL_MUST_NOT_DEPEND_ON_BUSINESS =
            noClasses()
                    .that()
                    .resideInAPackage("com.zhu.readmodel..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "..access..",
                            "..admin..",
                            "..runtime..",
                            "com.zhu.product..",
                            "..state..",
                            "..vector..",
                            "..objectstore..",
                            "com.zhu.cache..",
                            "io.agentscope..");

    /**
     * ops 是 access 与 admin 共用的运营行为（目录、记录、闸门、额度）。
     * 它只能看见 readmodel 的数据形状、cache-api 的抽象与 common 的纯类型，
     * 不得反向依赖任何一个进程模块，也不得碰 agentscope 或 Redis 实现。
     */
    private static final ArchRule OPS_MUST_NOT_DEPEND_ON_BUSINESS =
            noClasses()
                    .that()
                    .resideInAPackage("com.zhu.ops..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "..access..",
                            "..admin..",
                            "..runtime..",
                            "com.zhu.product..",
                            "..state..",
                            "..vector..",
                            "..objectstore..",
                            "com.zhu.cache.redis..",
                            "io.agentscope..");

    /** 确认规则作用在真实类上，并且边界都成立。 */
    @Test
    void moduleBoundariesHold() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.zhu");
        assertThat(classes.stream().map(JavaClass::getName))
                .contains(
                        "com.zhu.readmodel.config.ReadModelMapperConfig",
                        "com.zhu.readmodel.persistence.usage.TokenUsageMapper",
                        "com.zhu.readmodel.entity.tenant.TenantRecord",
                        "com.zhu.semantic.contract.product.ProductSemanticProvider",
                        "com.zhu.runtime.api.agent.AgentExecutor",
                        "com.zhu.runtime.infrastructure.gateway.ChannelGateway",
                        "com.zhu.runtime.api.approval.PendingAskFacade",
                        "com.zhu.runtime.api.wakeup.WakeupPort",
                        "com.zhu.ops.tenant.MysqlTenantUserCatalog",
                        "com.zhu.ops.deletion.DeletionRequestCatalog",
                        "com.zhu.ops.support.QueryLimits",
                        "com.zhu.product.api.ProductModule",
                    "com.zhu.runtime.infrastructure.agent.ProductModuleRegistry",
                        "com.zhu.product.companion.binding.CompanionResidentAgentBinding",
                        "com.zhu.state.config.DistributedStoreConfig",
                        "com.zhu.state.config.StateMapperConfig",
                        "com.zhu.vector.api.VectorStore",
                        "com.zhu.vector.config.VectorConfig",
                        "com.zhu.objectstore.api.ObjectStore",
                        "com.zhu.objectstore.infrastructure.LocalObjectStore",
                        "com.zhu.objectstore.infrastructure.S3ObjectStore",
                        "com.zhu.cache.api.CacheFacade",
                        "com.zhu.cache.redis.config.RedisCacheAutoConfiguration",
                        "com.zhu.access.api.controller.health.LivenessController");

        SEMANTIC_MUST_NOT_DEPEND_ON_RUNTIME_OR_ACCESS.check(classes);
        RUNTIME_ONLY_DEPENDS_ON_SEMANTIC_CONTRACT.check(classes);
        RUNTIME_MAY_ONLY_SEE_PRODUCT_API.check(classes);
        PRODUCT_MUST_NOT_DEPEND_ON_ACCESS.check(classes);
        SEMANTIC_MUST_NOT_DEPEND_ON_PRODUCT.check(classes);
        PRODUCT_SEMANTIC_IMPLEMENTED_ONLY_IN_PRODUCTS.check(classes);
        SEMANTIC_MUST_NOT_DEPEND_ON_CACHE.check(classes);
        BUSINESS_MUST_NOT_DEPEND_ON_CACHE_REDIS.check(classes);
        STATE_MUST_NOT_DEPEND_ON_BUSINESS.check(classes);
        VECTOR_MUST_NOT_DEPEND_ON_BUSINESS.check(classes);
        OBJECT_STORE_MUST_NOT_DEPEND_ON_BUSINESS.check(classes);
        ACCESS_MUST_NOT_TOUCH_AGENTSCOPE.check(classes);
        ACCESS_MUST_NOT_TOUCH_RUNTIME_INFRASTRUCTURE.check(classes);
        CONTROLLERS_MUST_NOT_ACCESS_REPOSITORIES.check(classes);
        READMODEL_MUST_NOT_DEPEND_ON_BUSINESS.check(classes);
        OPS_MUST_NOT_DEPEND_ON_BUSINESS.check(classes);

    }

    /**
     * readmodel 只放数据形状与查询，不得出现业务服务、事务边界或领域规则。
     * 共享表的写入（如额度上限、模型配置）允许留在 Mapper，但只能是一条语句，不含判断。
     */
    @Test
    void readModelHoldsNoBusinessLogic() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.zhu.readmodel");
        List<String> offenders = classes.stream()
                .filter(c -> !c.getPackageName().equals("com.zhu.readmodel.config"))
                .filter(c -> c.isAnnotatedWith("org.springframework.stereotype.Service")
                        || c.isAnnotatedWith("org.springframework.stereotype.Component")
                        || c.isAnnotatedWith("org.springframework.transaction.annotation.Transactional"))
                .map(JavaClass::getName)
                .toList();
        assertThat(offenders).as("readmodel must not carry business services").isEmpty();
    }
}
