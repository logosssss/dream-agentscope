package com.zhu.audit.application;

import com.zhu.audit.api.DeletionRequestCatalog;
import com.zhu.audit.api.DeletionRequestSummary;
import com.zhu.audit.infrastructure.persistence.DataDeletionRequestMapper;
import com.zhu.audit.infrastructure.persistence.entity.DataDeletionRequest;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.common.util.Ids;
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
}
