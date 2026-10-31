package com.zhu.admin.api.controller;

import com.zhu.admin.api.dto.BillingDraftRequest;
import com.zhu.audit.api.BillingRollup;
import com.zhu.audit.api.BillingSummary;
import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理进程上的计费汇总。草稿可以确认，已确认的可以开票。不付款，不改价格，也不执行工具。
 */
@RestController
public class AdminBillingController {

    private final ObjectProvider<BillingRollup> rollups;

    /** MySQL 关闭时没有汇总实现。接口仍在，调用会返回不可用。 */
    public AdminBillingController(ObjectProvider<BillingRollup> rollups) {
        this.rollups = rollups;
    }

    /**
     * 按租户和区间生成一张 DRAFT。同一区间再提交一次由汇总实现返回冲突。
     */
    @PostMapping("/api/admin/billing/drafts")
    public ApiResult<BillingSummary> draft(@RequestBody BillingDraftRequest request) {
        BillingRollup rollup = rollups.getIfAvailable();
        if (rollup == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "billing is unavailable");
        }
        BillingDraftRequest body = request == null
                ? new BillingDraftRequest(null, null, null)
                : request;
        return ApiResult.ok(rollup.draft(body.tenantId(), body.periodStart(), body.periodEnd()));
    }

    /**
     * 把一张草稿改成已确认。不重算金额。没有这张账单或已经确认过时，由汇总实现返回失败。
     */
    @PostMapping("/api/admin/billing/confirmations")
    public ApiResult<BillingSummary> confirm(@RequestBody BillingDraftRequest request) {
        BillingRollup rollup = rollups.getIfAvailable();
        if (rollup == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "billing is unavailable");
        }
        BillingDraftRequest body = request == null
                ? new BillingDraftRequest(null, null, null)
                : request;
        return ApiResult.ok(rollup.confirm(body.tenantId(), body.periodStart(), body.periodEnd()));
    }

    /**
     * 把一张已确认的账单改成已开票。不重算金额。草稿要先确认。
     */
    @PostMapping("/api/admin/billing/invoices")
    public ApiResult<BillingSummary> invoice(@RequestBody BillingDraftRequest request) {
        BillingRollup rollup = rollups.getIfAvailable();
        if (rollup == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "billing is unavailable");
        }
        BillingDraftRequest body = request == null
                ? new BillingDraftRequest(null, null, null)
                : request;
        return ApiResult.ok(rollup.invoice(body.tenantId(), body.periodStart(), body.periodEnd()));
    }
}
