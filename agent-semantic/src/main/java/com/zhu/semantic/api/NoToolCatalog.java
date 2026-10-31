package com.zhu.semantic.api;

import com.zhu.semantic.contract.ToolDescriptor;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * MySQL 关闭时的空目录。测试走这条，避免去查库。
 * MySQL 打开后由 tool_registry 接管，容器里仍然只有一个目录。
 */
@Service
@ConditionalOnProperty(
        name = "agentscope.distributed.mysql.enabled",
        havingValue = "false",
        matchIfMissing = true)
public class NoToolCatalog implements ToolCatalogService {

    /** 没有启用项时返回空列表，不返回 null。 */
    @Override
    public List<ToolDescriptor> listEnabled() {
        return List.of();
    }
}
