package com.zhu.runtime.infrastructure.persistence.usage;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.runtime.infrastructure.persistence.entity.usage.TokenUsage;

/**
 * token_usage 写入。合计与用户侧列表在 access / admin 各自 Mapper。
 */
public interface TokenUsageMapper extends BaseMapper<TokenUsage> {
}