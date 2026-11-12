package com.zhu.audit.infrastructure.persistence.deletion;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.deletion.DataDeletionRequest;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * data_deletion_request 的访问。登记用 BaseMapper 插入。决定在 XML 里按 PENDING 更新。
 */
public interface DataDeletionRequestMapper extends BaseMapper<DataDeletionRequest> {

    /**
     * 按请求标识读取。没有时返回 null。
     */
    DataDeletionRequest findByRequestId(@Param("requestId") String requestId);

    /**
     * 只把 PENDING 改成通过或拒绝。返回更新行数。
     */
    int decidePending(@Param("requestId") String requestId, @Param("status") String status);

    /**
     * 本人本租户的删除请求，新的在前。条数由调用方截好。
     */
    List<DataDeletionRequest> listOwned(
            @Param("userId") String userId,
            @Param("tenantId") String tenantId,
            @Param("limit") int limit);

    /**
     * 本人本租户的一条删除请求。别人的或没有时返回 null。含已决定的行。
     */
    DataDeletionRequest findOwned(
            @Param("requestId") String requestId,
            @Param("userId") String userId,
            @Param("tenantId") String tenantId);
}
