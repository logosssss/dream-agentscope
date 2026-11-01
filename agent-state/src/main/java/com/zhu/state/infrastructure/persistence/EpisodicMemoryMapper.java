package com.zhu.state.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.state.infrastructure.persistence.entity.EpisodicMemory;

/**
 * episodic_memory 的访问。简单插入用 BaseMapper，不在这里写 SQL。
 */
public interface EpisodicMemoryMapper extends BaseMapper<EpisodicMemory> {
}
