package com.zhu.bootstrap;

import com.zhu.audit.application.MysqlAttachmentCatalog;
import com.zhu.audit.infrastructure.persistence.AttachmentMapper;
import com.zhu.audit.infrastructure.persistence.AttachmentRefMapper;
import com.zhu.audit.infrastructure.persistence.entity.Attachment;
import com.zhu.audit.infrastructure.persistence.entity.AttachmentRef;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 附件只登记元数据和引用。不连接 MySQL，也不收文件。
 */
class AttachmentCatalogTest {

    /** 空白租户按空串。空白存储类型按 LOCAL。不校验路径上有没有文件。 */
    @Test
    void registerStoresMetadataOnly() {
        AttachmentMapper attachments = mock(AttachmentMapper.class);
        MysqlAttachmentCatalog catalog = new MysqlAttachmentCatalog(attachments, mock(AttachmentRefMapper.class));

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

    /** 只挂自己的附件。别人的或没有的按找不到。同一引用再挂一次是冲突。 */
    @Test
    void linkRequiresOwnedAttachment() {
        AttachmentMapper attachments = mock(AttachmentMapper.class);
        AttachmentRefMapper refs = mock(AttachmentRefMapper.class);
        MysqlAttachmentCatalog catalog = new MysqlAttachmentCatalog(attachments, refs);
        Attachment owned = new Attachment();
        owned.setAttachmentId("att-1");
        owned.setUserId("u");
        when(attachments.findByAttachmentId("att-1")).thenReturn(owned);

        assertThat(catalog.link("u", "att-1", "RUN", "run-1")).satisfies(saved -> {
            assertThat(saved.attachmentId()).isEqualTo("att-1");
            assertThat(saved.refType()).isEqualTo("RUN");
            assertThat(saved.refId()).isEqualTo("run-1");
        });
        ArgumentCaptor<AttachmentRef> saved = ArgumentCaptor.forClass(AttachmentRef.class);
        verify(refs).insert(saved.capture());
        assertThat(saved.getValue().getRefType()).isEqualTo("RUN");

        Attachment other = new Attachment();
        other.setUserId("other");
        when(attachments.findByAttachmentId("att-2")).thenReturn(other);
        assertThatThrownBy(() -> catalog.link("u", "att-2", "MESSAGE", "m-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(refs, times(1)).insert(any(AttachmentRef.class));

        when(refs.insert(any(AttachmentRef.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("dup"));
        assertThatThrownBy(() -> catalog.link("u", "att-1", "RUN", "run-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }
}
