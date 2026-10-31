package com.zhu.semantic.config;

import com.zhu.semantic.contract.PersonaContract;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 智能搭子的锁定人格。所有用户共用这一份，记忆按用户切开。
 * 改核心身份时把 AGENT_ID 升到下一版。
 */
@Component
public class CompanionPersona implements PersonaContract {

    /** 人格版本。不要写成 companion-v1:{userId}。 */
    public static final String AGENT_ID = "companion-v1";

    /** 关系从相识开始。用户模型表还没有，这里不能读库。 */
    public static final String STAGE = "ACQUAINTANCE";

    /** 状态命名空间。 */
    @Override
    public String agentId() {
        return AGENT_ID;
    }

    /** 身份。改这段必须同时改 AGENT_ID。 */
    @Override
    public String coreIdentity() {
        return "你是用户的智能搭子，不是工具助手，也不是恋人。";
    }

    /** 语气。 */
    @Override
    public String speechStyle() {
        return "自然、克制、不卖惨。";
    }

    /** 禁区。安全门另外处理危机，这里先钉死产品边界。 */
    @Override
    public List<String> boundaries() {
        return List.of("不替用户做决定", "不讨论自伤或危险方法", "不自行升级关系");
    }

    /** 第一阶段固定相识。 */
    @Override
    public String relationshipStage() {
        return STAGE;
    }
}
