package com.zhu.admin;

import com.zhu.admin.application.deletion.MysqlDeletionRequestCatalog;
import com.zhu.readmodel.persistence.deletion.DataDeletionRequestMapper;
import com.zhu.readmodel.entity.deletion.DataDeletionRequest;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 删除请求的读侧：登记后是 PENDING 且没有执行信息；
 * 执行完的行把 executed_at / executed_note 透出来，让人能核对真删结果。
 */
class DeletionRequestCatalogTest {

    /** 登记只写 PENDING，执行列留空。 */
    @Test
    void submitStartsWithNoExecutionInfo() {
        DataDeletionRequestMapper requests = mock(DataDeletionRequestMapper.class);
        MysqlDeletionRequestCatalog catalog = new MysqlDeletionRequestCatalog(requests);

        var summary = catalog.submit("u-1", "t-1", "ALL", "清理账号");

        assertThat(summary.status()).isEqualTo("PENDING");
        assertThat(summary.executedAt()).isEmpty();
        assertThat(summary.executedNote()).isEmpty();
        verify(requests).insert(any(DataDeletionRequest.class));
    }

    /** 不传状态（或传 ALL）时不加过滤，默认排序交给 SQL。 */
    @Test
    void recentWithoutStatusListsEverything() {
        DataDeletionRequestMapper requests = mock(DataDeletionRequestMapper.class);
        MysqlDeletionRequestCatalog catalog = new MysqlDeletionRequestCatalog(requests);
        DataDeletionRequest done = executedRow("DONE");
        when(requests.listRecent(isNull(), eq(50))).thenReturn(List.of(done));

        List<?> rows = catalog.recent(null, 50);

        assertThat(rows).hasSize(1);
        verify(requests).listRecent(isNull(), eq(50));
    }

    /** 传状态时透传，并限制在 100 条以内。 */
    @Test
    void recentFiltersByStatusAndClampsLimit() {
        DataDeletionRequestMapper requests = mock(DataDeletionRequestMapper.class);
        MysqlDeletionRequestCatalog catalog = new MysqlDeletionRequestCatalog(requests);
        when(requests.listRecent(eq("FAILED"), eq(100))).thenReturn(List.of(executedRow("FAILED")));

        var rows = catalog.recent(" FAILED ", 5000);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).status()).isEqualTo("FAILED");
        assertThat(rows.get(0).executedAt()).isEqualTo("2026-11-21T10:00:00");
        assertThat(rows.get(0).executedNote()).isEqualTo("deleted={chat_message=2}");
    }

    /** 没有行时返回空列表，不返回 null。 */
    @Test
    void recentReturnsEmptyListWhenNothingMatches() {
        DataDeletionRequestMapper requests = mock(DataDeletionRequestMapper.class);
        MysqlDeletionRequestCatalog catalog = new MysqlDeletionRequestCatalog(requests);
        when(requests.listRecent(eq("APPROVED"), eq(50))).thenReturn(List.of());

        assertThat(catalog.recent("APPROVED", 50)).isEmpty();
    }

    /** 详情带提出时间与执行结果；执行列空时给空串。 */
    @Test
    void ownedOneExposesExecutionColumns() {
        DataDeletionRequestMapper requests = mock(DataDeletionRequestMapper.class);
        MysqlDeletionRequestCatalog catalog = new MysqlDeletionRequestCatalog(requests);
        DataDeletionRequest pending = executedRow("PENDING");
        pending.setExecutedAt(null);
        pending.setExecutedNote(null);
        pending.setRequestedAt(LocalDateTime.of(2026, 11, 21, 9, 30));
        when(requests.findOwned("req-1", "u-1", "t-1")).thenReturn(pending);

        var detail = catalog.ownedOne("u-1", "t-1", "req-1");

        assertThat(detail.requestedAt()).isEqualTo("2026-11-21T09:30:00");
        assertThat(detail.executedAt()).isEmpty();
        assertThat(detail.executedNote()).isEmpty();
    }

    /** 一行已执行完的请求。 */
    private static DataDeletionRequest executedRow(String status) {
        DataDeletionRequest row = new DataDeletionRequest();
        row.setRequestId("req-1");
        row.setUserId("u-1");
        row.setTenantId("t-1");
        row.setDataType("ALL");
        row.setStatus(status);
        row.setExecutedAt(LocalDateTime.of(2026, 11, 21, 10, 0));
        row.setExecutedNote("deleted={chat_message=2}");
        return row;
    }
}
