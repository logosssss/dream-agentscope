package com.zhu.admin.application.event;

import com.zhu.ops.support.QueryLimits;

import com.zhu.admin.api.event.OperationAuditCatalog;
import com.zhu.admin.api.event.OperationAuditSummary;
import com.zhu.admin.infrastructure.persistence.event.OperationAuditMapper;
import com.zhu.admin.infrastructure.persistence.entity.event.OperationAudit;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 登记和查看后台操作审计。只在存储开关打开时创建。不写前后快照。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlOperationAuditCatalog implements OperationAuditCatalog {

    private static final int OPERATOR_LIMIT = 128;

    private static final int TENANT_LIMIT = 64;

    private static final int OPERATION_LIMIT = 128;

    private static final int TYPE_LIMIT = 64;

    private static final int OBJECT_LIMIT = 128;

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final OperationAuditMapper audits;

    /** 插入和列表共用 Mapper。校验失败不写库。 */
    public MysqlOperationAuditCatalog(OperationAuditMapper audits) {
        this.audits = audits;
    }

    /**
     * 空白租户按空串。对象标识空白留空。不截断超长字段。
     * 不存 before/after、IP 和 UA。登记响应的时间是空串，列表再从库里读。
     */
    @Override
    public OperationAuditSummary record(
            String operatorId,
            String tenantId,
            String operation,
            String objectType,
            String objectId) {
        if (operatorId == null || operatorId.isBlank() || operatorId.length() > OPERATOR_LIMIT
                || (tenantId != null && tenantId.length() > TENANT_LIMIT)
                || operation == null || operation.isBlank() || operation.length() > OPERATION_LIMIT
                || objectType == null || objectType.isBlank() || objectType.length() > TYPE_LIMIT
                || (objectId != null && objectId.length() > OBJECT_LIMIT)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "operation audit is invalid");
        }
        String savedTenant = tenantId == null || tenantId.isBlank() ? "" : tenantId;
        String savedObject = objectId == null || objectId.isBlank() ? null : objectId;
        OperationAudit row = new OperationAudit();
        row.setOperatorId(operatorId);
        row.setTenantId(savedTenant);
        row.setOperation(operation);
        row.setObjectType(objectType);
        row.setObjectId(savedObject);
        audits.insert(row);
        return new OperationAuditSummary(operatorId, savedTenant, operation, objectType, savedObject, "");
    }

    /** 条数和审计查询用同一套上限。空结果返回空列表。 */
    @Override
    public List<OperationAuditSummary> recent(int limit) {
        List<OperationAudit> rows = audits.recent(QueryLimits.normalize(limit));
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<OperationAuditSummary> entries = new ArrayList<>(rows.size());
        for (OperationAudit row : rows) {
            entries.add(new OperationAuditSummary(
                    row.getOperatorId(),
                    row.getTenantId(),
                    row.getOperation(),
                    row.getObjectType(),
                    row.getObjectId(),
                    time(row.getCreatedAt())));
        }
        return List.copyOf(entries);
    }

    /** 空时间给空串，页面不用判 null。精度跟库里的 DATETIME(3) 走。 */
    private static String time(LocalDateTime value) {
        return value == null ? "" : value.format(ISO);
    }
}
