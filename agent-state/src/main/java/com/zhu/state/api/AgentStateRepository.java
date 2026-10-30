package com.zhu.state.api;

import java.util.Optional;

public interface AgentStateRepository {

    Optional<String> find(String sessionId, String stateKey);

    void save(String sessionId, String stateKey, String stateData);
}
