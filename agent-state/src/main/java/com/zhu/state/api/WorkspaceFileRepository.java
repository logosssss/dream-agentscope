package com.zhu.state.api;

import java.util.Optional;

public interface WorkspaceFileRepository {

    Optional<byte[]> find(String namespacePath, String keyName);

    void save(String namespacePath, String keyName, byte[] value);
}
