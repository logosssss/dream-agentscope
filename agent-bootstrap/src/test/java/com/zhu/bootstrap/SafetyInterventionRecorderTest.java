package com.zhu.bootstrap;

import com.zhu.audit.api.SafetyInterventionRecorder;
import com.zhu.audit.application.MysqlSafetyInterventionRecorder;
import com.zhu.audit.infrastructure.persistence.SafetyInterventionMapper;
import com.zhu.audit.infrastructure.persistence.entity.SafetyIntervention;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 干预只接受升高和危机。摘要不含用户原文。不连接 MySQL。
 */
class SafetyInterventionRecorderTest {

    /** 空白租户和会话写成空串。普通档位不插入。 */
    @Test
    void recordsElevatedWithoutUserText() {
        SafetyInterventionMapper interventions = mock(SafetyInterventionMapper.class);
        MysqlSafetyInterventionRecorder recorder = new MysqlSafetyInterventionRecorder(interventions);

        recorder.record(" ", "u", " ", SafetyInterventionRecorder.LEVEL_ELEVATED,
                SafetyInterventionRecorder.SOURCE_SEMANTIC, "semantic elevated");
        ArgumentCaptor<SafetyIntervention> saved = ArgumentCaptor.forClass(SafetyIntervention.class);
        verify(interventions).insert(saved.capture());
        SafetyIntervention row = saved.getValue();
        assertThat(row.getTenantId()).isEqualTo("");
        assertThat(row.getUserId()).isEqualTo("u");
        assertThat(row.getSessionId()).isEqualTo("");
        assertThat(row.getRiskLevel()).isEqualTo("ELEVATED");
        assertThat(row.getSource()).isEqualTo("SEMANTIC");
        assertThat(row.getSummary()).isEqualTo("semantic elevated");
        assertThat(row.getSummary()).doesNotContain("自杀");

        assertThatThrownBy(() -> recorder.record("t", "u", "s", "NORMAL",
                        SafetyInterventionRecorder.SOURCE_KEYWORD, "keyword"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        verify(interventions, times(1)).insert(saved.capture());
    }
}
