package com.zhu.semantic.contract.product;

import com.zhu.semantic.contract.tool.ToolDescriptor;
import java.util.List;

/**
 * 运行层能看见的产品语义。实现放在产品域，运行层只依赖这个接口。
 */
public interface ProductSemanticProvider {

    /** 产品侧的 Agent 标识，用来隔离状态，不是展示名。 */
    String agentId();

    /** 这次执行允许挂上的工具。 */
    List<ToolDescriptor> tools();

    /** 停止条件。 */
    CompletionCriteria completion();

    /**
     * 提示词的本地兜底。外部配置缺失时用它，不能返回空串。
     *
     * @param key 提示词键
     */
    String promptFallback(String key);
}
