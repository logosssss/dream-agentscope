package com.zhu.bootstrap;

import com.zhu.product.api.ProductModule;
import java.util.List;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * 用户进程在单例创建完后校验产品声明的 HTTP 入口。
 * 比 {@code ApplicationRunner} 早，失败发生在启动收尾之前。契约模块不放这个 Bean。
 */
@Component
public class ProductContributionGuard implements SmartInitializingSingleton {

    private final List<ProductModule> modules;

    private final ApplicationContext context;

    /** 产品列表由容器收集。没有产品时列表为空，校验直接通过。 */
    public ProductContributionGuard(List<ProductModule> modules, ApplicationContext context) {
        this.modules = modules;
        this.context = context;
    }

    /** 只调用纯函数。失败抛 IllegalStateException，让上下文刷新失败。 */
    @Override
    public void afterSingletonsInstantiated() {
        ProductContributions.validate(modules, context);
    }
}
