package com.zhu.runtime.application.usage;

import com.zhu.runtime.api.UsageMeter;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ChatUsage;

/**
 * 从框架回复里取出 token 用量。运行层内部使用，不把框架类型漏到接口外。
 */
public final class ModelCallUsage {

    private ModelCallUsage() {
    }

    /**
     * 回复或用量为空时不记。耗时按调用方给出的起始时间计算，不用框架字段，避免单位不一致。
     * 运行标识优先用调用方传入的值，空则用回复上的标识。
     */
    public static void record(
            UsageMeter meter,
            String tenantId,
            String userId,
            String sessionId,
            String runId,
            String model,
            Msg reply,
            long startedNanos) {
        if (meter == null || reply == null) {
            return;
        }
        ChatUsage usage = reply.getChatUsage() != null ? reply.getChatUsage() : reply.getUsage();
        if (usage == null) {
            return;
        }
        long elapsed = System.nanoTime() - startedNanos;
        long latencyMillis = elapsed <= 0 ? 0 : elapsed / 1_000_000L;
        String id = runId == null || runId.isBlank() ? reply.getId() : runId;
        meter.onCall(tenantId, userId, sessionId, id, model, usage.getInputTokens(), usage.getOutputTokens(), latencyMillis);
    }
}
