package com.zhu.access.infrastructure.persistence.usage;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.access.infrastructure.persistence.entity.usage.TokenUsage;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 接入层 token_usage 读 Mapper。Bean 名与 runtime 写入 Mapper 区分，避免用户进程双扫冲突。
 */
public interface AccessTokenUsageMapper extends BaseMapper<TokenUsage> {

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

    /**
     * 本人本租户的一条用量。别人的或没有时返回 null。不带回提示词。
     */
    TokenUsage findOwned(
            @Param("id") Long id,
            @Param("userId") String userId,
            @Param("tenantId") String tenantId);
}
