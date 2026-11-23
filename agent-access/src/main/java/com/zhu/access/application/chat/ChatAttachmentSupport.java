package com.zhu.access.application.chat;

import com.zhu.ops.attachment.AttachmentCatalog;
import com.zhu.ops.attachment.StoredAttachment;
import com.zhu.access.api.dto.attachment.AttachmentContent;
import com.zhu.access.application.attachment.AttachmentAppService;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.runtime.api.agent.AgentImagePart;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 接入层：聊天随消息附件的校验、默认句与本轮图片装载。
 * 只服务 WEB 搭子；不负责 MESSAGE 引用写入。
 */
@Component
public class ChatAttachmentSupport {

    /** 仅附件无正文时补的用户意图。 */
    public static final String DEFAULT_ATTACHMENT_TEXT = "请查看我发送的附件。";

    public static final int MAX_PER_MESSAGE = 3;

    private static final String COMPANION_AGENT = "companion-v1";

    private static final Set<String> IMAGE_MIME = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif");

    private static final Set<String> DOC_EXT = Set.of(
            "pdf", "docx", "txt", "md", "markdown", "csv", "json", "log", "yml", "yaml", "xml");

    private final ObjectProvider<AttachmentCatalog> catalogs;

    private final AttachmentAppService uploads;

    public ChatAttachmentSupport(
            ObjectProvider<AttachmentCatalog> catalogs, AttachmentAppService uploads) {
        this.catalogs = catalogs;
        this.uploads = uploads;
    }

    /**
     * 解析本轮附件：校验白名单与归属，装载图片 Base64；文档只验存在。
     *
     * @return 规范化后的附件 id（去重保序）与本轮图片 parts
     */
    public Resolved resolve(String agentId, String userId, List<String> attachmentIds) {
        List<String> ids = normalizeIds(attachmentIds);
        if (ids.isEmpty()) {
            return Resolved.EMPTY;
        }
        if (!isCompanion(agentId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "attachments are only allowed for companion chat");
        }
        AttachmentCatalog catalog = catalogs.getIfAvailable();
        if (catalog == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "attachment catalog is unavailable");
        }
        List<AgentImagePart> images = new ArrayList<>();
        for (String attachmentId : ids) {
            StoredAttachment owned = catalog.owned(userId, attachmentId);
            if (!"ACTIVE".equalsIgnoreCase(owned.status())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "attachment is not active");
            }
            String mime = normalizeMime(owned.mimeType());
            String name = owned.fileName() == null ? "" : owned.fileName();
            if (isImage(mime, name)) {
                AttachmentContent content = uploads.download(userId, attachmentId);
                String data = Base64.getEncoder().encodeToString(content.content());
                String useMime = mime.isEmpty() ? "image/jpeg" : mime;
                images.add(new AgentImagePart(attachmentId, useMime, data));
            } else if (!isDocument(mime, name)) {
                throw new BusinessException(
                        ErrorCode.BAD_REQUEST,
                        "attachment type is not allowed for chat");
            }
        }
        return new Resolved(ids, List.copyOf(images));
    }

    /** 空正文且有附件时补默认句；否则 trim 后的原文。 */
    public static String resolveUserText(String text, boolean hasAttachments) {
        String trimmed = text == null ? "" : text.trim();
        if (!trimmed.isEmpty()) {
            return trimmed;
        }
        if (hasAttachments) {
            return DEFAULT_ATTACHMENT_TEXT;
        }
        return "";
    }

    /** MESSAGE 引用 id：sessionId:seq，历史回填用同一规则。 */
    public static String messageRefId(String sessionId, long seq) {
        return (sessionId == null ? "" : sessionId) + ":" + seq;
    }

    private static boolean isCompanion(String agentId) {
        return agentId == null || agentId.isBlank() || COMPANION_AGENT.equals(agentId.trim());
    }

    private static List<String> normalizeIds(List<String> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String raw : attachmentIds) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            unique.add(raw.trim());
            if (unique.size() > MAX_PER_MESSAGE) {
                throw new BusinessException(
                        ErrorCode.BAD_REQUEST, "at most " + MAX_PER_MESSAGE + " attachments per message");
            }
        }
        return List.copyOf(unique);
    }

    private static boolean isImage(String mime, String fileName) {
        if (IMAGE_MIME.contains(mime)) {
            return true;
        }
        String ext = extension(fileName);
        return Set.of("jpg", "jpeg", "png", "webp", "gif").contains(ext);
    }

    private static boolean isDocument(String mime, String fileName) {
        if (mime.startsWith("text/")
                || "application/json".equals(mime)
                || "application/xml".equals(mime)
                || "application/yaml".equals(mime)
                || "application/x-yaml".equals(mime)
                || "application/pdf".equals(mime)
                || "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(mime)) {
            return true;
        }
        return DOC_EXT.contains(extension(fileName));
    }

    private static String normalizeMime(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return "";
        }
        String mime = mimeType.trim().toLowerCase(Locale.ROOT);
        int semi = mime.indexOf(';');
        if (semi > 0) {
            mime = mime.substring(0, semi).trim();
        }
        return mime;
    }

    private static String extension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        String name = fileName.trim().toLowerCase(Locale.ROOT);
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < name.length()) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1);
    }

    /**
     * 本轮已校验的附件结果。
     *
     * @param attachmentIds 去重后的 id
     * @param images        本轮要进多模态的图
     */
    public record Resolved(List<String> attachmentIds, List<AgentImagePart> images) {
        static final Resolved EMPTY = new Resolved(List.of(), List.of());

        public Resolved {
            attachmentIds = attachmentIds == null ? List.of() : List.copyOf(attachmentIds);
            images = images == null ? List.of() : List.copyOf(images);
        }

        public boolean isEmpty() {
            return attachmentIds.isEmpty();
        }
    }
}
