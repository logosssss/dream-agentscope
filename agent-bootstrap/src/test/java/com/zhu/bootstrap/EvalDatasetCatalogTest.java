package com.zhu.bootstrap;

import com.zhu.audit.application.MysqlEvalDatasetCatalog;
import com.zhu.audit.infrastructure.persistence.EvalDatasetMapper;
import com.zhu.audit.infrastructure.persistence.entity.EvalDataset;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评测数据集登记。不连接 MySQL，也不写用例。
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
        });
        verify(datasets).insert(any(EvalDataset.class));

        when(datasets.insert(any(EvalDataset.class))).thenThrow(new DataIntegrityViolationException("dup"));
        assertThatThrownBy(() -> catalog.register("set-a", "示例", null))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }
}
