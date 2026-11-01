package com.zhu.bootstrap;

import com.zhu.audit.application.MysqlEvalRunCatalog;
import com.zhu.audit.infrastructure.persistence.EvalCaseMapper;
import com.zhu.audit.infrastructure.persistence.EvalDatasetMapper;
import com.zhu.audit.infrastructure.persistence.EvalRunMapper;
import com.zhu.audit.infrastructure.persistence.entity.EvalDataset;
import com.zhu.audit.infrastructure.persistence.entity.EvalRun;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评测执行登记条数后手工打分。不连接 MySQL，也不调用模型。
 */
class EvalRunCatalogTest {

    /** 没有数据集或没有用例时不插入。有用例时状态是 RUNNING，通过数是 0。 */
    @Test
    void startRecordsCaseCountWithoutScoring() {
        EvalDatasetMapper datasets = mock(EvalDatasetMapper.class);
        EvalCaseMapper cases = mock(EvalCaseMapper.class);
        EvalRunMapper runs = mock(EvalRunMapper.class);
        MysqlEvalRunCatalog catalog = new MysqlEvalRunCatalog(datasets, cases, runs);

        assertThatThrownBy(() -> catalog.start("set-a"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(runs, never()).insert(any(EvalRun.class));

        when(datasets.findVersion("set-a", "1.0.0")).thenReturn(new EvalDataset());
        when(cases.countVersion("set-a", "1.0.0")).thenReturn(0L);
        assertThatThrownBy(() -> catalog.start("set-a"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);

        when(cases.countVersion("set-a", "1.0.0")).thenReturn(2L);
        assertThat(catalog.start("set-a").status()).isEqualTo("RUNNING");
        ArgumentCaptor<EvalRun> saved = ArgumentCaptor.forClass(EvalRun.class);
        verify(runs).insert(saved.capture());
        EvalRun row = saved.getValue();
        assertThat(row.getTotalCases()).isEqualTo(2);
        assertThat(row.getPassedCases()).isZero();
        assertThat(row.getStatus()).isEqualTo("RUNNING");
        assertThat(row.getEvalRunId()).isNotBlank();
    }

    /** 只给还在跑的执行打分。没有、已经完成或通过数过大按找不到、冲突或无效。 */
    @Test
    void scoreUpdatesRunningOnly() {
        EvalDatasetMapper datasets = mock(EvalDatasetMapper.class);
        EvalCaseMapper cases = mock(EvalCaseMapper.class);
        EvalRunMapper runs = mock(EvalRunMapper.class);
        MysqlEvalRunCatalog catalog = new MysqlEvalRunCatalog(datasets, cases, runs);

        assertThatThrownBy(() -> catalog.score("run-1", 1))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(runs, never()).scoreRunning(eq("run-1"), anyInt(), any(), any());

        EvalRun running = new EvalRun();
        running.setEvalRunId("run-1");
        running.setDatasetId("set-a");
        running.setDatasetVersion("1.0.0");
        running.setTotalCases(2);
        running.setPassedCases(0);
        running.setStatus("RUNNING");
        when(runs.findByEvalRunId("run-1")).thenReturn(running);
        assertThatThrownBy(() -> catalog.score("run-1", 3))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);

        when(runs.scoreRunning(eq("run-1"), eq(1), any(), any())).thenReturn(1);
        assertThat(catalog.score("run-1", 1)).satisfies(saved -> {
            assertThat(saved.evalRunId()).isEqualTo("run-1");
            assertThat(saved.passedCases()).isEqualTo(1);
            assertThat(saved.totalCases()).isEqualTo(2);
            assertThat(saved.status()).isEqualTo("SUCCEEDED");
        });

        running.setStatus("SUCCEEDED");
        assertThatThrownBy(() -> catalog.score("run-1", 1))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
        verify(runs).scoreRunning(eq("run-1"), eq(1), any(), any());
    }
}
