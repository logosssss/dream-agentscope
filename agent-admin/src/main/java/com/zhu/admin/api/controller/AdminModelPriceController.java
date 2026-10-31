package com.zhu.admin.api.controller;

import com.zhu.admin.api.dto.ModelPriceRequest;
import com.zhu.audit.api.ModelPriceCatalog;
import com.zhu.audit.api.ModelPriceEntry;
import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理进程上的模型价格。只新增生效行，不改旧行，也不改正在进行的对话。
 */
@RestController
public class AdminModelPriceController {

    private final ObjectProvider<ModelPriceCatalog> catalogs;

    /** MySQL 关闭时没有价格目录。接口仍在，调用会返回不可用。 */
    public AdminModelPriceController(ObjectProvider<ModelPriceCatalog> catalogs) {
        this.catalogs = catalogs;
    }

    /**
     * 最近的价格。条数默认 50，上限 100。
     */
    @GetMapping("/api/admin/model-prices")
    public ApiResult<List<ModelPriceEntry>> recent(@RequestParam(defaultValue = "50") int limit) {
        return ApiResult.ok(catalog().recent(limit));
    }

    /**
     * 再插入一行。已经生效的旧价格留在表里，取价时用开始时间最晚的一行。
     */
    @PostMapping("/api/admin/model-prices")
    public ApiResult<ModelPriceEntry> add(@RequestBody ModelPriceRequest request) {
        ModelPriceRequest body = request == null
                ? new ModelPriceRequest(null, null, null, null, null, null)
                : request;
        return ApiResult.ok(catalog().add(
                body.model(),
                body.currency(),
                body.inputPer1k(),
                body.outputPer1k(),
                body.effectiveFrom(),
                body.effectiveTo()));
    }

    /** 没有目录时不把调用转成空列表，避免把“库没开”看成“没有价格”。 */
    private ModelPriceCatalog catalog() {
        ModelPriceCatalog catalog = catalogs.getIfAvailable();
        if (catalog == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "model price is unavailable");
        }
        return catalog;
    }
}
