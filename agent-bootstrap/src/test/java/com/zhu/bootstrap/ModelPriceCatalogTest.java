package com.zhu.bootstrap;

import com.zhu.audit.api.billing.ModelPriceEntry;
import com.zhu.audit.application.billing.MysqlModelPriceCatalog;
import com.zhu.audit.infrastructure.persistence.billing.ModelPriceMapper;
import com.zhu.audit.infrastructure.persistence.entity.billing.ModelPrice;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 改价只插入新行。单价为负或结束时间不晚于开始时间时不入库。不连接 MySQL。
 */
class ModelPriceCatalogTest {

    /** 空币种记成 CNY。单价保留 6 位小数。 */
    @Test
    void addsRowWithoutReplacingOldPrice() {
        ModelPriceMapper prices = mock(ModelPriceMapper.class);
        LocalDateTime from = LocalDateTime.parse("2026-10-01T00:00:00");

        ModelPriceEntry saved = new MysqlModelPriceCatalog(prices)
                .add("dashscope:qwen-plus", " ", new BigDecimal("0.002"), new BigDecimal("0.006"), from, null);

        ArgumentCaptor<ModelPrice> row = ArgumentCaptor.forClass(ModelPrice.class);
        verify(prices).insert(row.capture());
        assertThat(row.getValue().getModel()).isEqualTo("dashscope:qwen-plus");
        assertThat(row.getValue().getCurrency()).isEqualTo("CNY");
        assertThat(row.getValue().getInputPer1m()).isEqualByComparingTo("0.002000");
        assertThat(row.getValue().getEffectiveFrom()).isEqualTo(from);
        assertThat(row.getValue().getEffectiveTo()).isNull();
        assertThat(saved.outputPer1m()).isEqualByComparingTo("0.006000");
    }

    /** 结束时间不晚于开始时间时不插入。 */
    @Test
    void endBeforeStartIsRejected() {
        ModelPriceMapper prices = mock(ModelPriceMapper.class);
        LocalDateTime from = LocalDateTime.parse("2026-10-02T00:00:00");
        LocalDateTime to = LocalDateTime.parse("2026-10-01T00:00:00");

        assertThatThrownBy(() -> new MysqlModelPriceCatalog(prices)
                .add("m", "USD", BigDecimal.ONE, BigDecimal.ONE, from, to))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        verify(prices, never()).insert(any(ModelPrice.class));
    }
}
