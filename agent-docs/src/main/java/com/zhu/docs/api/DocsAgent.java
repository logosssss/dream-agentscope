package com.zhu.docs.api;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;

/**
 * 文档助手的固定标识。它不是 main Agent，通用对话入口不能点名调用。
 */
public final class DocsAgent {

    /** 产品模块、提问和通用对话拒绝都用这一份，避免三处各写一个字符串。 */
    public static final String ID = "docs-v1";

    private DocsAgent() {
    }

    /**
     * 通用对话如果点名这个标识，按不存在处理。
     * 不放在 Gateway 的注册检查里：提问本身还要靠这个标识进模型。
     *
     * @param agentId 请求里的标识，空表示走 main
     */
    public static void rejectPublicChat(String agentId) {
        if (ID.equals(agentId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "unknown agentId: " + ID);
        }
    }
}
