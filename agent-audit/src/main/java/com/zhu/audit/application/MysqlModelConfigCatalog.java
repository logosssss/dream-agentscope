package com.zhu.audit.application;

import com.zhu.audit.api.ModelConfigCatalog;
import com.zhu.audit.api.ModelConfigSummary;
import com.zhu.audit.infrastructure.persistence.ModelConfigMapper;
import com.zhu.audit.infrastructure.persistence.entity.ModelConfig;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 登记模型配置。只在存储开关打开时创建。不接到运行层。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlModelConfigCatalog implements ModelConfigCatalog {

    private static final String VERSION = "1.0.0";

    private static final String GLOBAL_SCOPE = "*";

    private static final int ID_LIMIT = 128;

    private static final int MODEL_LIMIT = 128;

    private static final int PROVIDER_LIMIT = 64;

    private static final int SCOPE_LIMIT = 64;

    private final ModelConfigMapper configs;

    /** 同一标识和版本由唯一键拒绝。 */
    public MysqlModelConfigCatalog(ModelConfigMapper configs) {
        this.configs = configs;
    }

    /**
     * 空白范围按全局处理。不写 params，也不改对话里正在用的模型名。
     */
    @Override
    public ModelConfigSummary register(String configId, String model, String provider, String tenantScope) {
        if (configId == null || configId.isBlank() || configId.length() > ID_LIMIT
                || model == null || model.isBlank() || model.length() > MODEL_LIMIT
                || provider == null || provider.isBlank() || provider.length() > PROVIDER_LIMIT
                || (tenantScope != null && tenantScope.length() > SCOPE_LIMIT)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "model config is invalid");
        }
        String scope = tenantScope == null || tenantScope.isBlank() ? GLOBAL_SCOPE : tenantScope;
        ModelConfig row = new ModelConfig();
        row.setConfigId(configId);
        row.setModel(model);
        row.setProvider(provider);
        row.setVersion(VERSION);
        row.setTenantScope(scope);
        row.setEnabled(1);
        try {
            configs.insert(row);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "model config already exists");
        }
        return new ModelConfigSummary(configId, model, provider, VERSION, scope);
    }
}
