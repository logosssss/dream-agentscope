package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.Attachment;

/**
 * attachment 的访问。登记用 BaseMapper 插入，不在这里收文件。
 */
public interface AttachmentMapper extends BaseMapper<Attachment> {
}
