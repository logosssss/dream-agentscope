package com.zhu.runtime.infrastructure.gateway;

import com.zhu.runtime.api.agent.AgentImagePart;
import com.zhu.runtime.api.agent.AgentRequest;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 运行层网关零件：把 AgentRequest 收成框架用户 Msg。
 * 有图时组 TextBlock + ImageBlock；无图走 textContent。
 */
final class AgentUserMessages {

    /** 视觉失败降级时塞进用户文本的前缀，避免模型假装看过图。 */
    static final String VISION_DEGRADE_PREFIX = "（系统提示：未能看图，已按纯文本继续；图片附件仍已上传。）\n";

    private AgentUserMessages() {
    }

    /**
     * 本轮用户消息。有有效图片字节才组多模态；否则纯文本。
     */
    static Msg of(AgentRequest request) {
        String text = request == null || request.userText() == null ? "" : request.userText();
        if (request == null || !request.hasImages()) {
            return textOnly(text);
        }
        List<ContentBlock> blocks = new ArrayList<>();
        blocks.add(TextBlock.builder().text(text).build());
        for (AgentImagePart part : request.images()) {
            if (part == null || part.base64Data() == null || part.base64Data().isBlank()) {
                continue;
            }
            String mime = part.mimeType() == null || part.mimeType().isBlank()
                    ? "image/jpeg"
                    : part.mimeType().trim().toLowerCase(Locale.ROOT);
            Base64Source source = Base64Source.builder().mediaType(mime).data(part.base64Data().trim()).build();
            blocks.add(ImageBlock.builder().source(source).build());
        }
        if (blocks.size() == 1) {
            return textOnly(text);
        }
        return Msg.builder().role(MsgRole.USER).content(blocks).build();
    }

    /** 视觉失败后的纯文本重试文案。 */
    static String degradedText(AgentRequest request) {
        String text = request == null || request.userText() == null ? "" : request.userText();
        if (text.startsWith(VISION_DEGRADE_PREFIX)) {
            return text;
        }
        return VISION_DEGRADE_PREFIX + text;
    }

    /** 纯文本用户消息。 */
    static Msg textOnly(String text) {
        return Msg.builder().role(MsgRole.USER).textContent(text == null ? "" : text).build();
    }
}
