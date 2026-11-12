package com.zhu.runtime.domain.middleware;

import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * 运行层：给 {@link PermissionEngine#checkPermission} 用的短命工具桩。
 * 只带工具名，不执行；不注册进 Toolkit。
 */
final class ApprovalProbeTool extends ToolBase {

    ApprovalProbeTool(String name) {
        super(name, "approval permission probe", Map.of(), true, true, false, null, false, false);
    }

    /** 探测桩从不执行。 */
    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.empty();
    }
}
