package com.zhu.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zhu.ops.attachment.AttachmentCatalog;
import com.zhu.ops.attachment.StoredAttachment;
import com.zhu.access.api.dto.attachment.AttachmentContent;
import com.zhu.access.application.attachment.AttachmentAppService;
import com.zhu.access.application.chat.ChatAttachmentSupport;
import com.zhu.common.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 聊天随消息附件：默认句、白名单与搭子限定。
 */
class ChatAttachmentSupportTest {

    @Test
    void emptyTextWithAttachmentsUsesDefaultSentence() {
        assertThat(ChatAttachmentSupport.resolveUserText("  ", true))
                .isEqualTo(ChatAttachmentSupport.DEFAULT_ATTACHMENT_TEXT);
        assertThat(ChatAttachmentSupport.resolveUserText("你好", true)).isEqualTo("你好");
        assertThat(ChatAttachmentSupport.resolveUserText("  ", false)).isEmpty();
    }

    @Test
    void nonCompanionRejectsAttachments() {
        AttachmentCatalog catalog = mock(AttachmentCatalog.class);
        ChatAttachmentSupport support = support(catalog, mock(AttachmentAppService.class));
        assertThatThrownBy(() -> support.resolve("echo-v1", "u1", List.of("a1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("companion");
    }

    @Test
    void loadsImageAsBase64Part() {
        AttachmentCatalog catalog = mock(AttachmentCatalog.class);
        AttachmentAppService uploads = mock(AttachmentAppService.class);
        when(catalog.owned("u1", "img-1"))
                .thenReturn(new StoredAttachment(
                        "img-1", "t", "u1", "a.png", "att/t/u1/x/a.png", "image/png", "ACTIVE"));
        when(uploads.download("u1", "img-1"))
                .thenReturn(new AttachmentContent("a.png", "image/png", new byte[] {1, 2, 3}));
        ChatAttachmentSupport.Resolved resolved =
                support(catalog, uploads).resolve("companion-v1", "u1", List.of("img-1"));
        assertThat(resolved.attachmentIds()).containsExactly("img-1");
        assertThat(resolved.images()).hasSize(1);
        assertThat(resolved.images().get(0).mimeType()).isEqualTo("image/png");
        assertThat(resolved.images().get(0).base64Data()).isNotBlank();
    }

    @Test
    void documentIsAcceptedWithoutImagePart() {
        AttachmentCatalog catalog = mock(AttachmentCatalog.class);
        when(catalog.owned("u1", "doc-1"))
                .thenReturn(new StoredAttachment(
                        "doc-1", "t", "u1", "a.txt", "att/t/u1/x/a.txt", "text/plain", "ACTIVE"));
        ChatAttachmentSupport.Resolved resolved =
                support(catalog, mock(AttachmentAppService.class)).resolve(null, "u1", List.of("doc-1"));
        assertThat(resolved.attachmentIds()).containsExactly("doc-1");
        assertThat(resolved.images()).isEmpty();
    }

    @Test
    void rejectsUnsupportedType() {
        AttachmentCatalog catalog = mock(AttachmentCatalog.class);
        when(catalog.owned("u1", "bin-1"))
                .thenReturn(new StoredAttachment(
                        "bin-1", "t", "u1", "a.exe", "att/t/u1/x/a.exe", "application/octet-stream", "ACTIVE"));
        assertThatThrownBy(() -> support(catalog, mock(AttachmentAppService.class))
                        .resolve("companion-v1", "u1", List.of("bin-1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not allowed");
    }

    @SuppressWarnings("unchecked")
    private static ChatAttachmentSupport support(AttachmentCatalog catalog, AttachmentAppService uploads) {
        ObjectProvider<AttachmentCatalog> catalogs = mock(ObjectProvider.class);
        when(catalogs.getIfAvailable()).thenReturn(catalog);
        return new ChatAttachmentSupport(catalogs, uploads);
    }
}
