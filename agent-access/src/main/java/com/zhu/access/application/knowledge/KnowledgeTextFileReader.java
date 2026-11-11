package com.zhu.access.application.knowledge;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * 接入层：把知识库上传的文本文件字节解成 UTF-8 正文。
 * 不解析 PDF/Office；不接对象存储。二进制或超限在这里拒绝。
 */
public final class KnowledgeTextFileReader {

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "txt", "md", "markdown", "csv", "json", "log", "yml", "yaml", "xml");

    private KnowledgeTextFileReader() {
    }

    /**
     * 校验类型与大小后按 UTF-8 解码。含 NUL 视为非文本。
     *
     * @param fileName    原始文件名，可空；扩展名用于兜底判定
     * @param contentType Content-Type，可空
     * @param bytes       文件字节，不可空
     * @param maxBytes    上限（含），与知识库正文上限对齐
     * @return 非空白正文
     */
    public static String readUtf8(String fileName, String contentType, byte[] bytes, int maxBytes) {
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "file is empty");
        }
        if (bytes.length > maxBytes) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "content exceeds 2MB");
        }
        if (!isAllowedText(fileName, contentType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "only plain text files are accepted");
        }
        for (byte b : bytes) {
            if (b == 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "binary file is not accepted");
            }
        }
        String text = new String(bytes, StandardCharsets.UTF_8).trim();
        if (text.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "file content is blank");
        }
        return text;
    }

    /**
     * 标题缺省时用文件名（去掉扩展名）；都空则返回 null。
     */
    public static String defaultTitle(String title, String fileName) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        String name = fileName.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < name.length()) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            return name.substring(0, dot);
        }
        return name;
    }

    private static boolean isAllowedText(String fileName, String contentType) {
        if (contentType != null && !contentType.isBlank()) {
            String mime = contentType.trim().toLowerCase(Locale.ROOT);
            int semi = mime.indexOf(';');
            if (semi > 0) {
                mime = mime.substring(0, semi).trim();
            }
            if (mime.startsWith("text/")
                    || "application/json".equals(mime)
                    || "application/xml".equals(mime)
                    || "application/x-yaml".equals(mime)
                    || "application/yaml".equals(mime)) {
                return true;
            }
        }
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        String name = fileName.trim().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return false;
        }
        return TEXT_EXTENSIONS.contains(name.substring(dot + 1));
    }
}
