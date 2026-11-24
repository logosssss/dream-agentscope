package com.zhu.bootstrap;

import com.zhu.product.api.ProductModule;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 用户进程启动期对产品 HTTP 声明的纯校验。
 * 不注册路由：组件扫描已经创建控制器，这里只确认声明没有落空、前缀没有撞车。
 */
public final class ProductContributions {

    private ProductContributions() {
    }

    /**
     * 每个声明的类必须已是 Bean，且类上有 {@link RequestMapping}。
     * 同一前缀只能属于一个类；同一个类重复声明同一前缀不算冲突。
     *
     * @param modules 进程里的全部产品
     * @param context 用来确认声明的类真有实例
     */
    public static void validate(List<ProductModule> modules, ApplicationContext context) {
        Map<String, Class<?>> owners = new LinkedHashMap<>();
        if (modules == null) {
            return;
        }
        for (ProductModule module : modules) {
            if (module == null || module.controllers() == null) {
                continue;
            }
            for (Class<?> controller : module.controllers()) {
                if (controller == null) {
                    throw new IllegalStateException("product " + module.agentId()
                            + " declares a null controller");
                }
                if (context.getBeansOfType(controller).isEmpty()) {
                    throw new IllegalStateException("product " + module.agentId()
                            + " declares controller " + controller.getName()
                            + " but no such bean exists");
                }
                RequestMapping mapping = controller.getAnnotation(RequestMapping.class);
                if (mapping == null) {
                    throw new IllegalStateException("product " + module.agentId()
                            + " declares controller " + controller.getName()
                            + " without a class-level @RequestMapping");
                }
                for (String prefix : prefixesOf(mapping)) {
                    Class<?> previous = owners.putIfAbsent(prefix, controller);
                    if (previous != null && previous != controller) {
                        throw new IllegalStateException("controllers " + previous.getName()
                                + " and " + controller.getName()
                                + " both claim path " + prefix);
                    }
                }
            }
        }
    }

    /** value 与 path 是别名，用集合去掉重复。都空时占住空串，避免无前缀控制器互相看不见。 */
    private static Set<String> prefixesOf(RequestMapping mapping) {
        Set<String> prefixes = new LinkedHashSet<>();
        for (String path : mapping.value()) {
            prefixes.add(path);
        }
        for (String path : mapping.path()) {
            prefixes.add(path);
        }
        if (prefixes.isEmpty()) {
            prefixes.add("");
        }
        return prefixes;
    }
}
