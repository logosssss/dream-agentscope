package com.zhu.semantic.application;

import com.zhu.semantic.api.tool.ToolCatalogService;
import com.zhu.semantic.contract.tool.ToolDescriptor;
import com.zhu.semantic.infrastructure.persistence.ToolRegistryMapper;
import com.zhu.semantic.infrastructure.persistence.entity.ToolRegistry;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 从 tool_registry 读取启用的工具。MySQL 打开时替换空目录。
 * 这里只交出描述。没有对应代码实现时，运行层不会把它注册进 Toolkit。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class StoredToolCatalog implements ToolCatalogService {

    private final ToolRegistryMapper registry;

    /** 表由用户进程的 Flyway 建立。没有数据源时这个 Bean 不创建。 */
    public StoredToolCatalog(ToolRegistryMapper registry) {
        this.registry = registry;
    }

    /**
     * 空白标识跳过。展示名或说明为空时用空串，不把 null 交给调用方。
     * 查询结果是 null 时视为没有启用项。
     */
    @Override
    public List<ToolDescriptor> listEnabled() {
        List<ToolRegistry> rows = registry.listEnabled();
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<ToolDescriptor> enabled = new ArrayList<>();
        for (ToolRegistry row : rows) {
            if (row == null || row.getToolId() == null || row.getToolId().isBlank()) {
                continue;
            }
            String name = row.getName() == null ? "" : row.getName();
            String description = row.getDescription() == null ? "" : row.getDescription();
            enabled.add(new ToolDescriptor(row.getToolId(), name, description));
        }
        return List.copyOf(enabled);
    }
}
