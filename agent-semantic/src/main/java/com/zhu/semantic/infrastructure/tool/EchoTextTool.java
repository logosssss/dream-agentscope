package com.zhu.semantic.infrastructure.tool;

import com.zhu.semantic.contract.ProductTool;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 产品域第一把业务工具。只做原样回声，用来跑通目录注册和审批。
 * 无副作用，不查库。批准后不会自动再执行。
 */
@Component
public class EchoTextTool implements ProductTool {

    /** 与目录、注解名一致。 */
    public static final String TOOL_ID = "echo_text";

    /** 目录匹配用这个标识。 */
    @Override
    public String toolId() {
        return TOOL_ID;
    }

    /** 工具方法就在本对象上。 */
    @Override
    public Object target() {
        return this;
    }

    /**
     * 原样返回入参。空入参按空串，不抛错。
     * 生产权限种子为 ASK，模型点名后会先走审批，不会直接执行。
     */
    @Tool(name = TOOL_ID, description = "原样返回")
    public String echo(@ToolParam(name = "text", description = "原文") String text) {
        return text == null ? "" : text;
    }
}
