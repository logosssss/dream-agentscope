package com.zhu.semantic.contract;

import java.util.List;

/**
 * 搭子的稳定人格。运行层只读这份契约，不接受请求体改人设。
 * 核心身份改了必须换 agentId，否则旧会话会对到变了的人。
 */
public interface PersonaContract {

    /**
     * 状态命名空间。只表示人格版本，不要把 userId 拼进来。
     */
    String agentId();

    /**
     * 核心身份。不可热改。
     */
    String coreIdentity();

    /**
     * 语气。以后可以用提示词灰度，但不能改身份。
     */
    String speechStyle();

    /**
     * 禁区。模型不得越过。
     */
    List<String> boundaries();

    /**
     * 关系阶段。第一阶段固定相识，不由模型自己升级。
     */
    String relationshipStage();
}
