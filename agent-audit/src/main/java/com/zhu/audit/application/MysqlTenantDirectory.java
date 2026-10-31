package com.zhu.audit.application;

import com.zhu.audit.api.TenantCatalog;
import com.zhu.audit.api.TenantGate;
import com.zhu.audit.api.TenantSummary;
import com.zhu.audit.infrastructure.persistence.TenantMapper;
import com.zhu.audit.infrastructure.persistence.entity.TenantRecord;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * MySQL 上的租户目录。只在存储开关打开时创建。不建用户，也不改已有行的状态。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlTenantDirectory implements TenantGate, TenantCatalog {

    private static final String ACTIVE = "ACTIVE";

    private static final String PLAN = "standard";

    private final TenantMapper tenants;

    /** 查找和插入用同一个 Mapper。重复标识由唯一键拒绝。 */
    public MysqlTenantDirectory(TenantMapper tenants) {
        this.tenants = tenants;
    }

    /**
     * 空标识、查不到，或状态不是 ACTIVE，都不能开始对话。
     * 不把停用和关闭分成两种错误，调用方只需要知道现在不能用。
     */
    @Override
    public void requireActive(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "tenantId is required");
        }
        TenantRecord row = tenants.findByTenantId(tenantId);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "tenant not found");
        }
        if (!ACTIVE.equals(row.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "tenant is not active");
        }
    }

    /**
     * 标识或名称为空、超长时拒绝。套餐不由调用方指定。
     * 同一标识再插入一次按冲突处理。
     */
    @Override
    public TenantSummary register(String tenantId, String name) {
        if (tenantId == null || tenantId.isBlank() || tenantId.length() > 64
                || name == null || name.isBlank() || name.length() > 128) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "tenant is invalid");
        }
        TenantRecord row = new TenantRecord();
        row.setTenantId(tenantId);
        row.setName(name);
        row.setPlan(PLAN);
        row.setStatus(ACTIVE);
        try {
            tenants.insert(row);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "tenant already exists");
        }
        return new TenantSummary(tenantId, name, PLAN, ACTIVE);
    }
}
