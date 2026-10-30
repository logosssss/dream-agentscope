package com.zhu.audit.application;

import com.zhu.audit.api.AuditRecorder;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;

/**
 * 审计的进程内实现。表还没建，先把记录留在内存里，让 Hook 有可注入的 Bean。
 */
@Service
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
