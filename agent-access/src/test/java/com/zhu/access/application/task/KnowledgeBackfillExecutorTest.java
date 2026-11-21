package com.zhu.access.application.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhu.runtime.api.event.AuditRecorder;
import com.zhu.runtime.task.AgentTask;
import com.zhu.runtime.task.handle.DocumentIndexExecutor;
import com.zhu.state.api.knowledge.KnowledgeBaseItem;
import com.zhu.state.api.knowledge.KnowledgeDocumentItem;
import com.zhu.state.api.knowledge.KnowledgeRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 回填只做三件事：跳过已成功、逐篇调用索引、把成败写进审计。
 */
class KnowledgeBackfillExecutorTest {

    private final KnowledgeRepository knowledge = mock(KnowledgeRepository.class);

    private final DocumentIndexExecutor documents = mock(DocumentIndexExecutor.class);

    private final List<String> audits = new ArrayList<>();

    private final AuditRecorder audit = (action, actor, detail) -> audits.add(action + "|" + actor + "|" + detail);

    private KnowledgeBackfillExecutor executor() {
        return new KnowledgeBackfillExecutor(knowledge, documents, audit);
    }

    private AgentTask task(Map<String, Object> payload) {
        return new AgentTask("t-1", KnowledgeBackfillExecutor.TYPE, "demo", "u-1", "companion-v1", null, payload, Instant.now());
    }

    private KnowledgeBaseItem base(String kbId) {
        return new KnowledgeBaseItem(kbId, "demo", "u-1", "companion-v1", "默认库", null);
    }

    private KnowledgeDocumentItem doc(String docId, int version, String status) {
        return new KnowledgeDocumentItem(
                docId, version, "kb-1", "demo", "u-1", "companion-v1", "标题", null, status, null);
    }

    @Test
    void reindexesOnlyFailedDocuments() {
        when(knowledge.listBases(eq("demo"), eq("u-1"))).thenReturn(List.of(base("kb-1")));
        when(knowledge.listLatestDocuments(eq("kb-1"), eq("demo"), eq("u-1")))
                .thenReturn(List.of(doc("d-ok", 1, KnowledgeRepository.SUCCESS), doc("d-bad", 2, KnowledgeRepository.FAILED)));

        executor().execute(task(Map.of()));

        verify(documents).execute(any());
        assertThat(audits).containsExactly("task.backfill|u-1|bases=1,indexed=1,failed=0,skipped=1");
    }

    @Test
    void stopsAtLimitAndCountsFailures() {
        when(knowledge.listBases(anyString(), anyString())).thenReturn(List.of(base("kb-1")));
        when(knowledge.listLatestDocuments(eq("kb-1"), eq("demo"), eq("u-1")))
                .thenReturn(List.of(
                        doc("d-1", 1, KnowledgeRepository.FAILED),
                        doc("d-2", 1, KnowledgeRepository.PENDING),
                        doc("d-3", 1, KnowledgeRepository.PENDING)));
        doThrow(new IllegalStateException("embedding failed")).when(documents).execute(any());

        executor().execute(task(Map.of("limit", 2)));

        assertThat(audits).containsExactly("task.backfill|u-1|bases=1,indexed=0,failed=2,skipped=1");
    }

    @Test
    void missingBaseYieldsEmptyReport() {
        when(knowledge.findBase(eq("kb-x"), eq("demo"), eq("u-1"))).thenReturn(null);

        executor().execute(task(Map.of("kbId", "kb-x")));

        assertThat(audits).containsExactly("task.backfill|u-1|bases=0,indexed=0,failed=0,skipped=0");
    }
}
