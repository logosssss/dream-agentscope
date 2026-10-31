package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.TokenUsage;

/**
 * token_usage 的访问。简单插入用 BaseMapper，Java 不写 SQL。
 */
public interface TokenUsageMapper extends BaseMapper<TokenUsage> {
}
