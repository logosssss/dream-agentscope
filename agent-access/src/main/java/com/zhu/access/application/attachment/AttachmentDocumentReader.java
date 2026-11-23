package com.zhu.access.application.attachment;

import com.zhu.access.application.knowledge.KnowledgeTextFileReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

/**
 * 接入层：聊天附件正文抽取。支持纯文本、PDF、docx；扫描 PDF 不 OCR。
 * 超长截断到 {@link #MAX_CHARS}，并注明已截断。
 */
public final class AttachmentDocumentReader {

    /** 单次返回字符上限（约 64KB UTF-8）。 */
    public static final int MAX_CHARS = 64 * 1024;

    private static final Set<String> TEXT_EXT = Set.of(
            "txt", "md", "markdown", "csv", "json", "log", "yml", "yaml", "xml");

    private AttachmentDocumentReader() {
    }

    /**
     * 按类型抽文本并截断。抽不出字时返回说明，不抛业务异常给工具层。
     */
    public static String extract(String fileName, String contentType, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "附件为空，无法读取。";
        }
        String mime = normalizeMime(contentType);
        String ext = extension(fileName);
        try {
            if (isPdf(mime, ext)) {
                return truncate(readPdf(bytes));
            }
            if (isDocx(mime, ext)) {
                return truncate(readDocx(bytes));
            }
            if (isPlainText(mime, ext)) {
                String text = KnowledgeTextFileReader.readUtf8(
                        fileName, contentType, bytes, AttachmentAppService.MAX_BYTES);
                return truncate(text);
            }
            return "不支持的附件类型，无法提取文本。";
        } catch (RuntimeException | IOException error) {
            String message = error.getMessage();
            if (message != null && !message.isBlank()) {
                return "读取附件失败：" + message;
            }
            return "读取附件失败。";
        }
    }

    private static String readPdf(byte[] bytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            if (text == null || text.isBlank()) {
                return "无法提取文本（可能是扫描件），本期不做 OCR。";
            }
            return text.trim();
        }
    }

    private static String readDocx(byte[] bytes) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
                XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            String text = extractor.getText();
            if (text == null || text.isBlank()) {
                return "Word 文档没有可提取的文本。";
            }
            return text.trim();
        }
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= MAX_CHARS) {
            return text;
        }
        return text.substring(0, MAX_CHARS) + "\n…（已截断）";
    }

    private static boolean isPdf(String mime, String ext) {
        return "application/pdf".equals(mime) || "pdf".equals(ext);
    }

    private static boolean isDocx(String mime, String ext) {
        return "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(mime)
                || "docx".equals(ext);
    }

    private static boolean isPlainText(String mime, String ext) {
        if (mime.startsWith("text/")
                || "application/json".equals(mime)
                || "application/xml".equals(mime)
                || "application/yaml".equals(mime)
                || "application/x-yaml".equals(mime)) {
            return true;
        }
        return TEXT_EXT.contains(ext);
    }

    private static String normalizeMime(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "";
        }
        String mime = contentType.trim().toLowerCase(Locale.ROOT);
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
}
