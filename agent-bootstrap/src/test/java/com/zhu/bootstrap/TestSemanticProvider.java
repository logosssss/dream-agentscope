package com.zhu.bootstrap;

import com.zhu.semantic.contract.product.CompletionCriteria;
import com.zhu.semantic.contract.product.ProductSemanticProvider;
import com.zhu.semantic.contract.tool.ToolDescriptor;
import java.util.List;

/**
 * 测试里的产品语义。兜底是一个点，工具目录为空。
 */
final class TestSemanticProvider implements ProductSemanticProvider {

    /** 固定的状态命名空间。 */
    @Override
    public String agentId() {
        return "product-agent";
    }

    /** 测试不挂业务工具。 */
    @Override
    public List<ToolDescriptor> tools() {
        return List.of();
    }

    /** 和运行配置的缺省轮数一致。 */
    @Override
    public CompletionCriteria completion() {
        return new CompletionCriteria(20);
    }

    /** 任何键都返回非空兜底。 */
    @Override
    public String promptFallback(String key) {
        return ".";
    }
}
