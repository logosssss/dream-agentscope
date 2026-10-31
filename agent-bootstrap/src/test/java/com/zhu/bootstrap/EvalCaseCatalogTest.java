package com.zhu.bootstrap;

import com.zhu.audit.application.MysqlEvalCaseCatalog;
import com.zhu.audit.infrastructure.persistence.EvalCaseMapper;
import com.zhu.audit.infrastructure.persistence.EvalDatasetMapper;
import com.zhu.audit.infrastructure.persistence.entity.EvalCase;
import com.zhu.audit.infrastructure.persistence.entity.EvalDataset;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评测用例只挂在已登记的 1.0.0 上。不连接 MySQL，也不执行。
 */
class EvalCaseCatalogTest {

    /** 没有数据集时不插入。有数据集时写入输入，同一序号再写一次按冲突处理。 */
    @Test
    void addRequiresTheDataset() {
        EvalDatasetMapper datasets = mock(EvalDatasetMapper.class);
        EvalCaseMapper cases = mock(EvalCaseMapper.class);
        MysqlEvalCaseCatalog catalog = new MysqlEvalCaseCatalog(datasets, cases);

        assertThatThrownBy(() -> catalog.add("set-a", 1, "问", null))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(cases, never()).insert(any(EvalCase.class));

        when(datasets.findVersion("set-a", "1.0.0")).thenReturn(new EvalDataset());
        assertThat(catalog.add("set-a", 1, "问", " ").expectedText()).isNull();
        verify(cases).insert(any(EvalCase.class));

        when(cases.insert(any(EvalCase.class))).thenThrow(new DataIntegrityViolationException("dup"));
        assertThatThrownBy(() -> catalog.add("set-a", 1, "问", null))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }
}
