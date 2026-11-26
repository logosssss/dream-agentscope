package com.zhu.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.zhu.admin.nacos.NacosAdminAllowlist;
import org.junit.jupiter.api.Test;

/**
 * 管理进程的 Nacos 白名单要能解析，并且带上 cron 时区键。
 *
 * <p>这条测的作用有一半在「能解析」：白名单以前没有用例，写坏一个缩进
 * 只会在进程启动时才炸（{@code NacosAdminAllowlist} 是静态加载，坏了连构造都过不去）。
 * 另一半在业务：管理端登记调度时按这个时区算下次触发，和用户进程必须同一口径。
 */
class NacosAdminAllowlistTest {

    /** 白名单必须能加载出两段。加载失败会抛 ExceptionInInitializerError。 */
    @Test
    void allowlistLoadsBothSections() {
        assertThat(NacosAdminAllowlist.infraExact())
                .contains("spring.datasource.url");
        assertThat(NacosAdminAllowlist.businessPrefixes()).isEmpty();
    }

    /** 业务键当前只有 cron 时区一项，少了它管理端就按进程默认时区登记。 */
    @Test
    void businessExactCarriesTheScheduleZone() {
        assertThat(NacosAdminAllowlist.businessExact()).contains("agentscope.schedule.zone");
    }
}
