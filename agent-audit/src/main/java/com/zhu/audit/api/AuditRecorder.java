package com.zhu.audit.api;

public interface AuditRecorder {

    void record(String action, String actorId, String detail);
}
