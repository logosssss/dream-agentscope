package com.zhu.state.api;

import java.util.Optional;

/**
 * 会话状态的读写缝。当前走 DistributedStore，本接口不暴露框架类型。
 * 自定义查询出现再实现，不要在旁边再写一套 SQL。
 */
public interface AgentStateRepository {

    /** 按会话和状态键读取一份状态，不存在时为空。 */
    Optional<String> find(String sessionId, String stateKey);

    /** 写入或覆盖一份会话状态。 */
    void save(String sessionId, String stateKey, String stateData);
}
