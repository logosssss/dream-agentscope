package com.zhu.semantic.contract.product;

/**
 * 一次执行何时停。由产品域声明，运行层只读取。
 *
 * @param maxIterations 最大推理轮数
 */
public record CompletionCriteria(int maxIterations) {
}
