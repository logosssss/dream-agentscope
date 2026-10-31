package com.zhu.semantic.config;

import com.zhu.semantic.contract.CrisisPrompt;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/**
 * 从 classpath 读危机话术。不访问 Nacos，也不走 PromptSource。
 */
@Component
public class ClasspathCrisisPrompt implements CrisisPrompt {

    private static final String RESOURCE = "/prompts/prompt.safety.crisis.md";

    private final String text;

    /** 启动时读一次。文件没有或空白时用代码兜底，避免空提示词进模型。 */
    public ClasspathCrisisPrompt() {
        this.text = load();
    }

    /** 已经收成非空正文。 */
    @Override
    public String text() {
        return text;
    }

    /**
     * 读失败或空白都退回兜底。换行统一成 LF，避免 Windows 检出和代码常量对不上。
     */
    private static String load() {
        try (InputStream in = ClasspathCrisisPrompt.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return FALLBACK;
            }
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n")
                    .replace('\r', '\n')
                    .strip();
            return body.isBlank() ? FALLBACK : body;
        } catch (IOException ex) {
            return FALLBACK;
        }
    }
}
