package com.zhu.semantic.application;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.semantic.api.PromptService;
import com.zhu.semantic.infrastructure.nacos.PromptSource;
import java.util.Map;
import java.util.logging.Logger;
import org.springframework.stereotype.Service;

/**
 * 提示词读取。外部来源失败、缺失或渲染后为空时，退回调用方给的 fallback。
 * 容器里只能有一个 PromptService。产品域要换存储时替换这个类。
 */
@Service
public class PromptAppService implements PromptService {

    private static final Logger log = Logger.getLogger(PromptAppService.class.getName());

    private final PromptSource source;

    public PromptAppService(PromptSource source) {
        this.source = source;
    }

    /**
     * 先取外部模板再替换 {{变量}}。Nacos 抖动不能让调用失败，也不能把空串交给模型。
     * fallback 本身为空直接拒绝，避免用空提示词冒充兜底。
     */
    @Override
    public String get(String key, Map<String, String> variables, String fallback) {
        if (fallback == null || fallback.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "prompt fallback is required");
        }
        if (key == null || key.isBlank()) {
            return fallback;
        }
        try {
            String rendered = render(source.load(key), variables);
            if (rendered == null || rendered.isBlank()) {
                return fallback;
            }
            return rendered;
        } catch (Exception error) {
            log.warning("prompt fetch failed, key=" + key + ", using fallback");
            return fallback;
        }
    }

    /** 只替换调用方给出的变量。没写到的占位符留着，方便发现模板和参数对不上。 */
    private static String render(String template, Map<String, String> variables) {
        if (template == null || variables == null || variables.isEmpty()) {
            return template;
        }
        String rendered = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }
            String value = entry.getValue() == null ? "" : entry.getValue();
            rendered = rendered.replace("{{" + entry.getKey() + "}}", value);
        }
        return rendered;
    }
}
