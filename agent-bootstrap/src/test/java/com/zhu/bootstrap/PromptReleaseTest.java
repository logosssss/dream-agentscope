package com.zhu.bootstrap;

import com.sun.net.httpserver.HttpServer;
import com.zhu.semantic.infrastructure.nacos.NacosPromptPublisher;
import com.zhu.semantic.infrastructure.prompt.PromptRelease;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 提示词先校验再发布。假接口在本机，不连接外部 Nacos。
 */
class PromptReleaseTest {

    /** 校验失败时发布端收不到正文。通过后原文原样送出。 */
    @Test
    void invalidTemplateIsNotPublished(@TempDir Path directory) throws IOException {
        List<String> published = new ArrayList<>();
        Files.writeString(directory.resolve("prompt.platform.system.md"), "你好 {{name}}");
        assertThatThrownBy(() -> PromptRelease.publish(directory, (key, body) -> published.add(body)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("prompt variable is unknown");
        assertThat(published).isEmpty();

        Files.writeString(directory.resolve("prompt.platform.system.md"), "你好");
        PromptRelease.publish(directory, (key, body) -> published.add(key + "=" + body));
        assertThat(published).containsExactly("prompt.platform.system=你好");
    }

    /** 危机话术通过校验，但不交给发布端。 */
    @Test
    void crisisPromptIsNotPublished(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("prompt.companion.system.md"), "搭子");
        Files.writeString(directory.resolve("prompt.safety.crisis.md"), "危机");
        List<String> published = new ArrayList<>();
        PromptRelease.publish(directory, (key, body) -> published.add(key + "=" + body));
        assertThat(published).containsExactly("prompt.companion.system=搭子");
    }

    /** 发布打到草稿、提交、上线三步。模板出现在草稿里，不出现在失败信息里。 */
    @Test
    void publishPostsDraftSubmitAndRelease(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("prompt.platform.system.md"), "你好");
        List<String> paths = new ArrayList<>();
        List<String> bodies = new ArrayList<>();
        AtomicInteger hits = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            hits.incrementAndGet();
            paths.add(exchange.getRequestURI().getPath());
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"code\":0}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(response);
            }
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            Clock clock = Clock.fixed(Instant.ofEpochSecond(1_700_000_000L), ZoneOffset.UTC);
            NacosPromptPublisher publisher = new NacosPromptPublisher(
                    "127.0.0.1:" + port, "", "", "", clock);
            publisher.release(directory);
        } finally {
            server.stop(0);
        }
        assertThat(hits).hasValue(3);
        assertThat(paths).containsExactly(
                "/nacos/v3/admin/ai/prompt/draft",
                "/nacos/v3/admin/ai/prompt/submit",
                "/nacos/v3/admin/ai/prompt/publish");
        assertThat(bodies.get(0)).contains("promptKey=prompt.platform.system");
        assertThat(bodies.get(0)).contains("targetVersion=1.0.1700000000");
        assertThat(bodies.get(0)).contains("template=");
        assertThat(bodies.get(1)).doesNotContain("template=");
    }
}
