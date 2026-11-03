package com.zhu.bootstrap;

import com.zhu.audit.application.MysqlModelConfigCatalog;
import com.zhu.audit.infrastructure.persistence.ModelConfigMapper;
import com.zhu.audit.infrastructure.persistence.entity.ModelConfig;
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
 * 模型配置登记后可供对话选用。列表含停用。不连接 MySQL，也不解析参数。
 */
class ModelConfigCatalogTest {

    /** 空白范围按全局。同一标识再写一次按冲突处理。 */
    @Test
    void registerInsertsVersionOne() {
        ModelConfigMapper configs = mock(ModelConfigMapper.class);
        MysqlModelConfigCatalog catalog = new MysqlModelConfigCatalog(configs);

        assertThat(catalog.register("cfg-a", "qwen-plus", "dashscope", " ")).satisfies(saved -> {
            assertThat(saved.version()).isEqualTo("1.0.0");
            assertThat(saved.tenantScope()).isEqualTo("*");
        });
        verify(configs).insert(any(ModelConfig.class));

        when(configs.insert(any(ModelConfig.class))).thenThrow(new DataIntegrityViolationException("dup"));
        assertThatThrownBy(() -> catalog.register("cfg-a", "qwen-plus", "dashscope", null))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    /** 租户范围优先于全局。没有启用行时没有单独模型。 */
    @Test
    void resolvePrefersTenantScope() {
        ModelConfigMapper configs = mock(ModelConfigMapper.class);
        MysqlModelConfigCatalog catalog = new MysqlModelConfigCatalog(configs);

        assertThat(catalog.resolveModel("t")).isNull();

        ModelConfig tenant = new ModelConfig();
        tenant.setProvider("dashscope");
        tenant.setModel("qwen-max");
        when(configs.findEnabled("t")).thenReturn(tenant);
        assertThat(catalog.resolveModel("t")).isEqualTo("dashscope:qwen-max");

        ModelConfig global = new ModelConfig();
        global.setProvider("dashscope");
        global.setModel("qwen-plus");
        when(configs.findEnabled(null)).thenReturn(global);
        assertThat(catalog.resolveModel(" ")).isEqualTo("dashscope:qwen-plus");
    }

    /** 列表含停用。空结果原样返回。 */
    @Test
    void recentListsRegisteredConfigs() {
        ModelConfigMapper configs = mock(ModelConfigMapper.class);
        MysqlModelConfigCatalog catalog = new MysqlModelConfigCatalog(configs);
        when(configs.recent(50)).thenReturn(List.of());
        assertThat(catalog.recent(50)).isEmpty();

        ModelConfig disabled = new ModelConfig();
        disabled.setConfigId("cfg-a");
        disabled.setModel("qwen-plus");
        disabled.setProvider("dashscope");
        disabled.setVersion("1.0.0");
        disabled.setTenantScope("*");
        disabled.setEnabled(0);
        when(configs.recent(50)).thenReturn(List.of(disabled));
        assertThat(catalog.recent(0)).singleElement().satisfies(saved -> {
            assertThat(saved.configId()).isEqualTo("cfg-a");
            assertThat(saved.tenantScope()).isEqualTo("*");
            assertThat(saved.version()).isEqualTo("1.0.0");
            assertThat(saved.enabled()).isFalse();
        });
    }

    /** 停用只打仍启用的 1.0.0。 */
    @Test
    void disableEnabledConfig() {
        ModelConfigMapper configs = mock(ModelConfigMapper.class);
        MysqlModelConfigCatalog catalog = new MysqlModelConfigCatalog(configs);
        ModelConfig existing = new ModelConfig();
        existing.setConfigId("cfg-a");
        existing.setModel("qwen-plus");
        existing.setProvider("dashscope");
        existing.setVersion("1.0.0");
        existing.setTenantScope("*");
        existing.setEnabled(1);
        when(configs.findByConfigId("cfg-a", "1.0.0")).thenReturn(existing);
        when(configs.disableEnabled("cfg-a", "1.0.0")).thenReturn(1);

        assertThat(catalog.disable("cfg-a")).satisfies(saved -> {
            assertThat(saved.configId()).isEqualTo("cfg-a");
            assertThat(saved.enabled()).isFalse();
        });
    }
}
