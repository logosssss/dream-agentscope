package com.zhu.audit.infrastructure.persistence;

import com.zhu.audit.api.DailyCostAccumulator;
import com.zhu.audit.api.TokenUsageWriter;
import com.zhu.audit.domain.ModelCosting;
import com.zhu.audit.infrastructure.persistence.entity.ModelPrice;
import com.zhu.audit.infrastructure.persistence.entity.TokenUsage;
import com.zhu.common.util.Ids;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * 把 token 用量写入 MySQL。有当前价格时按每千 token 计价，没有则金额记 0。
 * 计价后若有费用累加器，再写入当日 Redis 微单位计数。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlTokenUsageWriter implements TokenUsageWriter {

    private static final Logger log = Logger.getLogger(MysqlTokenUsageWriter.class.getName());

    private static final int MODEL_LIMIT = 128;

    private final TokenUsageMapper usages;

    private final ModelPriceMapper prices;

    private final DailyCostAccumulator costs;

    /** 累加器可缺省。价格缺失时不拒绝写入。 */
    @Autowired
    public MysqlTokenUsageWriter(
            TokenUsageMapper usages,
            ModelPriceMapper prices,
            @Nullable DailyCostAccumulator costs) {
        this.usages = usages;
        this.prices = prices;
        this.costs = costs;
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
        BigDecimal amount = ModelCosting.amount(input, output, price);
        TokenUsage row = new TokenUsage();
        row.setRunId(runId == null || runId.isBlank() ? Ids.newId() : runId);
        row.setTenantId(tenantId == null ? "" : tenantId);
        row.setUserId(userId);
        row.setSessionId(sessionId == null || sessionId.isBlank() ? null : sessionId);
        row.setModel(name);
        row.setInputTokens(input);
        row.setOutputTokens(output);
        row.setTotalTokens(input + output);
        row.setCostAmount(amount);
        row.setCurrency(currency(price));
        usages.insert(row);
        accumulate(tenantId, userId, input, output, price);
    }

    private void accumulate(String tenantId, String userId, long input, long output, ModelPrice price) {
        if (costs == null) {
            return;
        }
        try {
            long micros = ModelCosting.micros(input, output, price);
            costs.add(tenantId, userId, micros);
        } catch (RuntimeException ex) {
            log.warning("daily cost accumulate failed");
        }
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
