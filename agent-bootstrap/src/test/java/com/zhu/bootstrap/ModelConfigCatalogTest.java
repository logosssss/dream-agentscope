package com.zhu.bootstrap;

import com.zhu.audit.application.MysqlModelConfigCatalog;
import com.zhu.audit.infrastructure.persistence.ModelConfigMapper;
import com.zhu.audit.infrastructure.persistence.entity.ModelConfig;
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
 * 模型配置登记后可供对话选用。不连接 MySQL，也不解析参数。
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
}
