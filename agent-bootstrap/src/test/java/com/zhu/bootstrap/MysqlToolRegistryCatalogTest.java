package com.zhu.bootstrap;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.semantic.api.ToolRegistrySummary;
import com.zhu.semantic.application.MysqlToolRegistryCatalog;
import com.zhu.semantic.infrastructure.persistence.ToolRegistryMapper;
import com.zhu.semantic.infrastructure.persistence.entity.ToolRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工具目录登记、停用、恢复与列表。不连接 MySQL，也不注册 Toolkit。
 */
class MysqlToolRegistryCatalogTest {

    /** 登记写 1.0.0；空白模块与说明按空串回给调用方。 */
    @Test
    void registerWritesFixedVersion() {
        ToolRegistryMapper mapper = mock(ToolRegistryMapper.class);
        MysqlToolRegistryCatalog catalog = new MysqlToolRegistryCatalog(mapper);

        ToolRegistrySummary saved = catalog.register("echo_text", "回声", " ", null);

        ArgumentCaptor<ToolRegistry> row = ArgumentCaptor.forClass(ToolRegistry.class);
        verify(mapper).insert(row.capture());
        assertThat(row.getValue().getToolId()).isEqualTo("echo_text");
        assertThat(row.getValue().getName()).isEqualTo("回声");
        assertThat(row.getValue().getVersion()).isEqualTo("1.0.0");
        assertThat(row.getValue().getModule()).isNull();
        assertThat(row.getValue().getDescription()).isNull();
        assertThat(row.getValue().getEnabled()).isEqualTo(1);
        assertThat(saved).isEqualTo(new ToolRegistrySummary("echo_text", "回声", "1.0.0", "", "", true));
    }

    /** 同一标识再登记按冲突处理。 */
    @Test
    void duplicateToolIdIsConflict() {
        ToolRegistryMapper mapper = mock(ToolRegistryMapper.class);
        when(mapper.insert(any(ToolRegistry.class))).thenThrow(new DuplicateKeyException("dup"));
        MysqlToolRegistryCatalog catalog = new MysqlToolRegistryCatalog(mapper);

        assertThatThrownBy(() -> catalog.register("echo_text", "回声", null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    /** 列表含停用，空模块说明变成空串；enabled 空按否。 */
    @Test
    void recentMapsNullFields() {
        ToolRegistry row = new ToolRegistry();
        row.setToolId("ping");
        row.setName("探测");
        row.setVersion("1.0.0");
        row.setEnabled(1);
        ToolRegistryMapper mapper = mock(ToolRegistryMapper.class);
        when(mapper.recent(50)).thenReturn(List.of(row));

        assertThat(new MysqlToolRegistryCatalog(mapper).recent(50))
                .containsExactly(new ToolRegistrySummary("ping", "探测", "1.0.0", "", "", true));
    }

    /** 停用只打仍启用的行。 */
    @Test
    void disableEnabledRow() {
        ToolRegistry existing = new ToolRegistry();
        existing.setToolId("echo_text");
        existing.setName("回声");
        existing.setVersion("1.0.0");
        existing.setEnabled(1);
        ToolRegistryMapper mapper = mock(ToolRegistryMapper.class);
        when(mapper.findByToolId("echo_text")).thenReturn(existing);
        when(mapper.disableEnabled("echo_text")).thenReturn(1);

        assertThat(new MysqlToolRegistryCatalog(mapper).disable("echo_text"))
                .isEqualTo(new ToolRegistrySummary("echo_text", "回声", "1.0.0", "", "", false));
    }

    /** 恢复只打停用行。 */
    @Test
    void restoreDisabledRow() {
        ToolRegistry existing = new ToolRegistry();
        existing.setToolId("echo_text");
        existing.setName("回声");
        existing.setVersion("1.0.0");
        existing.setModule("companion");
        existing.setDescription("原样返回");
        existing.setEnabled(0);
        ToolRegistryMapper mapper = mock(ToolRegistryMapper.class);
        when(mapper.findByToolId("echo_text")).thenReturn(existing);
        when(mapper.restoreDisabled("echo_text")).thenReturn(1);

        assertThat(new MysqlToolRegistryCatalog(mapper).restore("echo_text"))
                .isEqualTo(new ToolRegistrySummary("echo_text", "回声", "1.0.0", "companion", "原样返回", true));
    }
}
