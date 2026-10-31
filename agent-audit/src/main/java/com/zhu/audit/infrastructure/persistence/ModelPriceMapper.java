package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.ModelPrice;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;

/**
 * model_price 的访问。当前价格的查询在 XML 里，Java 不写 SQL。
 */
public interface ModelPriceMapper extends BaseMapper<ModelPrice> {

    /**
     * 取这个模型在给定时刻仍然生效、且生效开始最晚的一行。没有则返回 null。
     */
    ModelPrice selectCurrent(@Param("model") String model, @Param("now") LocalDateTime now);
}
