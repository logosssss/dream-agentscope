package com.zhu.semantic.infrastructure.prompt;

import com.zhu.semantic.api.PlatformPrompt;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * 把目录里的提示词先校验，再交给发布端。目录不存在就跳过。
 * 危机话术会校验，但不发布。不在进程启动时调用，也不连接 Nacos。
 * 校验失败时发布端不会收到任何一条。
 */
public final class PromptRelease {

    private PromptRelease() {
    }

    /**
     * 先跑整目录校验。通过后按文件名顺序发布原文，不把占位符换成 sample。
     * 发布端抛出的异常原样传出，避免校验过的文件被悄悄丢掉。
     */
    public static void publish(Path directory, PromptSink sink) {
        if (sink == null) {
            throw new IllegalArgumentException("prompt sink is required");
        }
        PromptTemplateCheck.checkDirectory(directory);
        if (directory == null || !Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                String key = PromptTemplateCheck.fileKey(file.getFileName().toString());
                if (!PlatformPrompt.publishable(key)) {
                    continue;
                }
                String body = Files.readString(file, StandardCharsets.UTF_8);
                sink.publish(key, body);
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("prompt file is invalid");
        }
    }
}
