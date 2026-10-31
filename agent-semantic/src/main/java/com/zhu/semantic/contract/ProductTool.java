package com.zhu.semantic.contract;

/**
 * 可以注册进 Toolkit 的业务工具。实现放在产品域，运行层只按目录标识挑选。
 */
public interface ProductTool {

    /**
     * 和工具目录、方法上 {@code @Tool} 的 name 一致。
     * 对不上目录的实现不会注册。
     */
    String toolId();

    /**
     * 带工具方法的对象。运行层把它交给 Toolkit，不认识具体业务类。
     */
    Object target();
}
