package com.zhu.runtime.application.model;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.runtime.api.model.ModelConfigLookup;
import com.zhu.readmodel.entity.model.ModelConfig;
import com.zhu.readmodel.persistence.model.ModelConfigMapper;
import java.util.logging.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * 运行层：对话前读启用中的模型配置。只在存储开关打开时创建。不解析参数 JSON。
 * 登记/停用等目录写能力在管理进程，不在这里。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlModelConfigLookup implements ModelConfigLookup {

    private static final Logger log = Logger.getLogger(MysqlModelConfigLookup.class.getName());

    private final ModelConfigMapper configs;

    /** 注入 Mapper；查库失败时拒绝对话。 */
    public MysqlModelConfigLookup(ModelConfigMapper configs) {
        this.configs = configs;
    }

    /**
     * 查库失败时拒绝对话，不退回进程配置。
     * 没有启用行时返回 null，由运行层继续用进程里的模型名。
     */
    @Override
    public String resolveModel(String tenantId) {
        try {
            ModelConfig row = configs.findEnabled(tenantId == null || tenantId.isBlank() ? null : tenantId);
            if (row == null || row.getProvider() == null || row.getProvider().isBlank()
                    || row.getModel() == null || row.getModel().isBlank()) {
                return null;
            }
            return row.getProvider() + ":" + row.getModel();
        } catch (DataAccessException ex) {
            log.warning("model config lookup failed");
            throw new BusinessException(ErrorCode.UNAVAILABLE, "model config is unavailable");
        }
    }
}
