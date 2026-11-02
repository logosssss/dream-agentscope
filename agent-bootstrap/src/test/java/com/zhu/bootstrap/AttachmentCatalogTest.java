package com.zhu.bootstrap;

import com.zhu.audit.api.StoredAttachment;
import com.zhu.audit.application.MysqlAttachmentCatalog;
import com.zhu.audit.infrastructure.persistence.AttachmentMapper;
import com.zhu.audit.infrastructure.persistence.AttachmentRefMapper;
import com.zhu.audit.infrastructure.persistence.entity.Attachment;
import com.zhu.audit.infrastructure.persistence.entity.AttachmentRef;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        assertThat(catalog.link("u", "att-1", "DOCUMENT", "doc-1")).satisfies(saved -> {
            assertThat(saved.refType()).isEqualTo("DOCUMENT");
            assertThat(saved.refId()).isEqualTo("doc-1");
        });
        ArgumentCaptor<AttachmentRef> saved = ArgumentCaptor.forClass(AttachmentRef.class);
        verify(refs, times(2)).insert(saved.capture());
        assertThat(saved.getAllValues()).extracting(AttachmentRef::getRefType).containsExactly("RUN", "DOCUMENT");

        Attachment other = new Attachment();
        other.setUserId("other");
        when(attachments.findByAttachmentId("att-2")).thenReturn(other);
        assertThatThrownBy(() -> catalog.link("u", "att-2", "MESSAGE", "m-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(refs, times(2)).insert(any(AttachmentRef.class));

        when(refs.insert(any(AttachmentRef.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("dup"));
        assertThatThrownBy(() -> catalog.link("u", "att-1", "RUN", "run-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    /** 只返回本人本租户挂到该引用上的路径。类型不认识时空列表。 */
    @Test
    void storagePathsListsOwnedDocumentKeys() {
        AttachmentMapper attachments = mock(AttachmentMapper.class);
        MysqlAttachmentCatalog catalog = new MysqlAttachmentCatalog(attachments, mock(AttachmentRefMapper.class));
        when(attachments.listStoragePathsByRef("u", "t", "DOCUMENT", "d1"))
                .thenReturn(List.of("kb/t/u/d1/v1/a.txt"));
        assertThat(catalog.storagePaths("u", "t", "DOCUMENT", "d1")).containsExactly("kb/t/u/d1/v1/a.txt");
        assertThat(catalog.storagePaths("u", "t", "DISK", "d1")).isEmpty();
        verify(attachments, times(1)).listStoragePathsByRef("u", "t", "DOCUMENT", "d1");
    }

    /** 本人启用的行才能取到路径和文件名。 */
    @Test
    void ownedReturnsActiveRow() {
        AttachmentMapper attachments = mock(AttachmentMapper.class);
        MysqlAttachmentCatalog catalog = new MysqlAttachmentCatalog(attachments, mock(AttachmentRefMapper.class));
        when(attachments.findStored("att-1")).thenReturn(storedRow(
                "att-1", "t", "u", "a.txt", "att/t/u/att-1/a.txt", "text/plain", "ACTIVE"));
        StoredAttachment stored = catalog.owned("u", "att-1");
        assertThat(stored.fileName()).isEqualTo("a.txt");
        assertThat(stored.storagePath()).isEqualTo("att/t/u/att-1/a.txt");
        assertThat(stored.mimeType()).isEqualTo("text/plain");
    }

    /** 别人的或停用的按找不到，避免把路径漏出去。 */
    @Test
    void ownedRejectsForeignAndInactive() {
        AttachmentMapper attachments = mock(AttachmentMapper.class);
        MysqlAttachmentCatalog catalog = new MysqlAttachmentCatalog(attachments, mock(AttachmentRefMapper.class));
        when(attachments.findStored("att-2")).thenReturn(storedRow(
                "att-2", "t", "other", "a.txt", "att/t/other/att-2/a.txt", "text/plain", "ACTIVE"));
        when(attachments.findStored("att-3")).thenReturn(storedRow(
                "att-3", "t", "u", "a.txt", "att/t/u/att-3/a.txt", "text/plain", "DELETED"));
        when(attachments.findStored("missing")).thenReturn(null);
        assertThatThrownBy(() -> catalog.owned("u", "att-2"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        assertThatThrownBy(() -> catalog.owned("u", "att-3"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        assertThatThrownBy(() -> catalog.owned("u", "missing"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    /** 空编号按请求错误，不查库。 */
    @Test
    void ownedRejectsBlankIds() {
        AttachmentMapper attachments = mock(AttachmentMapper.class);
        MysqlAttachmentCatalog catalog = new MysqlAttachmentCatalog(attachments, mock(AttachmentRefMapper.class));
        assertThatThrownBy(() -> catalog.owned("u", " "))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        verify(attachments, never()).findStored(any());
    }

    /** 给 Mapper 桩用的一行。只填 owned 会读的字段。 */
    private static Attachment storedRow(
            String id, String tenant, String user, String name, String path, String mime, String status) {
        Attachment row = new Attachment();
        row.setAttachmentId(id);
        row.setTenantId(tenant);
        row.setUserId(user);
        row.setFileName(name);
        row.setStoragePath(path);
        row.setMimeType(mime);
        row.setStatus(status);
        return row;
    }
}
