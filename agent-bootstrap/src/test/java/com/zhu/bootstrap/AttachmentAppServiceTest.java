package com.zhu.bootstrap;

import com.zhu.access.application.AttachmentAppService;
import com.zhu.audit.api.AttachmentCatalog;
import com.zhu.audit.api.AttachmentSummary;
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
 * 通用附件先写对象存储再登记。目录不可用或超限时不写对象。
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

    private static AttachmentAppService service(AttachmentCatalog catalog, ObjectStore objects) {
        @SuppressWarnings("unchecked")
        ObjectProvider<AttachmentCatalog> catalogs = mock(ObjectProvider.class);
        when(catalogs.getIfAvailable()).thenReturn(catalog);
        return new AttachmentAppService(catalogs, objects);
    }
}
