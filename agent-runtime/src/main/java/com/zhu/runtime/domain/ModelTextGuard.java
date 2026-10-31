package com.zhu.runtime.domain;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * 进出模型的文本检查。规则留在代码里，不放到 Nacos。
 * 不判断话题，只挡住过长的用户输入，以及还没替换完的 {{变量}}。
 */
public final class ModelTextGuard {

    private static final Logger log = Logger.getLogger(ModelTextGuard.class.getName());

    /** 没配或配成小于 1 时使用。按 Java 字符数，不是字节。 */
    public static final int DEFAULT_MAX_USER_CHARS = 32768;

    /** 和提示词渲染同一写法。单括号留给正文。 */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{[^{}]+}}");

    private ModelTextGuard() {
    }

    /**
     * 调用模型之前检查。用户正文可以包含花括号。系统提示词里留下占位符则拒绝。
     * 上限小于 1 时改用默认值，避免配置写成 0 后不再限制。
     */
    public static void beforeCall(String systemPrompt, String userText, int maxUserChars) {
        int limit = maxUserChars < 1 ? DEFAULT_MAX_USER_CHARS : maxUserChars;
        if (userText != null && userText.length() > limit) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "user text is too long");
        }
        if (systemPrompt != null && PLACEHOLDER.matcher(systemPrompt).find()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "system prompt placeholders are incomplete");
        }
    }

    /**
     * 模型最终回复里若还留着占位符，改成空串再交给调用方。
     * 不抛错，因为模型调用已经发生。不把回复正文写进日志。
     */
    public static String outbound(String reply) {
        if (reply == null || reply.isEmpty()) {
            return "";
        }
        if (PLACEHOLDER.matcher(reply).find()) {
            log.warning("model reply placeholders dropped");
            return "";
        }
        return reply;
    }
}
