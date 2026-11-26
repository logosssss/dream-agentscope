package com.zhu.admin;

import com.zhu.admin.api.event.AuditQuery;
import com.zhu.ops.quota.TenantQuotaCatalog;
import com.zhu.ops.tenant.TenantCatalog;
import com.zhu.ops.tenant.TenantUserCatalog;
import com.zhu.readmodel.persistence.event.AgentEventAuditMapper;
import com.zhu.readmodel.persistence.run.AgentRunMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MySQL 打开时，管理进程也必须能拿到 {@code agent-readmodel} 的 Mapper。
 *
 * <p>这条用例就是为「管理进程只扫 com.zhu.admin，却要注入 com.zhu.readmodel.persistence 的 Mapper」
 * 这个坑写的：一旦有人删掉 AdminApplication 上的 ReadModelMapperConfig 导入，这里会立刻红。
 * 不连真库——Mapper 是代理对象，构造容器不需要数据库可连通。
 */
@SpringBootTest(properties = {
        "agentscope.startup.dependencies.enabled=false",
        "agentscope.distributed.mysql.enabled=true",
        "agentscope.cache.redis.enabled=false",
        "spring.datasource.url=jdbc:mysql://127.0.0.1:1/agentscope?useUnicode=true&characterEncoding=utf8",
        "spring.datasource.username=probe",
        "spring.datasource.password=probe",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
class AdminMySqlWiringTest {

    @Autowired
    private ApplicationContext context;

    /** 共享表的 Mapper 由 agent-readmodel 提供，管理进程要能按类型拿到。 */
    @Test
    void readModelMappersAreVisibleToAdmin() {
        assertThat(context.getBeansOfType(AgentEventAuditMapper.class)).hasSize(1);
        assertThat(context.getBeansOfType(AgentRunMapper.class)).hasSize(1);
        assertThat(context.getBeansOfType(AuditQuery.class)).hasSize(1);
    }

    /** 租户、租户用户和额度目录在 ops，删掉 AdminApplication 上的导入就会缺 Bean。 */
    @Test
    void opsCatalogsAreImported() {
        assertThat(context.getBeansOfType(TenantCatalog.class)).hasSize(1);
        assertThat(context.getBeansOfType(TenantUserCatalog.class)).hasSize(1);
        assertThat(context.getBeansOfType(TenantQuotaCatalog.class)).hasSize(1);
    }
}
