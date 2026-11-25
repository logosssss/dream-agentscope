package com.zhu.access.application.chat;

import com.zhu.common.context.TenantContext;
import com.zhu.semantic.api.prompt.PlatformPrompt;
import com.zhu.semantic.api.prompt.PromptService;
import com.zhu.semantic.contract.product.ProductSemanticProvider;
import java.util.Map;

/**
 * WEB 与 API 组 {@code AgentRequest} 时共用的调用方字段。接入层。
 * 租户只读登录线程。人设只读产品提示词。
 */
final class AccessCaller {

    /** 只提供静态方法，避免两条入口各写一遍身份和人设。 */
    private AccessCaller() {
    }

    /**
     * 未登录租户写成空串，避免 null 进会话键。线程上有值则原样使用，空白也不改。
     */
    static String tenantId() {
        String tenantId = TenantContext.get();
        return tenantId == null ? "" : tenantId;
    }

    /**
     * 人设只来自产品提示词。请求体里的 systemPrompt 不参与，避免客户端改人格。
     */
    static String systemPrompt(PromptService prompts, ProductSemanticProvider semantics) {
        return prompts.get(PlatformPrompt.SYSTEM_KEY, Map.of(), semantics.promptFallback(PlatformPrompt.SYSTEM_KEY));
    }
}
