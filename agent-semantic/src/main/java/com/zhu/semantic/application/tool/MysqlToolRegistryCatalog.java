package com.zhu.semantic.application.tool;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.semantic.api.tool.ToolRegistryCatalog;
import com.zhu.semantic.api.tool.ToolRegistrySummary;
import com.zhu.semantic.infrastructure.persistence.ToolRegistryMapper;
import com.zhu.semantic.infrastructure.persistence.entity.ToolRegistry;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 登记、停用、恢复和列出工具目录。只在存储开关打开时创建。不执行工具，也不写权限。
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
        return new ToolRegistrySummary(toolId, name, VERSION, savedModule, savedDescription, true);
    }

    /**
     * 先确认存在且启用，再按启用行更新。
     */
    @Override
    public ToolRegistrySummary disable(String toolId) {
        ToolRegistry existing = require(toolId);
        if (!Integer.valueOf(1).equals(existing.getEnabled())) {
            throw new BusinessException(ErrorCode.CONFLICT, "tool registry is not enabled");
        }
        int updated = registry.disableEnabled(toolId);
        if (updated != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "tool registry is not enabled");
        }
        return summary(existing, false);
    }

    /**
     * 先确认存在且停用，再按停用行更新。
     */
    @Override
    public ToolRegistrySummary restore(String toolId) {
        ToolRegistry existing = require(toolId);
        if (Integer.valueOf(1).equals(existing.getEnabled())) {
            throw new BusinessException(ErrorCode.CONFLICT, "tool registry is already enabled");
        }
        int updated = registry.restoreDisabled(toolId);
        if (updated != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "tool registry is already enabled");
        }
        return summary(existing, true);
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
                        row.getDescription() == null ? "" : row.getDescription(),
                        Integer.valueOf(1).equals(row.getEnabled())))
                .toList();
    }

    /** 工具标识不能空。 */
    private ToolRegistry require(String toolId) {
        if (toolId == null || toolId.isBlank() || toolId.length() > ID_LIMIT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "tool registry is invalid");
        }
        ToolRegistry existing = registry.findByToolId(toolId);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "tool registry not found");
        }
        return existing;
    }

    /** 用库里的展示字段，只改启用标记。 */
    private static ToolRegistrySummary summary(ToolRegistry existing, boolean enabled) {
        return new ToolRegistrySummary(
                existing.getToolId(),
                existing.getName() == null ? "" : existing.getName(),
                existing.getVersion() == null ? VERSION : existing.getVersion(),
                existing.getModule() == null ? "" : existing.getModule(),
                existing.getDescription() == null ? "" : existing.getDescription(),
                enabled);
    }
}
