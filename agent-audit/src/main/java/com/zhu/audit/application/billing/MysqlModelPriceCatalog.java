package com.zhu.audit.application.billing;

import com.zhu.audit.application.support.AuditQueryLimits;

import com.zhu.audit.api.billing.ModelPriceCatalog;
import com.zhu.audit.api.billing.ModelPriceEntry;
import com.zhu.audit.infrastructure.persistence.billing.ModelPriceMapper;
import com.zhu.audit.infrastructure.persistence.entity.billing.ModelPrice;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 从 MySQL 读写模型价格。只在存储开关打开时创建。改价是插入新行。
 * 单价单位是元/百万 token。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlModelPriceCatalog implements ModelPriceCatalog {

    private static final int MODEL_LIMIT = 128;

    private final ModelPriceMapper prices;

    /** MySQL 打开时由审计 Mapper 扫描提供实现。 */
    public MysqlModelPriceCatalog(ModelPriceMapper prices) {
        this.prices = prices;
    }

    /** 条数和审计查询用同一套上限。 */
    @Override
    public List<ModelPriceEntry> recent(int limit) {
        List<ModelPrice> rows = prices.recent(AuditQueryLimits.normalize(limit));
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<ModelPriceEntry> entries = new ArrayList<>(rows.size());
        for (ModelPrice row : rows) {
            entries.add(toEntry(row));
        }
        return List.copyOf(entries);
    }

    /**
     * 模型为空或超过 128 位、币种不是 3 位、单价为空或为负时拒绝。
     * 不把仓库里的单价写进来。0 表示这个模型免费。
     */
    @Override
    public ModelPriceEntry add(
            String model,
            String currency,
            BigDecimal inputPer1m,
            BigDecimal outputPer1m,
            LocalDateTime effectiveFrom,
            LocalDateTime effectiveTo) {
        if (model == null || model.isBlank() || model.length() > MODEL_LIMIT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "model price is invalid");
        }
        String code = currency == null || currency.isBlank() ? "CNY" : currency.trim().toUpperCase();
        if (code.length() != 3) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "model price is invalid");
        }
        if (inputPer1m == null || outputPer1m == null || inputPer1m.signum() < 0 || outputPer1m.signum() < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "model price is invalid");
        }
        LocalDateTime from = effectiveFrom == null ? LocalDateTime.now(ZoneOffset.UTC) : effectiveFrom;
        if (effectiveTo != null && !effectiveTo.isAfter(from)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "model price is invalid");
        }
        ModelPrice row = new ModelPrice();
        row.setModel(model);
        row.setCurrency(code);
        row.setInputPer1m(inputPer1m.setScale(6, RoundingMode.HALF_UP));
        row.setOutputPer1m(outputPer1m.setScale(6, RoundingMode.HALF_UP));
        row.setEffectiveFrom(from);
        row.setEffectiveTo(effectiveTo);
        prices.insert(row);
        return toEntry(row);
    }

    /** 空行不会进列表。单价按库里的值返回。 */
    private static ModelPriceEntry toEntry(ModelPrice row) {
        return new ModelPriceEntry(
                row.getId(),
                row.getModel(),
                row.getCurrency(),
                row.getInputPer1m(),
                row.getOutputPer1m(),
                row.getEffectiveFrom(),
                row.getEffectiveTo());
    }
}
