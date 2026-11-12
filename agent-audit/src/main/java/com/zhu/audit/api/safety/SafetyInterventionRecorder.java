package com.zhu.audit.api.safety;

/**
 * 记下一次安全干预。只在升高或危机时写。
 * 摘要不含用户原文。MySQL 关闭时的实现直接跳过。
 */
public interface SafetyInterventionRecorder {

    /** 升高留意。仍用搭子话术。 */
    String LEVEL_ELEVATED = "ELEVATED";

    /** 危机。已经换成危机话术。 */
    String LEVEL_CRITICAL = "CRITICAL";

    /** 关键词命中。 */
    String SOURCE_KEYWORD = "KEYWORD";

    /** 语义判定给出档位。 */
    String SOURCE_SEMANTIC = "SEMANTIC";

    /** 语义判定失败，按升高处理。 */
    String SOURCE_JUDGE_FAILED = "JUDGE_FAILED";

    /**
     * 档位只接受升高或危机。来源只接受关键词、语义或判定失败。
     * 摘要由调用方写死短句，不要把用户原文传进来。
     */
    void record(
            String tenantId,
            String userId,
            String sessionId,
            String riskLevel,
            String source,
            String summary);
}
