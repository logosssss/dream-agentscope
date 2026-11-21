package com.zhu.access.application.task;

import com.zhu.runtime.api.event.AuditRecorder;
import com.zhu.runtime.task.AgentTask;
import com.zhu.runtime.task.handle.AgentTaskExecutor;
import com.zhu.runtime.task.handle.DocumentIndexExecutor;
import com.zhu.runtime.task.handle.TaskPayload;
import com.zhu.state.api.knowledge.KnowledgeBaseItem;
import com.zhu.state.api.knowledge.KnowledgeDocumentItem;
import com.zhu.state.api.knowledge.KnowledgeRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * BACKFILL：把没索引成功的文档重新索引一遍。
 *
 * <p>为什么需要它：索引流程是「发布任务 → 成功后改 SUCCESS」，进程在中间重启、模型临时不可用、
 * 向量库抖动都会留下 PENDING / FAILED 的文档，而这些文档只能靠人重新上传才会再索引。
 * 回填把这一步变成一次任务：扫本人（或指定库）的最新版本，跳过已经 SUCCESS 的，
 * 其余逐条交给索引执行器，成功失败各计数。
 *
 * <p>只做「重跑索引」，不删文档、不改正文、不跨用户。单次最多处理 {@code limit} 篇（默认 20，上限 100），
 * 避免一条任务把嵌入额度打光。
 */
@Component
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class KnowledgeBackfillExecutor implements AgentTaskExecutor {

    /** 任务类型。调度表里 task_type 填这个名字。 */
    public static final String TYPE = "BACKFILL";

    private static final int DEFAULT_LIMIT = 20;

    private static final int MAX_LIMIT = 100;

    private final KnowledgeRepository knowledge;

    private final DocumentIndexExecutor documents;

    private final AuditRecorder audit;

    public KnowledgeBackfillExecutor(
            KnowledgeRepository knowledge, DocumentIndexExecutor documents, AuditRecorder audit) {
        this.knowledge = knowledge;
        this.documents = documents;
        this.audit = audit;
    }

    @Override
    public String type() {
        return TYPE;
    }

    /**
     * payload 可选：{@code kbId} 指定一个库，{@code limit} 指定本次最多几篇。
     * 不传 kbId 时扫本人全部库。
     */
    @Override
    public void execute(AgentTask task) {
        String tenantId = task.tenantId() == null ? "" : task.tenantId();
        String userId = task.userId();
        int limit = TaskPayload.bounded(task.payload(), "limit", DEFAULT_LIMIT, 1, MAX_LIMIT);
        List<KnowledgeBaseItem> bases = bases(task, tenantId, userId);
        int indexed = 0;
        int failed = 0;
        int skipped = 0;
        for (KnowledgeBaseItem base : bases) {
            if (base == null) {
                continue;
            }
            for (KnowledgeDocumentItem doc : knowledge.listLatestDocuments(base.kbId(), tenantId, userId)) {
                if (doc == null) {
                    continue;
                }
                if (indexed + failed >= limit) {
                    skipped++;
                    continue;
                }
                if (KnowledgeRepository.SUCCESS.equals(doc.status())
                        || KnowledgeRepository.PROCESSING.equals(doc.status())) {
                    skipped++;
                    continue;
                }
                if (reindex(task, tenantId, userId, doc)) {
                    indexed++;
                } else {
                    failed++;
                }
            }
        }
        // 审计只放计数与库数量，不放标题或正文。
        audit.record(
                "task.backfill",
                userId,
                "bases=" + bases.size() + ",indexed=" + indexed + ",failed=" + failed + ",skipped=" + skipped);
    }

    /** 指定库时只认本人的库；找不到就当作没有可回填的，不报错。 */
    private List<KnowledgeBaseItem> bases(AgentTask task, String tenantId, String userId) {
        String kbId = TaskPayload.text(task.payload(), "kbId");
        if (kbId.isEmpty()) {
            return knowledge.listBases(tenantId, userId);
        }
        KnowledgeBaseItem base = knowledge.findBase(kbId, tenantId, userId);
        return base == null ? List.of() : List.of(base);
    }

    /**
     * 走与正常索引完全相同的执行器，所以切块、嵌入、双写、失败改 FAILED 的口径只有一份。
     * 单篇失败不打断整批：把异常收下来记一次失败，继续下一篇。
     */
    private boolean reindex(AgentTask task, String tenantId, String userId, KnowledgeDocumentItem doc) {
        AgentTask index = new AgentTask(
                task.taskId(),
                DocumentIndexExecutor.TYPE,
                tenantId,
                userId,
                doc.agentId(),
                null,
                Map.of("docId", doc.docId(), "version", doc.version()),
                Instant.now());
        try {
            documents.execute(index);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
