package com.zhu.semantic.api;

import com.zhu.semantic.contract.ToolDescriptor;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 当前没有业务工具。目录保持为空，避免把不能执行的名字交给模型。
 */
@Service
public class NoToolCatalog implements ToolCatalogService {

    /** 没有启用项时返回空列表，不返回 null。 */
    @Override
    public List<ToolDescriptor> listEnabled() {
        return List.of();
    }
}
