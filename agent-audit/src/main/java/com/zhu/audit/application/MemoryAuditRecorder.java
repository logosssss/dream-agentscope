package com.zhu.audit.application;

import com.zhu.audit.api.AuditRecorder;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 审计的进程内实现。MySQL 关闭时使用，进程重启后记录消失。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "false", matchIfMissing = true)
public class MemoryAuditRecorder implements AuditRecorder {

    private final List<String> records = new CopyOnWriteArrayList<>();

    /**
     * 追加一条记录。detail 由调用方控制，这里不再拼提示词或密钥。
     */
    @Override
    public void record(String action, String actorId, String detail) {
        records.add(action + "|" + actorId + "|" + detail);
    }

    /** 已记下的记录，测试和排查用。返回副本，避免调用方改内部列表。 */
    public List<String> records() {
        return List.copyOf(records);
    }
}
