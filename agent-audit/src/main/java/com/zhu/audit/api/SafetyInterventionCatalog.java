package com.zhu.audit.api;

import java.util.List;

/**
 * 查看安全干预。只读，不改档位，也不补写摘要。
 */
public interface SafetyInterventionCatalog {

    /**
     * 最近的干预。条数默认由调用方给出，实现侧再压到上限。
     */
    List<SafetyInterventionSummary> recent(int limit);
}
