package com.zhu.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.zhu.access.application.attachment.AttachmentDocumentReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * 附件正文抽取：纯文本与截断。
 */
class AttachmentDocumentReaderTest {

    @Test
    void readsPlainText() {
        String text = AttachmentDocumentReader.extract(
                "note.txt", "text/plain", "hello-attachment".getBytes(StandardCharsets.UTF_8));
        assertThat(text).isEqualTo("hello-attachment");
    }

    @Test
    void truncatesLongText() {
        StringBuilder builder = new StringBuilder();
        while (builder.length() < AttachmentDocumentReader.MAX_CHARS + 100) {
            builder.append("abcdefghij");
        }
        String text = AttachmentDocumentReader.extract(
                "big.txt", "text/plain", builder.toString().getBytes(StandardCharsets.UTF_8));
        assertThat(text).endsWith("…（已截断）");
        assertThat(text.length()).isLessThanOrEqualTo(AttachmentDocumentReader.MAX_CHARS + 20);
    }

    @Test
    void emptyBytesExplains() {
        assertThat(AttachmentDocumentReader.extract("a.txt", "text/plain", new byte[0]))
                .contains("空");
    }
}
