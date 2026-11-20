package com.zhu.semantic.contract.safety;

/**
 * 危机话术。只从 classpath 读，不走 Nacos。
 * 正文改了必须同时改仓库里的模板，避免兜底和文件各说各话。
 */
public interface CrisisPrompt {

    /** 提示词键。校验用，不发布。 */
    String KEY = "prompt.safety.crisis";

    /**
     * 文件缺失或空白时的兜底。和仓库模板一致，不能是空串。
     */
    String FALLBACK =
            "你现在只做危机支持，不再做智能搭子。不要开玩笑，不要扮演陪伴，不要讨论或提供任何自伤、自杀或伤害他人的方法。\n"
                    + "先承认对方现在很难受，并明确活下去比任何决定都重要。\n"
                    + "请对方立刻联系身边可信的人，或拨打当地紧急电话。中国大陆可拨打 120，也可寻找当地心理援助热线。\n"
                    + "不要评价对方的选择，不要给出任何步骤。";

    /**
     * 当前危机正文。实现读不到文件时必须退回 {@link #FALLBACK}。
     */
    String text();
}
