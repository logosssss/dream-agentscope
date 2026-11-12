package com.zhu.audit.application.deletion;

import com.zhu.audit.application.support.AuditQueryLimits;

import com.zhu.audit.api.deletion.DeletionRequestCatalog;
import com.zhu.audit.api.deletion.DeletionRequestDetail;
import com.zhu.audit.api.deletion.DeletionRequestSummary;
import com.zhu.audit.infrastructure.persistence.deletion.DataDeletionRequestMapper;
import com.zhu.audit.infrastructure.persistence.entity.deletion.DataDeletionRequest;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.common.util.Ids;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 登记数据删除请求。只在存储开关打开时创建。不批准，也不执行。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlDeletionRequestCatalog implements DeletionRequestCatalog {

    private static final String PENDING = "PENDING";

    private static final int USER_LIMIT = 128;

    private static final int TENANT_LIMIT = 64;

    private static final int REASON_LIMIT = 1024;

    private static final int REQUEST_ID_LIMIT = 64;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static final Set<String> DATA_TYPES = Set.of("SESSION", "ATTACHMENT", "ALL");

    private final DataDeletionRequestMapper requests;

    /** 请求标识由本类生成。不依赖外部传入。 */
    public MysqlDeletionRequestCatalog(DataDeletionRequestMapper requests) {
        this.requests = requests;
    }

    /**
     * 空白租户按空串保存。原因空白留空。不截断超长字段。
     * 状态停在 PENDING，不写执行结果。
     */
    @Override
    public DeletionRequestSummary submit(String userId, String tenantId, String dataType, String reason) {
        if (userId == null || userId.isBlank() || userId.length() > USER_LIMIT
                || (tenantId != null && tenantId.length() > TENANT_LIMIT)
                || dataType == null || dataType.isBlank() || !DATA_TYPES.contains(dataType)
                || (reason != null && reason.length() > REASON_LIMIT)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "deletion request is invalid");
        }
        String savedTenant = tenantId == null || tenantId.isBlank() ? "" : tenantId;
        String savedReason = reason == null || reason.isBlank() ? null : reason;
        String requestId = Ids.newId();
        DataDeletionRequest row = new DataDeletionRequest();
        row.setRequestId(requestId);
        row.setUserId(userId);
        row.setTenantId(savedTenant);
        row.setDataType(dataType);
        row.setStatus(PENDING);
        row.setReason(savedReason);
        requests.insert(row);
        return new DeletionRequestSummary(requestId, userId, savedTenant, dataType, PENDING, savedReason);
    }

    /**
     * 空用户拒绝。空白租户按空串，和登记时一致。
     * 已通过或拒绝的行也返回，调用方据此知道决定，仍不删数据。
     */
    @Override
    public List<DeletionRequestSummary> owned(String userId, String tenantId, int limit) {
        if (userId == null || userId.isBlank() || userId.length() > USER_LIMIT
                || (tenantId != null && tenantId.length() > TENANT_LIMIT)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "deletion request is invalid");
        }
        String savedTenant = tenantId == null || tenantId.isBlank() ? "" : tenantId;
        List<DataDeletionRequest> rows = requests.listOwned(userId, savedTenant, AuditQueryLimits.normalize(limit));
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream().map(MysqlDeletionRequestCatalog::summary).toList();
    }

    /**
     * 空标识拒绝。找不到或不是本人本租户按找不到，避免把别人的原因交出去。
     * 已通过或拒绝的行也返回，仍不删数据。
     */
    @Override
    public DeletionRequestDetail ownedOne(String userId, String tenantId, String requestId) {
        if (userId == null || userId.isBlank() || userId.length() > USER_LIMIT
                || (tenantId != null && tenantId.length() > TENANT_LIMIT)
                || requestId == null || requestId.isBlank() || requestId.length() > REQUEST_ID_LIMIT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "deletion request is invalid");
        }
        String savedTenant = tenantId == null || tenantId.isBlank() ? "" : tenantId;
        DataDeletionRequest row = requests.findOwned(requestId, userId, savedTenant);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "deletion request not found");
        }
        String tenant = row.getTenantId() == null ? "" : row.getTenantId();
        String requested = row.getRequestedAt() == null ? "" : TIME.format(row.getRequestedAt());
        return new DeletionRequestDetail(
                row.getRequestId(), tenant, row.getDataType(), row.getStatus(), row.getReason(), requested);
    }

    /** 列表和登记共用同一份摘要。 */
    private static DeletionRequestSummary summary(DataDeletionRequest row) {
        String tenant = row.getTenantId() == null ? "" : row.getTenantId();
        return new DeletionRequestSummary(
                row.getRequestId(),
                row.getUserId(),
                tenant,
                row.getDataType(),
                row.getStatus(),
                row.getReason());
    }
}
