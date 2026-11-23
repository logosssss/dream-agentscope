package com.zhu.runtime.infrastructure.tool;

import com.zhu.semantic.api.attachment.UserAttachmentTextPort;
import com.zhu.semantic.contract.tool.ProductTool;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 运行层：按附件 id 读取本人文档正文（PDF / docx / 纯文本）。
 * 身份只从 RuntimeContext 取；端口实现在接入层。
 */
@Component
public class AttachmentReadTool implements ProductTool {

    public static final String TOOL_ID = "attachment_read";

    private final ObjectProvider<UserAttachmentTextPort> ports;

    public AttachmentReadTool(ObjectProvider<UserAttachmentTextPort> ports) {
        this.ports = ports;
    }

    @Override
    public String toolId() {
        return TOOL_ID;
    }

    @Override
    public Object target() {
        return this;
    }

    /**
     * 读当前用户的附件文本。端口缺失或参数空时返回说明，不抛给模型线程。
     */
    @Tool(
            name = TOOL_ID,
            description = "当需要阅读用户本轮或会话中上传的文档附件（PDF、Word、纯文本）时使用。输入 attachmentId。")
    public String read(
            @ToolParam(name = "attachmentId", description = "附件标识") String attachmentId,
            RuntimeContext ctx) {
        UserAttachmentTextPort port = ports.getIfAvailable();
        if (port == null) {
            return "附件读取能力暂不可用。";
        }
        String userId = ctx == null ? null : ctx.getUserId();
        Object tenantRaw = ctx == null ? null : ctx.get("tenantId");
        String tenantId = tenantRaw == null ? "" : tenantRaw.toString();
        if (userId == null || userId.isBlank()) {
            return "未找到用户身份，无法读取附件。";
        }
        return port.readText(tenantId, userId, attachmentId);
    }
}
