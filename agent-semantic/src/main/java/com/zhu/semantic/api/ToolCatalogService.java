package com.zhu.semantic.api;

import com.zhu.semantic.contract.ToolDescriptor;
import java.util.List;

public interface ToolCatalogService {

    List<ToolDescriptor> listEnabled();
}
