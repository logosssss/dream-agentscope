package com.zhu.state.api;

import java.util.Optional;

/**
 * 工作区文件的读写缝。对应 DistributedStore 的 BaseStore，例如 MEMORY.md 和 skills。
 */
public interface WorkspaceFileRepository {

    /** 按命名空间和键读取文件内容，不存在时为空。 */
    Optional<byte[]> find(String namespacePath, String keyName);

    /** 写入或覆盖一份工作区文件。 */
    void save(String namespacePath, String keyName, byte[] value);
}
