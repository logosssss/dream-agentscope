package com.zhu.semantic.contract;

import java.util.List;

public interface ProductSemanticProvider {

    String agentId();

    List<ToolDescriptor> tools();

    CompletionCriteria completion();

    String promptFallback(String key);
}
