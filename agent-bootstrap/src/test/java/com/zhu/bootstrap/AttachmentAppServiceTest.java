package com.zhu.bootstrap;

import com.zhu.access.api.dto.AttachmentContent;
import com.zhu.access.application.AttachmentAppService;
import com.zhu.audit.api.AttachmentCatalog;
import com.zhu.audit.api.AttachmentSummary;
import com.zhu.audit.api.StoredAttachment;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.objectstore.api.ObjectStore;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 通用附件先写对象存储再登记。下载只读本人对象键。
 */
class AttachmentAppServiceTest {

    /** 写入后按实现回写 storage_type，路径落在 att/ 下。 */
    @Test
    void uploadPutsThenRegisters() {
        AttachmentCatalog catalog = mock(AttachmentCatalog.class);
        ObjectStore objects = mock(ObjectStore.class);
        when(objects.storageType()).thenReturn("OSS");
        when(objects.put(anyString(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(catalog.register(
                        eq("u"),
                        eq("t"),
                        eq("s1"),
                        eq("note.txt"),
                        eq("OSS"),
                        anyString(),
                        eq("text/plain"),
                        eq(5L),
                        isNull()))
                .thenReturn(new AttachmentSummary("att-1", "t", "u", "s1", "note.txt", "OSS", "att/t/u/x/note.txt", "ACTIVE"));

        AttachmentSummary saved = service(catalog, objects)
                .upload("u", "t", "s1", "note.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));

        assertThat(saved.attachmentId()).isEqualTo("att-1");
        assertThat(saved.storageType()).isEqualTo("OSS");
        verify(objects).put(anyString(), any());
        verify(catalog).register(
                eq("u"),
                eq("t"),
                eq("s1"),
                eq("note.txt"),
                eq("OSS"),
                anyString(),
                eq("text/plain"),
                eq(5L),
                isNull());
    }

    /** 没有目录时不碰对象存储。 */
    @Test
    void missingCatalogDoesNotPut() {
        ObjectStore objects = mock(ObjectStore.class);
        assertThatThrownBy(() -> service(null, objects).upload("u", "t", null, "a.txt", null, new byte[] {1}))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.UNAVAILABLE);
        verify(objects, never()).put(anyString(), any());
    }

    /** 空文件和超限都拒绝。 */
    @Test
    void rejectsEmptyAndOversized() {
        AttachmentCatalog catalog = mock(AttachmentCatalog.class);
        ObjectStore objects = mock(ObjectStore.class);
        AttachmentAppService service = service(catalog, objects);
        assertThatThrownBy(() -> service.upload("u", "t", null, "a.txt", null, new byte[0]))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        assertThatThrownBy(() ->
                        service.upload("u", "t", null, "a.bin", null, new byte[AttachmentAppService.MAX_BYTES + 1]))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        verify(objects, never()).put(anyString(), any());
        verify(catalog, never())
                .register(any(), any(), any(), any(), any(), any(), any(), anyLong(), any());
    }

    /** 登记失败时删掉刚写入的对象，避免孤儿文件。 */
    @Test
    void registerFailureDeletesObject() {
        AttachmentCatalog catalog = mock(AttachmentCatalog.class);
        ObjectStore objects = mock(ObjectStore.class);
        when(objects.storageType()).thenReturn("LOCAL");
        when(objects.put(anyString(), any())).thenReturn("att/t/u/id/a.txt");
        when(catalog.register(any(), any(), any(), any(), any(), any(), any(), anyLong(), any()))
                .thenThrow(new BusinessException(ErrorCode.BAD_REQUEST, "attachment is invalid"));
        assertThatThrownBy(() -> service(catalog, objects).upload("u", "t", null, "a.txt", null, new byte[] {1}))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        verify(objects).delete("att/t/u/id/a.txt");
    }

    /** 路径分隔会被清掉，避免对象键穿越。 */
    @Test
    void objectKeySanitizesFileName() {
        AttachmentCatalog catalog = mock(AttachmentCatalog.class);
        ObjectStore objects = mock(ObjectStore.class);
        when(objects.storageType()).thenReturn("LOCAL");
        when(objects.put(anyString(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(catalog.register(any(), any(), any(), any(), any(), any(), any(), anyLong(), any()))
                .thenReturn(new AttachmentSummary("att-1", "t", "u", null, "x", "LOCAL", "p", "ACTIVE"));
        service(catalog, objects).upload("u", "t", null, "../x.txt", null, new byte[] {1});
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(objects).put(key.capture(), any());
        assertThat(key.getValue()).startsWith("att/t/u/");
        assertThat(key.getValue()).endsWith("/.._x.txt");
    }

    /** 本人对象键才能读字节。 */
    @Test
    void downloadReturnsOwnedBytes() {
        AttachmentCatalog catalog = mock(AttachmentCatalog.class);
        ObjectStore objects = mock(ObjectStore.class);
        when(catalog.owned("u", "att-1")).thenReturn(new StoredAttachment(
                "att-1", "t", "u", "a.txt", "att/t/u/att-1/a.txt", "text/plain", "ACTIVE"));
        when(objects.get("att/t/u/att-1/a.txt")).thenReturn(new byte[] {1, 2});
        AttachmentContent content = service(catalog, objects).download("u", "att-1");
        assertThat(content.fileName()).isEqualTo("a.txt");
        assertThat(content.mimeType()).isEqualTo("text/plain");
        assertThat(content.content()).containsExactly(1, 2);
    }

    /** 知识库对象键也允许本人读。 */
    @Test
    void downloadAllowsKnowledgeKey() {
        AttachmentCatalog catalog = mock(AttachmentCatalog.class);
        ObjectStore objects = mock(ObjectStore.class);
        when(catalog.owned("u", "att-1")).thenReturn(new StoredAttachment(
                "att-1", "t", "u", "a.txt", "kb/t/u/d1/v1/a.txt", "text/plain", "ACTIVE"));
        when(objects.get("kb/t/u/d1/v1/a.txt")).thenReturn(new byte[] {9});
        assertThat(service(catalog, objects).download("u", "att-1").content()).containsExactly(9);
    }

    /** 空租户登记时对象键用下划线，下载按同一规则认。 */
    @Test
    void downloadAllowsBlankTenantKey() {
        AttachmentCatalog catalog = mock(AttachmentCatalog.class);
        ObjectStore objects = mock(ObjectStore.class);
        when(catalog.owned("u", "att-1")).thenReturn(new StoredAttachment(
                "att-1", "", "u", "a.txt", "att/_/u/att-1/a.txt", "text/plain", "ACTIVE"));
        when(objects.get("att/_/u/att-1/a.txt")).thenReturn(new byte[] {3});
        assertThat(service(catalog, objects).download("u", "att-1").content()).containsExactly(3);
    }

    /** 别人的行目录会拦，对象存储不会被读。 */
    @Test
    void downloadDoesNotFetchForeignRow() {
        AttachmentCatalog catalog = mock(AttachmentCatalog.class);
        ObjectStore objects = mock(ObjectStore.class);
        when(catalog.owned("u", "att-2"))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "attachment not found"));
        assertThatThrownBy(() -> service(catalog, objects).download("u", "att-2"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(objects, never()).get(anyString());
    }

    /** JSON 声称的盘路径或别人的对象键不读。 */
    @Test
    void downloadRejectsForeignPrefix() {
        AttachmentCatalog catalog = mock(AttachmentCatalog.class);
        ObjectStore objects = mock(ObjectStore.class);
        when(catalog.owned("u", "att-1")).thenReturn(new StoredAttachment(
                "att-1", "t", "u", "a.txt", "C:/secret.bin", "text/plain", "ACTIVE"));
        assertThatThrownBy(() -> service(catalog, objects).download("u", "att-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(objects, never()).get(anyString());
    }

    /** 路径里带 .. 的不读，避免跳出自己的前缀。 */
    @Test
    void downloadRejectsDotDotPath() {
        AttachmentCatalog catalog = mock(AttachmentCatalog.class);
        ObjectStore objects = mock(ObjectStore.class);
        when(catalog.owned("u", "att-1")).thenReturn(new StoredAttachment(
                "att-1", "t", "u", "a.txt", "att/t/u/../secret.bin", "text/plain", "ACTIVE"));
        assertThatThrownBy(() -> service(catalog, objects).download("u", "att-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(objects, never()).get(anyString());
    }

    /** 对象不存在按找不到。 */
    @Test
    void downloadMissingObjectIsNotFound() {
        AttachmentCatalog catalog = mock(AttachmentCatalog.class);
        ObjectStore objects = mock(ObjectStore.class);
        when(catalog.owned("u", "att-1")).thenReturn(new StoredAttachment(
                "att-1", "t", "u", "a.txt", "att/t/u/att-1/a.txt", "text/plain", "ACTIVE"));
        when(objects.get("att/t/u/att-1/a.txt")).thenReturn(null);
        assertThatThrownBy(() -> service(catalog, objects).download("u", "att-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    /** 目录没配时不读对象。 */
    @Test
    void downloadWithoutCatalogIsUnavailable() {
        ObjectStore objects = mock(ObjectStore.class);
        assertThatThrownBy(() -> service(null, objects).download("u", "att-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.UNAVAILABLE);
        verify(objects, never()).get(anyString());
    }

    private static AttachmentAppService service(AttachmentCatalog catalog, ObjectStore objects) {
        @SuppressWarnings("unchecked")
        ObjectProvider<AttachmentCatalog> catalogs = mock(ObjectProvider.class);
        when(catalogs.getIfAvailable()).thenReturn(catalog);
        return new AttachmentAppService(catalogs, objects);
    }
}
