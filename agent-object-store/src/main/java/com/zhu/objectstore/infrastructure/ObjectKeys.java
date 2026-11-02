package com.zhu.objectstore.infrastructure;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;

/**
 * 对象键规范化。本地与 OSS 共用，拒绝空键与路径穿越。
 */
public final class ObjectKeys {

    private ObjectKeys() {
    }

    /**
     * 去掉首尾斜杠，拒绝空段、. 与 ..。
     */
    public static String normalize(String key) {
        if (key == null || key.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "object key is required");
        }
        String trimmed = key.trim().replace('\\', '/');
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "object key is required");
        }
        for (String part : trimmed.split("/")) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "object key is invalid");
            }
        }
        return trimmed;
    }

    /**
     * 规范化可选前缀；空白返回空串；保证非空时以 / 结尾。
     */
    public static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "";
        }
        String trimmed = prefix.trim().replace('\\', '/');
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.isEmpty()) {
            return "";
        }
        for (String part : trimmed.split("/")) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "object prefix is invalid");
            }
        }
        return trimmed.endsWith("/") ? trimmed : trimmed + "/";
    }
}
