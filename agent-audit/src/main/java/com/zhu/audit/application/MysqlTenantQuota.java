package com.zhu.audit.application;

import com.zhu.audit.api.TenantQuotaCatalog;
import com.zhu.audit.api.TenantQuotaLookup;
import com.zhu.audit.api.TenantQuotaSummary;
import com.zhu.audit.infrastructure.persistence.TenantQuotaMapper;
import com.zhu.audit.infrastructure.persistence.entity.TenantQuotaRecord;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.logging.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * MySQL 上的租户每日请求上限。只在存储开关打开时创建。不写已用量，也不写其它类型。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlTenantQuota implements TenantQuotaLookup, TenantQuotaCatalog {

    private static final Logger log = Logger.getLogger(MysqlTenantQuota.class.getName());

    private static final String REQUESTS = "REQUESTS";

    private static final String DAILY = "DAILY";

    private final TenantQuotaMapper quotas;

    /** 查找和插入用同一个 Mapper。重复的租户加类型加周期由唯一键拒绝。 */
    public MysqlTenantQuota(TenantQuotaMapper quotas) {
        this.quotas = quotas;
    }

    /**
     * 空标识当作没有单独上限。查库失败时拒绝对话，不退回进程配置。
     */
    @Override
    public Long dailyRequestLimit(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return null;
        }
        try {
            return quotas.findDailyRequestLimit(tenantId);
        } catch (DataAccessException ex) {
            log.warning("tenant quota lookup failed");
            throw new BusinessException(ErrorCode.UNAVAILABLE, "tenant quota is unavailable");
        }
    }

    /**
     * 标识为空、超长，或上限为负时拒绝。0 可以写入，表示这个租户不限额。
     * 同一租户再登记一次按冲突处理，不覆盖旧上限。
     */
    @Override
    public TenantQuotaSummary setDailyRequests(String tenantId, Long limit) {
        if (tenantId == null || tenantId.isBlank() || tenantId.length() > 64 || limit == null || limit < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "tenant quota is invalid");
        }
        TenantQuotaRecord row = new TenantQuotaRecord();
        row.setTenantId(tenantId);
        row.setQuotaType(REQUESTS);
        row.setPeriod(DAILY);
        row.setLimitValue(limit);
        row.setEnabled(1);
        try {
            quotas.insert(row);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "tenant quota already exists");
        }
        return new TenantQuotaSummary(tenantId, REQUESTS, DAILY, limit);
    }

    /**
     * 只改已有行的上限。没有行不插入。
     * 0 仍表示这个租户不限额。不改启用标记。
     */
    @Override
    public TenantQuotaSummary updateDailyRequests(String tenantId, Long limit) {
        if (tenantId == null || tenantId.isBlank() || tenantId.length() > 64 || limit == null || limit < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "tenant quota is invalid");
        }
        int updated = quotas.updateDailyRequestLimit(tenantId, limit);
        if (updated != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "tenant quota not found");
        }
        return new TenantQuotaSummary(tenantId, REQUESTS, DAILY, limit);
    }
}
