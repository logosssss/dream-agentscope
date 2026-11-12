package com.zhu.audit.application.eval;

import com.zhu.audit.api.eval.EvalCaseCatalog;
import com.zhu.audit.api.eval.EvalCaseSummary;
import com.zhu.audit.infrastructure.persistence.eval.EvalCaseMapper;
import com.zhu.audit.infrastructure.persistence.eval.EvalDatasetMapper;
import com.zhu.audit.infrastructure.persistence.entity.eval.EvalCase;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 给已登记的评测数据集追加用例。只在存储开关打开时创建。不执行。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlEvalCaseCatalog implements EvalCaseCatalog {

    private static final String VERSION = "1.0.0";

    private static final int ID_LIMIT = 128;

    private static final int TEXT_LIMIT = 4000;

    private final EvalDatasetMapper datasets;

    private final EvalCaseMapper cases;

    /** 先确认数据集，再插入用例。同一序号由唯一键拒绝。 */
    public MysqlEvalCaseCatalog(EvalDatasetMapper datasets, EvalCaseMapper cases) {
        this.datasets = datasets;
        this.cases = cases;
    }

    /**
     * 序号从 1 起。输入不能空，输入和期望输出都不超过 4000 字。
     * 数据集还没登记时拒绝，不把用例挂到空版本上。
     */
    @Override
    public EvalCaseSummary add(String datasetId, Integer caseNo, String inputText, String expectedText) {
        if (datasetId == null || datasetId.isBlank() || datasetId.length() > ID_LIMIT
                || caseNo == null || caseNo < 1
                || inputText == null || inputText.isBlank() || inputText.length() > TEXT_LIMIT
                || (expectedText != null && expectedText.length() > TEXT_LIMIT)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "eval case is invalid");
        }
        if (datasets.findVersion(datasetId, VERSION) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "eval dataset not found");
        }
        EvalCase row = new EvalCase();
        row.setDatasetId(datasetId);
        row.setVersion(VERSION);
        row.setCaseNo(caseNo);
        row.setInputText(inputText);
        row.setExpectedText(expectedText == null || expectedText.isBlank() ? null : expectedText);
        try {
            cases.insert(row);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "eval case already exists");
        }
        String expected = expectedText == null || expectedText.isBlank() ? null : expectedText;
        return new EvalCaseSummary(datasetId, VERSION, caseNo, inputText, expected);
    }
}
