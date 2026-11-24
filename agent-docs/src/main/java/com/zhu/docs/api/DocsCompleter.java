package com.zhu.docs.api;

/**
 * 一轮补全。文档库不认识模型和 Harness，由接入层把这句话交给 docs-v1。
 */
public interface DocsCompleter {

    /**
     * 用给定的系统提示和用户消息问一次。系统提示必须原样送进模型。
     *
     * @param systemPrompt 文档助手系统提示
     * @param userText     标题、截断后的正文和问题
     * @return 模型文本，没有内容时是空串
     */
    String complete(String systemPrompt, String userText);
}
