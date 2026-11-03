package com.zhu.semantic.application;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.semantic.api.ToolRegistryCatalog;
import com.zhu.semantic.api.ToolRegistrySummary;
import com.zhu.semantic.infrastructure.persistence.ToolRegistryMapper;
import com.zhu.semantic.infrastructure.persistence.entity.ToolRegistry;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 登记和列出工具目录。只在存储开关打开时创建。不执行工具，也不写权限。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlToolRegistryCatalog implements ToolRegistryCatalog {

    private static final String VERSION = "1.0.0";

    private static final int ID_LIMIT = 128;

    private static final int NAME_LIMIT = 128;

    private static final int MODULE_LIMIT = 64;

    private final ToolRegistryMapper registry;

    /** 同一 tool_id 由唯一键拒绝。 */
    public MysqlToolRegistryCatalog(ToolRegistryMapper registry) {
        this.registry = registry;
    }

    /**
     * 空白模块、说明按空串保存。不写 param_schema。
     * 没有对应 ProductTool 时运行层仍不会注册进 Toolkit。
     */
    @Override
    public ToolRegistrySummary register(String toolId, String name, String module, String description) {
        if (toolId == null || toolId.isBlank() || toolId.length() > ID_LIMIT
                || name == null || name.isBlank() || name.length() > NAME_LIMIT
                || (module != null && module.length() > MODULE_LIMIT)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "tool registry is invalid");
        }
        String savedModule = module == null || module.isBlank() ? "" : module;
        String savedDescription = description == null ? "" : description;
        ToolRegistry row = new ToolRegistry();
        row.setToolId(toolId);
        row.setName(name);
        row.setVersion(VERSION);
        row.setModule(savedModule.isEmpty() ? null : savedModule);
        row.setDescription(savedDescription.isEmpty() ? null : savedDescription);
        row.setEnabled(1);
        try {
            registry.insert(row);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "tool registry already exists");
        }
        return new ToolRegistrySummary(toolId, name, VERSION, savedModule, savedDescription);
    }

    /**
     * 空结果返回空列表。不按启用再滤一层，停用也要能看见。
     * 不读 param_schema。
     */
    @Override
    public List<ToolRegistrySummary> recent(int limit) {
        List<ToolRegistry> rows = registry.recent(CatalogQueryLimits.normalize(limit));
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(row -> new ToolRegistrySummary(
                        row.getToolId(),
                        row.getName() == null ? "" : row.getName(),
                        row.getVersion() == null ? VERSION : row.getVersion(),
                        row.getModule() == null ? "" : row.getModule(),
                        row.getDescription() == null ? "" : row.getDescription()))
                .toList();
    }
}
