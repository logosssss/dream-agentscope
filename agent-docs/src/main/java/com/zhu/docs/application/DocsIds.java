package com.zhu.docs.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 文档和块的稳定 id。同一路径永远得到同一个 id，改内容不换 id。
 */
public final class DocsIds {

    private static final HexFormat HEX = HexFormat.of();

    private DocsIds() {
    }

    /**
     * {@code repo + path} 的 SHA-256。中间用空字符隔开，避免路径拼接撞车。
     */
    public static String documentId(String repo, String path) {
        return sha256(repo + "\0" + path);
    }

    /**
     * {@code 文档 id + 序号} 的 SHA-256。序号从 0 开始。
     */
    public static String chunkId(String documentId, int ordinal) {
        return sha256(documentId + "\0" + ordinal);
    }

    /** 正文哈希，用来判断要不要重写。 */
    public static String contentHash(String text) {
        return sha256(text == null ? "" : text);
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HEX.formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required", ex);
        }
    }
}
