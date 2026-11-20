package com.zhu.bootstrap;

import com.zhu.semantic.application.tool.StoredToolCatalog;
import com.zhu.semantic.contract.tool.ToolDescriptor;
import com.zhu.semantic.infrastructure.persistence.ToolRegistryMapper;
import com.zhu.semantic.infrastructure.persistence.entity.ToolRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 启用的目录行变成工具描述。不连接 MySQL，也不注册 Toolkit。
 */
class StoredToolCatalogTest {

    /** 空白标识跳过。空的展示名和说明变成空串。 */
    @Test
    void enabledRowsBecomeDescriptors() {
        ToolRegistry echo = row("echo_text", "回声", "原样返回");
        ToolRegistry blank = row(" ", "忽略", "没有标识");
        ToolRegistry unnamed = row("ping", null, null);
        ToolRegistryMapper mapper = mock(ToolRegistryMapper.class);
        when(mapper.listEnabled()).thenReturn(List.of(echo, blank, unnamed));

        List<ToolDescriptor> enabled = new StoredToolCatalog(mapper).listEnabled();

        assertThat(enabled).containsExactly(
                new ToolDescriptor("echo_text", "回声", "原样返回"),
                new ToolDescriptor("ping", "", ""));
    }

    /** 查询没有结果时返回空列表。 */
    @Test
    void nullQueryIsEmpty() {
        ToolRegistryMapper mapper = mock(ToolRegistryMapper.class);
        when(mapper.listEnabled()).thenReturn(null);

        assertThat(new StoredToolCatalog(mapper).listEnabled()).isEmpty();
    }

    /** 测试行。停用的行由 SQL 滤掉，这里不放。 */
    private static ToolRegistry row(String toolId, String name, String description) {
        ToolRegistry row = new ToolRegistry();
        row.setToolId(toolId);
        row.setName(name);
        row.setDescription(description);
        return row;
    }
}
