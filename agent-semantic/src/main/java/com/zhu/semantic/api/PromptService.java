package com.zhu.semantic.api;

import java.util.Map;

public interface PromptService {

    String get(String key, Map<String, String> variables, String fallback);
}
