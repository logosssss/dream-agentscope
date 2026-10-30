package com.zhu.semantic.api;

import com.zhu.semantic.contract.ToolDescriptor;
import java.util.List;

/**
 * 当前启用的工具目录。运行层注册 Toolkit 时只问这个接口。
 */
public interface ToolCatalogService {

    /** 返回已启用的工具，不包含停用项。 */
    List<ToolDescriptor> listEnabled();
}
