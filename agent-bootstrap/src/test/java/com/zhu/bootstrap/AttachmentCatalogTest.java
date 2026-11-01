package com.zhu.bootstrap;

import com.zhu.audit.application.MysqlAttachmentCatalog;
import com.zhu.audit.infrastructure.persistence.AttachmentMapper;
import com.zhu.audit.infrastructure.persistence.entity.Attachment;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 附件只登记元数据。不连接 MySQL，也不收文件。
 */
class AttachmentCatalogTest {

    /** 空白租户按空串。空白存储类型按 LOCAL。不校验路径上有没有文件。 */
    @Test
    void registerStoresMetadataOnly() {
        AttachmentMapper attachments = mock(AttachmentMapper.class);
        MysqlAttachmentCatalog catalog = new MysqlAttachmentCatalog(attachments);

        assertThatThrownBy(() -> catalog.register("u", "t", null, "a.txt", "DISK", "/a", null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);

        assertThat(catalog.register("u", " ", " ", "a.txt", " ", "/tmp/a.txt", " ", null, " ")).satisfies(saved -> {
            assertThat(saved.attachmentId()).isNotBlank();
            assertThat(saved.tenantId()).isEmpty();
            assertThat(saved.sessionId()).isNull();
            assertThat(saved.storageType()).isEqualTo("LOCAL");
            assertThat(saved.status()).isEqualTo("ACTIVE");
        });
        ArgumentCaptor<Attachment> saved = ArgumentCaptor.forClass(Attachment.class);
        verify(attachments).insert(saved.capture());
        assertThat(saved.getValue().getSizeBytes()).isEqualTo(0L);
        assertThat(saved.getValue().getChecksum()).isNull();
        assertThat(saved.getValue().getMimeType()).isNull();
    }
}
