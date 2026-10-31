package com.zhu.audit.infrastructure.persistence;

import com.zhu.audit.api.TokenUsageWriter;
import com.zhu.audit.infrastructure.persistence.entity.ModelPrice;
import com.zhu.audit.infrastructure.persistence.entity.TokenUsage;
import com.zhu.common.util.Ids;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 把 token 用量写入 MySQL。有当前价格时按每千 token 计价，没有则金额记 0。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlTokenUsageWriter implements TokenUsageWriter {

    private static final int MODEL_LIMIT = 128;

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    private final TokenUsageMapper usages;

    private final ModelPriceMapper prices;

    /** MySQL 打开时由审计 Mapper 扫描提供实现。价格缺失时不拒绝写入。 */
    public MysqlTokenUsageWriter(TokenUsageMapper usages, ModelPriceMapper prices) {
        this.usages = usages;
        this.prices = prices;
    }

    /**
     * 用户或模型为空时不写，避免违反非空约束。
     * 负数 token 记成 0。运行标识为空时现生成一个。
     * 价格按 UTC 现在取当前生效行。没有行、或单价为空时金额记 0。
     */
    @Override
    public void record(
            String tenantId,
            String userId,
            String sessionId,
            String runId,
            String model,
            long inputTokens,
            long outputTokens) {
        if (userId == null || userId.isBlank() || model == null || model.isBlank()) {
            return;
        }
        long input = Math.max(0L, inputTokens);
        long output = Math.max(0L, outputTokens);
        String name = model.length() <= MODEL_LIMIT ? model : model.substring(0, MODEL_LIMIT);
        ModelPrice price = prices.selectCurrent(name, LocalDateTime.now(ZoneOffset.UTC));
        TokenUsage row = new TokenUsage();
        row.setRunId(runId == null || runId.isBlank() ? Ids.newId() : runId);
        row.setTenantId(tenantId == null ? "" : tenantId);
        row.setUserId(userId);
        row.setSessionId(sessionId == null || sessionId.isBlank() ? null : sessionId);
        row.setModel(name);
        row.setInputTokens(input);
        row.setOutputTokens(output);
        row.setTotalTokens(input + output);
        row.setCostAmount(cost(input, output, price));
        row.setCurrency(currency(price));
        usages.insert(row);
    }

    /** 每千 token 的单价乘以 token 数再除以 1000。结果保留 6 位小数。 */
    private static BigDecimal cost(long input, long output, ModelPrice price) {
        if (price == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal inputCost = rate(price.getInputPer1k()).multiply(BigDecimal.valueOf(input));
        BigDecimal outputCost = rate(price.getOutputPer1k()).multiply(BigDecimal.valueOf(output));
        return inputCost.add(outputCost).divide(THOUSAND, 6, RoundingMode.HALF_UP);
    }

    /** 空单价当成 0，避免没有填价的列把这次写入弄失败。 */
    private static BigDecimal rate(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** 价格行没有币种时仍用 CNY。超过 3 位时截断，对齐列宽。 */
    private static String currency(ModelPrice price) {
        if (price == null || price.getCurrency() == null || price.getCurrency().isBlank()) {
            return "CNY";
        }
        String value = price.getCurrency().trim();
        return value.length() <= 3 ? value : value.substring(0, 3);
    }
}
