package com.zhu.bootstrap;

import com.zhu.access.application.knowledge.KnowledgeAppService;
import com.zhu.access.application.knowledge.KnowledgeTextFileReader;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 知识库文件只收 UTF-8 纯文本。不连库。
 */
class KnowledgeTextFileReaderTest {

    @Test
    void readsAllowedText() {
        assertThat(KnowledgeTextFileReader.readUtf8(
                        "a.md", "text/markdown", "你好".getBytes(StandardCharsets.UTF_8), 100))
                .isEqualTo("你好");
        assertThat(KnowledgeTextFileReader.defaultTitle(null, "path/readme.md")).isEqualTo("readme");
    }

    @Test
    void rejectsBinaryOrUnknownType() {
        assertThatThrownBy(() -> KnowledgeTextFileReader.readUtf8(
                        "a.bin", "application/octet-stream", new byte[] {1, 0, 2}, 100))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        assertThatThrownBy(() -> KnowledgeTextFileReader.readUtf8(
                        "a.pdf",
                        "application/pdf",
                        "x".getBytes(StandardCharsets.UTF_8),
                        KnowledgeAppService.MAX_CONTENT_BYTES))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }
}
