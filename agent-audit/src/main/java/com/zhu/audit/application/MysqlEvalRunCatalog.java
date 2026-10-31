package com.zhu.audit.application;

import com.zhu.audit.api.EvalRunCatalog;
import com.zhu.audit.api.EvalRunSummary;
import com.zhu.audit.infrastructure.persistence.EvalCaseMapper;
import com.zhu.audit.infrastructure.persistence.EvalDatasetMapper;
import com.zhu.audit.infrastructure.persistence.EvalRunMapper;
import com.zhu.audit.infrastructure.persistence.entity.EvalRun;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.common.util.Ids;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 登记一次评测执行。只在存储开关打开时创建。不调用模型，也不写通过率。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlEvalRunCatalog implements EvalRunCatalog {

    private static final String VERSION = "1.0.0";

    private static final String RUNNING = "RUNNING";

    private static final int ID_LIMIT = 128;

    private final EvalDatasetMapper datasets;

    private final EvalCaseMapper cases;

    private final EvalRunMapper runs;

    /** 先确认数据集和用例条数，再插入执行记录。 */
    public MysqlEvalRunCatalog(EvalDatasetMapper datasets, EvalCaseMapper cases, EvalRunMapper runs) {
        this.datasets = datasets;
        this.cases = cases;
        this.runs = runs;
    }

    /**
     * 没有用例时不开跑，避免留下一条永远是 0 的执行。
     * 通过数保持 0，结束时间留空。状态停在 RUNNING。
     */
    @Override
    public EvalRunSummary start(String datasetId) {
        if (datasetId == null || datasetId.isBlank() || datasetId.length() > ID_LIMIT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "eval run is invalid");
        }
        if (datasets.findVersion(datasetId, VERSION) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "eval dataset not found");
        }
        long total = cases.countVersion(datasetId, VERSION);
        if (total < 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "eval dataset has no cases");
        }
        if (total > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "eval run is invalid");
        }
        EvalRun row = new EvalRun();
        row.setEvalRunId(Ids.newId());
        row.setDatasetId(datasetId);
        row.setDatasetVersion(VERSION);
        row.setTotalCases((int) total);
        row.setPassedCases(0);
        row.setStatus(RUNNING);
        row.setStartedAt(LocalDateTime.now(ZoneOffset.UTC));
        runs.insert(row);
        return new EvalRunSummary(row.getEvalRunId(), datasetId, VERSION, (int) total, 0, RUNNING);
    }
}
