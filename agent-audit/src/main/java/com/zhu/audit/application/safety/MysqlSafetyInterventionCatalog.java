package com.zhu.audit.application.safety;

import com.zhu.audit.application.support.AuditQueryLimits;

import com.zhu.audit.api.safety.SafetyInterventionCatalog;
import com.zhu.audit.api.safety.SafetyInterventionSummary;
import com.zhu.audit.infrastructure.persistence.safety.SafetyInterventionMapper;
import com.zhu.audit.infrastructure.persistence.entity.safety.SafetyIntervention;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 查看安全干预。只在存储开关打开时创建。不写新行。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlSafetyInterventionCatalog implements SafetyInterventionCatalog {

    private final SafetyInterventionMapper interventions;

    /** 查询用 Mapper。条数在这里收口。 */
    public MysqlSafetyInterventionCatalog(SafetyInterventionMapper interventions) {
        this.interventions = interventions;
    }

    /** 条数和审计查询用同一套上限。空结果返回空列表。 */
    @Override
    public List<SafetyInterventionSummary> recent(int limit) {
        List<SafetyIntervention> rows = interventions.recent(AuditQueryLimits.normalize(limit));
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<SafetyInterventionSummary> entries = new ArrayList<>(rows.size());
        for (SafetyIntervention row : rows) {
            entries.add(new SafetyInterventionSummary(
                    row.getTenantId(),
                    row.getUserId(),
                    row.getSessionId(),
                    row.getRiskLevel(),
                    row.getSource(),
                    row.getSummary()));
        }
        return List.copyOf(entries);
    }
}
