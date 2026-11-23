package com.zhu.semantic.api.attachment;

/**
 * 语义层：按附件 id 读取本人可读正文，供运行层工具调用。
 * 实现在接入层（鉴权 + 对象存储 + 解析）；不把存储细节漏进 runtime。
 */
public interface UserAttachmentTextPort {

    /**
     * 读取附件文本。失败时返回给人看的说明字符串，不抛给模型线程。
     *
     * @param tenantId     租户，可空串
     * @param userId       当前用户
     * @param attachmentId 附件标识
     * @return 截断后的正文或错误说明
     */
    String readText(String tenantId, String userId, String attachmentId);
}
