package com.zhu.bootstrap;

import com.zhu.audit.application.MysqlRetentionPolicyCatalog;
import com.zhu.audit.infrastructure.persistence.DataRetentionPolicyMapper;
import com.zhu.audit.infrastructure.persistence.entity.DataRetentionPolicy;
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
 * 数据保留策略登记、停用、恢复和列表。不连接 MySQL，也不执行清理。
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
            assertThat(saved.enabled()).isTrue();
        });
        verify(policies).insert(any(DataRetentionPolicy.class));

        when(policies.insert(any(DataRetentionPolicy.class))).thenThrow(new DataIntegrityViolationException("dup"));
        assertThatThrownBy(() -> catalog.register("SESSION", "*", 30, null))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    /** 停用只动仍启用的行；恢复只动已停用的行。空白范围按全局。 */
    @Test
    void disableAndRestoreToggleEnabled() {
        DataRetentionPolicyMapper policies = mock(DataRetentionPolicyMapper.class);
        MysqlRetentionPolicyCatalog catalog = new MysqlRetentionPolicyCatalog(policies);

        DataRetentionPolicy enabled = new DataRetentionPolicy();
        enabled.setDataType("SESSION");
        enabled.setScope("*");
        enabled.setRetainDays(30);
        enabled.setAction("ARCHIVE");
        enabled.setEnabled(1);
        when(policies.findByKey("SESSION", "*")).thenReturn(enabled);
        when(policies.disableEnabled("SESSION", "*")).thenReturn(1);
        assertThat(catalog.disable("SESSION", " ")).satisfies(saved -> {
            assertThat(saved.enabled()).isFalse();
            assertThat(saved.scope()).isEqualTo("*");
        });

        DataRetentionPolicy disabled = new DataRetentionPolicy();
        disabled.setDataType("SESSION");
        disabled.setScope("*");
        disabled.setRetainDays(30);
        disabled.setAction("ARCHIVE");
        disabled.setEnabled(0);
        when(policies.findByKey("SESSION", "*")).thenReturn(disabled);
        when(policies.restoreDisabled("SESSION", "*")).thenReturn(1);
        assertThat(catalog.restore("SESSION", null).enabled()).isTrue();

        when(policies.findByKey("SESSION", "*")).thenReturn(disabled);
        assertThatThrownBy(() -> catalog.disable("SESSION", "*"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);

        when(policies.findByKey("MESSAGE", "t1")).thenReturn(null);
        assertThatThrownBy(() -> catalog.restore("MESSAGE", "t1"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    /** 列表含停用。空结果原样返回。不执行清理。 */
    @Test
    void recentListsRegisteredPolicies() {
        DataRetentionPolicyMapper policies = mock(DataRetentionPolicyMapper.class);
        MysqlRetentionPolicyCatalog catalog = new MysqlRetentionPolicyCatalog(policies);
        when(policies.recent(50)).thenReturn(List.of());
        assertThat(catalog.recent(50)).isEmpty();

        DataRetentionPolicy disabled = new DataRetentionPolicy();
        disabled.setDataType("SESSION");
        disabled.setScope("*");
        disabled.setRetainDays(30);
        disabled.setAction("ARCHIVE");
        disabled.setEnabled(0);
        when(policies.recent(50)).thenReturn(List.of(disabled));
        assertThat(catalog.recent(0)).singleElement().satisfies(saved -> {
            assertThat(saved.dataType()).isEqualTo("SESSION");
            assertThat(saved.scope()).isEqualTo("*");
            assertThat(saved.retainDays()).isEqualTo(30);
            assertThat(saved.action()).isEqualTo("ARCHIVE");
            assertThat(saved.enabled()).isFalse();
        });
    }
}
