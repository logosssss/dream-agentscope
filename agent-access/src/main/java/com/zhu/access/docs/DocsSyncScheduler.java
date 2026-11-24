package com.zhu.access.docs;

import com.zhu.docs.api.DocsIngest;
import com.zhu.docs.application.DocsProperties;
import java.util.logging.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每天 03:00 兜底同步。MySQL 或 Milvus 没装配时什么都不做。
 * 和手动同步共用同一把单飞锁。
 */
@Component
public class DocsSyncScheduler {

    private static final Logger LOG = Logger.getLogger(DocsSyncScheduler.class.getName());

    private final DocsProperties properties;

    private final ObjectProvider<DocsIngest> ingests;

    /** 同步实现缺席时定时任务空转，不让用户进程起不来。 */
    public DocsSyncScheduler(DocsProperties properties, ObjectProvider<DocsIngest> ingests) {
        this.properties = properties;
        this.ingests = ingests;
    }

    /**
     * 默认上海时区 03:00。开关关掉或还没有索引实现时直接返回。
     */
    @Scheduled(cron = "${docs.sync-cron:0 0 3 * * *}", zone = "${docs.sync-zone:Asia/Shanghai}")
    public void nightly() {
        if (!properties.isSyncEnabled()) {
            return;
        }
        DocsIngest ingest = ingests.getIfAvailable();
        if (ingest == null) {
            return;
        }
        long runId = ingest.start();
        LOG.info("docs nightly sync accepted runId=" + runId);
    }
}
