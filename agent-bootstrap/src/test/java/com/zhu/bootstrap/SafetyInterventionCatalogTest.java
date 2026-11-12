package com.zhu.bootstrap;

import com.zhu.audit.application.safety.MysqlSafetyInterventionCatalog;
import com.zhu.audit.infrastructure.persistence.safety.SafetyInterventionMapper;
import com.zhu.audit.infrastructure.persistence.entity.safety.SafetyIntervention;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 干预列表只读目录。不连接 MySQL，也不带用户原文。
 */
class SafetyInterventionCatalogTest {

    /** 空列表原样返回。有行时摘要仍是短句。 */
    @Test
    void recentReturnsRowsWithoutUserText() {
        SafetyInterventionMapper interventions = mock(SafetyInterventionMapper.class);
        MysqlSafetyInterventionCatalog catalog = new MysqlSafetyInterventionCatalog(interventions);
        when(interventions.recent(50)).thenReturn(List.of());
        assertThat(catalog.recent(0)).isEmpty();

        SafetyIntervention row = new SafetyIntervention();
        row.setTenantId("t");
        row.setUserId("u");
        row.setSessionId("s");
        row.setRiskLevel("ELEVATED");
        row.setSource("SEMANTIC");
        row.setSummary("semantic elevated");
        when(interventions.recent(50)).thenReturn(List.of(row));

        assertThat(catalog.recent(50)).singleElement().satisfies(saved -> {
            assertThat(saved.tenantId()).isEqualTo("t");
            assertThat(saved.userId()).isEqualTo("u");
            assertThat(saved.riskLevel()).isEqualTo("ELEVATED");
            assertThat(saved.summary()).isEqualTo("semantic elevated");
            assertThat(saved.summary()).doesNotContain("自杀");
        });
    }
}
