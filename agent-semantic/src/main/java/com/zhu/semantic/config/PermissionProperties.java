package com.zhu.semantic.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 工具权限规则。来源是配置，不是数据库。没写规则时列表为空，调用方按允许处理。
 */
@ConfigurationProperties(prefix = "agentscope.permission")
public class PermissionProperties {

    /** 全部规则。空列表表示没有任何额外限制。 */
    private List<Rule> rules = new ArrayList<>();

    /** 当前配置的规则。 */
    public List<Rule> getRules() {
        return rules;
    }

    /** 绑定配置。null 当成没有规则，避免后面空指针。 */
    public void setRules(List<Rule> rules) {
        this.rules = rules == null ? new ArrayList<>() : rules;
    }

    /**
     * 一条工具规则。tenantId、roleId 用 * 表示全部。
     */
    public static class Rule {

        /** 工具标识。空白的规则在装配时拒绝。 */
        private String toolId = "";

        /** 租户。默认全部租户。 */
        private String tenantId = "*";

        /** 角色。默认全部角色。当前请求还没有角色时，只能命中 *。 */
        private String roleId = "*";

        /** ALLOW、ASK 或 DENY。写错时装配失败，避免把拒绝静默当成允许。 */
        private String decision = "ALLOW";

        /** 数值越小越优先。相同优先级再看是否写明了租户和角色。 */
        private int priority = 100;

        /** 关闭的规则不参与匹配。 */
        private boolean enabled = true;

        /** 工具标识。 */
        public String getToolId() {
            return toolId;
        }

        /** 写入工具标识。 */
        public void setToolId(String toolId) {
            this.toolId = toolId;
        }

        /** 租户，可能是 *。 */
        public String getTenantId() {
            return tenantId;
        }

        /** 写入租户。 */
        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        /** 角色，可能是 *。 */
        public String getRoleId() {
            return roleId;
        }

        /** 写入角色。 */
        public void setRoleId(String roleId) {
            this.roleId = roleId;
        }

        /** 决定文本。 */
        public String getDecision() {
            return decision;
        }

        /** 写入决定文本。 */
        public void setDecision(String decision) {
            this.decision = decision;
        }

        /** 优先级。 */
        public int getPriority() {
            return priority;
        }

        /** 写入优先级。 */
        public void setPriority(int priority) {
            this.priority = priority;
        }

        /** 是否启用。 */
        public boolean isEnabled() {
            return enabled;
        }

        /** 写入是否启用。 */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
