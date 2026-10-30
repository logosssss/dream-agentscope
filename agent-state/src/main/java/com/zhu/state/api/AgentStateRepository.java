package com.zhu.state.api;

import java.util.Optional;

/**
 * 会话状态的读写缝。持久化由 MySQL DistributedStore 承担，本接口不暴露框架类型。
 */
public interface AgentStateRepository {

    /** 按会话和状态键读取一份状态，不存在时为空。 */
    Optional<String> find(String sessionId, String stateKey);

    /** 写入或覆盖一份会话状态。 */
    void save(String sessionId, String stateKey, String stateData);
}
