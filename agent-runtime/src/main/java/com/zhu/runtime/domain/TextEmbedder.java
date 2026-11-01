package com.zhu.runtime.domain;

/**
 * 短文本嵌入口。模型锁 text-embedding-v4，维度 1024。
 * 没密钥或调用失败返回空数组，不要抛给对话。
 */
public interface TextEmbedder {

    /**
     * 把一句摘要变成向量。空白输入返回空数组。
     * 长度不是 1024 时调用方不要写入向量库。
     */
    float[] embed(String text);
}
