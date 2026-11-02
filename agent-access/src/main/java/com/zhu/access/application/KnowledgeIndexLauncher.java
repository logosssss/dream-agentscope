package com.zhu.access.application;

import com.zhu.common.util.Ids;
import com.zhu.runtime.task.AgentTask;
import com.zhu.runtime.task.AgentTaskPublisher;
import com.zhu.runtime.task.DocumentIndexExecutor;
import com.zhu.state.api.KnowledgeRepository;
import java.time.Instant;
import java.util.Map;
import java.util.logging.Logger;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 把索引发布挪出 HTTP 线程。内部仍走 sync TaskPublisher。
 */
@Service
public class KnowledgeIndexLauncher {

    private static final Logger log = Logger.getLogger(KnowledgeIndexLauncher.class.getName());

    private final AgentTaskPublisher publisher;

    private final KnowledgeRepository knowledge;

    public KnowledgeIndexLauncher(AgentTaskPublisher publisher, KnowledgeRepository knowledge) {
        this.publisher = publisher;
        this.knowledge = knowledge;
    }

    /**
     * 异步发布 DOCUMENT_INDEX。失败把文档标 FAILED，避免一直卡在 PENDING。
     */
    @Async
    public void enqueue(String tenantId, String userId, String agentId, String docId, int version) {
        try {
            publisher.publish(new AgentTask(
                    Ids.newId(),
                    DocumentIndexExecutor.TYPE,
                    tenantId == null ? "" : tenantId,
                    userId,
                    agentId,
                    null,
                    Map.of("docId", docId, "version", version),
                    Instant.now()));
        } catch (RuntimeException ex) {
            log.warning("knowledge index enqueue failed");
            knowledge.updateDocumentStatus(
                    docId,
                    version,
                    tenantId,
                    userId,
                    KnowledgeRepository.FAILED,
                    ex.getClass().getSimpleName());
        }
    }
}
