package com.zhu.bootstrap;

import com.zhu.runtime.domain.CrisisReplyGuard;
import com.zhu.semantic.contract.CrisisPrompt;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 危机回复红线只看候选正文。预置稿里的「不要开玩笑」不能把自己盖掉。
 */
class CrisisReplyGuardTest {

    /** 非危机原样过占位符检查。玩笑句也不换。 */
    @Test
    void nonCriticalKeepsJoke() {
        assertThat(CrisisReplyGuard.finalize("开个玩笑吧", false, "危机话术")).isEqualTo("开个玩笑吧");
        assertThat(CrisisReplyGuard.finalize("结果 {{name}}", false, "危机话术")).isEmpty();
    }

    /** 危机命中玩笑、暧昧、扮演或危险方法时换成预置稿。 */
    @Test
    void criticalHitsAreReplaced() {
        assertThat(CrisisReplyGuard.finalize("我开个玩笑", true, "危机话术")).isEqualTo("危机话术");
        assertThat(CrisisReplyGuard.finalize("亲爱的别怕", true, "危机话术")).isEqualTo("危机话术");
        assertThat(CrisisReplyGuard.finalize("你来扮演超人", true, "危机话术")).isEqualTo("危机话术");
        assertThat(CrisisReplyGuard.finalize("具体方法如下", true, "危机话术")).isEqualTo("危机话术");
    }

    /** 危机里干净回复留下。空串和被丢掉的占位符不拦。 */
    @Test
    void criticalCleanReplyStays() {
        assertThat(CrisisReplyGuard.finalize("先找身边可信的人", true, "危机话术"))
                .isEqualTo("先找身边可信的人");
        assertThat(CrisisReplyGuard.finalize("", true, "危机话术")).isEmpty();
        assertThat(CrisisReplyGuard.finalize(null, true, "危机话术")).isEmpty();
        assertThat(CrisisReplyGuard.finalize("结果 {{name}}", true, "危机话术")).isEmpty();
    }

    /** 预置稿空白时用代码兜底。盖掉后不再拿兜底去过词表。 */
    @Test
    void replacementUsesFallbackAndIsNotRechecked() {
        assertThat(CrisisReplyGuard.finalize("开个玩笑", true, " ")).isEqualTo(CrisisPrompt.FALLBACK);
        assertThat(CrisisReplyGuard.finalize("开个玩笑", true, CrisisPrompt.FALLBACK))
                .isEqualTo(CrisisPrompt.FALLBACK);
        assertThat(CrisisPrompt.FALLBACK).contains("开玩笑");
    }
}
