package com.zhu.bootstrap;

import com.zhu.audit.application.eval.MysqlEvalDatasetCatalog;
import com.zhu.audit.infrastructure.persistence.eval.EvalDatasetMapper;
import com.zhu.audit.infrastructure.persistence.entity.eval.EvalDataset;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评测数据集登记、停用、恢复和列表。不连接 MySQL，也不写用例。
 */
class EvalDatasetCatalogTest {

    /** 新数据集版本是 1.0.0。同一标识再登记一次按冲突处理。 */
    @Test
    void registerInsertsVersionOne() {
        EvalDatasetMapper datasets = mock(EvalDatasetMapper.class);
        MysqlEvalDatasetCatalog catalog = new MysqlEvalDatasetCatalog(datasets);

        assertThat(catalog.register("set-a", "示例", " ")).satisfies(saved -> {
            assertThat(saved.version()).isEqualTo("1.0.0");
            assertThat(saved.description()).isNull();
            assertThat(saved.enabled()).isTrue();
        });
        verify(datasets).insert(any(EvalDataset.class));

        when(datasets.insert(any(EvalDataset.class))).thenThrow(new DataIntegrityViolationException("dup"));
        assertThatThrownBy(() -> catalog.register("set-a", "示例", null))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    /** 停用只动仍启用的 1.0.0；恢复只动已停用的行。 */
    @Test
    void disableAndRestoreToggleEnabled() {
        EvalDatasetMapper datasets = mock(EvalDatasetMapper.class);
        MysqlEvalDatasetCatalog catalog = new MysqlEvalDatasetCatalog(datasets);

        EvalDataset enabled = new EvalDataset();
        enabled.setDatasetId("set-a");
        enabled.setName("示例");
        enabled.setVersion("1.0.0");
        enabled.setEnabled(1);
        when(datasets.findVersion("set-a", "1.0.0")).thenReturn(enabled);
        when(datasets.disableEnabled("set-a", "1.0.0")).thenReturn(1);
        assertThat(catalog.disable("set-a").enabled()).isFalse();

        EvalDataset disabled = new EvalDataset();
        disabled.setDatasetId("set-a");
        disabled.setName("示例");
        disabled.setVersion("1.0.0");
        disabled.setEnabled(0);
        when(datasets.findVersion("set-a", "1.0.0")).thenReturn(disabled);
        when(datasets.restoreDisabled("set-a", "1.0.0")).thenReturn(1);
        assertThat(catalog.restore("set-a").enabled()).isTrue();

        when(datasets.findVersion("set-a", "1.0.0")).thenReturn(disabled);
        assertThatThrownBy(() -> catalog.disable("set-a"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);

        when(datasets.findVersion("missing", "1.0.0")).thenReturn(null);
        assertThatThrownBy(() -> catalog.restore("missing"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    /** 列表含停用。空结果原样返回。不带回用例。 */
    @Test
    void recentListsRegisteredDatasets() {
        EvalDatasetMapper datasets = mock(EvalDatasetMapper.class);
        MysqlEvalDatasetCatalog catalog = new MysqlEvalDatasetCatalog(datasets);
        when(datasets.recent(50)).thenReturn(List.of());
        assertThat(catalog.recent(50)).isEmpty();

        EvalDataset disabled = new EvalDataset();
        disabled.setDatasetId("set-a");
        disabled.setName("示例");
        disabled.setVersion("1.0.0");
        disabled.setDescription("短说明");
        disabled.setEnabled(0);
        when(datasets.recent(50)).thenReturn(List.of(disabled));
        assertThat(catalog.recent(0)).singleElement().satisfies(saved -> {
            assertThat(saved.datasetId()).isEqualTo("set-a");
            assertThat(saved.name()).isEqualTo("示例");
            assertThat(saved.version()).isEqualTo("1.0.0");
            assertThat(saved.description()).isEqualTo("短说明");
            assertThat(saved.enabled()).isFalse();
        });
    }
}
