package com.zhu.audit.application;

import com.zhu.audit.api.EvalDatasetCatalog;
import com.zhu.audit.api.EvalDatasetSummary;
import com.zhu.audit.infrastructure.persistence.EvalDatasetMapper;
import com.zhu.audit.infrastructure.persistence.entity.EvalDataset;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * MySQL 上的评测数据集。只在存储开关打开时创建。可列出已登记行。不写用例，也不执行。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlEvalDatasetCatalog implements EvalDatasetCatalog {

    private static final String VERSION = "1.0.0";

    private static final int ID_LIMIT = 128;

    private static final int NAME_LIMIT = 256;

    private static final int DESCRIPTION_LIMIT = 1000;

    private final EvalDatasetMapper datasets;

    /** 插入用 Mapper。同一标识和版本由唯一键拒绝。 */
    public MysqlEvalDatasetCatalog(EvalDatasetMapper datasets) {
        this.datasets = datasets;
    }

    /**
     * 标识或名称为空、超长，或说明超过 1000 字时拒绝。版本不由调用方指定。
     * 同一标识再登记 1.0.0 按冲突处理。
     */
    @Override
    public EvalDatasetSummary register(String datasetId, String name, String description) {
        if (datasetId == null || datasetId.isBlank() || datasetId.length() > ID_LIMIT
                || name == null || name.isBlank() || name.length() > NAME_LIMIT
                || (description != null && description.length() > DESCRIPTION_LIMIT)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "eval dataset is invalid");
        }
        EvalDataset row = new EvalDataset();
        row.setDatasetId(datasetId);
        row.setName(name);
        row.setVersion(VERSION);
        row.setDescription(description == null || description.isBlank() ? null : description);
        row.setEnabled(1);
        try {
            datasets.insert(row);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "eval dataset already exists");
        }
        String stored = description == null || description.isBlank() ? null : description;
        return new EvalDatasetSummary(datasetId, name, VERSION, stored);
    }

    /**
     * 空结果返回空列表。不按启用再滤一层，停用也要能看见。
     * 不带回用例，也不执行。
     */
    @Override
    public List<EvalDatasetSummary> recent(int limit) {
        List<EvalDataset> rows = datasets.recent(AuditQueryLimits.normalize(limit));
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(row -> new EvalDatasetSummary(
                        row.getDatasetId(),
                        row.getName(),
                        row.getVersion(),
                        row.getDescription()))
                .toList();
    }
}
