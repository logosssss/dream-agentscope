package com.zhu.audit.api.eval;

import java.util.List;

/**
 * 管理进程登记、停用、恢复和查看评测数据集。不写用例，也不发起评测。
 */
public interface EvalDatasetCatalog {

    /**
     * 新增 1.0.0。同一标识和版本再登记一次是冲突。说明可以空。
     */
    EvalDatasetSummary register(String datasetId, String name, String description);

    /**
     * 停用仍启用的 1.0.0。没有这一行或已停用由实现返回失败。
     * 停用后不再开跑。不删用例。
     */
    EvalDatasetSummary disable(String datasetId);

    /**
     * 恢复已停用的 1.0.0。没有这一行或已启用由实现返回失败。
     */
    EvalDatasetSummary restore(String datasetId);

    /**
     * 最近登记的评测数据集，含停用。条数默认由调用方给出，实现侧再压到上限。
     * 不带回用例，也不执行。
     */
    List<EvalDatasetSummary> recent(int limit);
}
