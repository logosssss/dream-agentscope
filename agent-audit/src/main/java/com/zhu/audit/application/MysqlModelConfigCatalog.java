package com.zhu.audit.application;

import com.zhu.audit.api.ModelConfigCatalog;
import com.zhu.audit.api.ModelConfigLookup;
import com.zhu.audit.api.ModelConfigSummary;
import com.zhu.audit.infrastructure.persistence.ModelConfigMapper;
import com.zhu.audit.infrastructure.persistence.entity.ModelConfig;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.List;
import java.util.logging.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 登记和列出模型配置，并给对话挑启用行。只在存储开关打开时创建。不解析参数 JSON。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlModelConfigCatalog implements ModelConfigCatalog, ModelConfigLookup {

    private static final Logger log = Logger.getLogger(MysqlModelConfigCatalog.class.getName());

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
     * 空白范围按全局处理。不写 params。
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

    /**
     * 空结果返回空列表。不按启用再滤一层，停用也要能看见。
     * 不读参数 JSON。
     */
    @Override
    public List<ModelConfigSummary> recent(int limit) {
        List<ModelConfig> rows = configs.recent(AuditQueryLimits.normalize(limit));
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(row -> new ModelConfigSummary(
                        row.getConfigId(),
                        row.getModel(),
                        row.getProvider(),
                        row.getVersion(),
                        row.getTenantScope()))
                .toList();
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
