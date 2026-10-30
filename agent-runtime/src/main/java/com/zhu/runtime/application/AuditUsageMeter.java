package com.zhu.runtime.application;

import com.zhu.audit.api.AuditRecorder;
import com.zhu.runtime.api.UsageMeter;
import java.util.logging.Logger;
import org.springframework.stereotype.Service;

/**
 * 把模型用量写成一条审计。明细只有模型名和数字，不放提示词或回复正文。
 */
@Service
public class AuditUsageMeter implements UsageMeter {

    private static final Logger log = Logger.getLogger(AuditUsageMeter.class.getName());

    private final AuditRecorder audits;

    public AuditUsageMeter(AuditRecorder audits) {
        this.audits = audits;
    }

    /**
     * 记账失败只记日志。用量写失败不能把已经生成的回复变成失败。
     */
    @Override
    public void onCall(String userId, String model, int inputTokens, int outputTokens, long latencyMillis) {
        try {
            audits.record("model.usage", userId == null ? "" : userId, detail(model, inputTokens, outputTokens, latencyMillis));
        } catch (RuntimeException ex) {
            log.warning("usage record failed");
        }
    }

    /** 拼成一行，模型名里的换行去掉，避免审计明细被拆开。 */
    static String detail(String model, int inputTokens, int outputTokens, long latencyMillis) {
        String name = model == null ? "" : model.replace('\r', ' ').replace('\n', ' ');
        return "model=" + name
                + " inputTokens=" + inputTokens
                + " outputTokens=" + outputTokens
                + " latencyMillis=" + latencyMillis;
    }
}
