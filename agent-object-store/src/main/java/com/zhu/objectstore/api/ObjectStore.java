package com.zhu.objectstore.api;

/**
 * 对象存取口。只按相对键读写字节，不解析业务路径语义。
 * 生产可换 OSS / S3；开发态落本地目录。
 */
public interface ObjectStore {

    /**
     * 写入或覆盖。返回规范化后的相对业务键（不含桶前缀）。
     * key 为空、含 .. 时拒绝。
     */
    String put(String key, byte[] content);

    /**
     * 读出完整字节。不存在时返回 null。
     */
    byte[] get(String key);

    /**
     * 按键删除。不存在时忽略。
     */
    void delete(String key);

    /**
     * 附件表 storage_type。本地实现返回 LOCAL，OSS 返回 OSS。
     */
    String storageType();
}
