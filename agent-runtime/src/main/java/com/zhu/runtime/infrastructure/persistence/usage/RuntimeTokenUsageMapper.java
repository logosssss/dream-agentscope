package com.zhu.runtime.infrastructure.persistence.usage;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.runtime.infrastructure.persistence.entity.usage.TokenUsage;

/**
 * 运行层 token_usage 写入 Mapper。Bean 名与 access 读 Mapper 区分，避免用户进程双扫冲突。
 */
public interface RuntimeTokenUsageMapper extends BaseMapper<TokenUsage> {
}
