package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.TokenUsage;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * token_usage 的访问。简单插入用 BaseMapper，合计和列表在 XML 里。
 */
public interface TokenUsageMapper extends BaseMapper<TokenUsage> {

    /**
     * 合计这个租户在区间内的 token 和金额。起点含、终点不含。没有行时合计为 0。
     */
    UsagePeriodTotal sumForPeriod(
            @Param("tenantId") String tenantId,
            @Param("periodStart") LocalDateTime periodStart,
            @Param("periodEnd") LocalDateTime periodEnd);

    /**
     * 当前用户在这个租户下最近的用量。不带回提示词。
     */
    List<TokenUsage> listOwned(
            @Param("userId") String userId,
            @Param("tenantId") String tenantId,
            @Param("limit") int limit);
}
