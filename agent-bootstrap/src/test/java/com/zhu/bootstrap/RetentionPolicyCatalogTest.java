package com.zhu.bootstrap;

import com.zhu.audit.application.MysqlRetentionPolicyCatalog;
import com.zhu.audit.infrastructure.persistence.DataRetentionPolicyMapper;
import com.zhu.audit.infrastructure.persistence.entity.DataRetentionPolicy;
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
 * 数据保留策略只登记目录。不连接 MySQL，也不执行清理。
 */
class RetentionPolicyCatalogTest {

    /** 空白范围按全局，空白动作按 ARCHIVE。同一类型和范围再写一次按冲突处理。 */
    @Test
    void registerInsertsDefaultAction() {
        DataRetentionPolicyMapper policies = mock(DataRetentionPolicyMapper.class);
        MysqlRetentionPolicyCatalog catalog = new MysqlRetentionPolicyCatalog(policies);

        assertThat(catalog.register("SESSION", " ", 30, " ")).satisfies(saved -> {
            assertThat(saved.scope()).isEqualTo("*");
            assertThat(saved.action()).isEqualTo("ARCHIVE");
            assertThat(saved.retainDays()).isEqualTo(30);
        });
        verify(policies).insert(any(DataRetentionPolicy.class));

        when(policies.insert(any(DataRetentionPolicy.class))).thenThrow(new DataIntegrityViolationException("dup"));
        assertThatThrownBy(() -> catalog.register("SESSION", "*", 30, null))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }
}
