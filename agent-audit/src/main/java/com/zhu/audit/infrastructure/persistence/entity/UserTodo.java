package com.zhu.audit.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * user_todo 的一行。审批待办由询问开、决定关。确认和任务由用户登记。
 */
@TableName("user_todo")
public class UserTodo {

    /** 自增主键。列表返回这一列。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发起用户。 */
    private String userId;

    /** APPROVAL、CONFIRM 或 TASK。 */
    private String todoType;

    /** 审批单、运行或任务标识。 */
    private String refId;

    /** 短标题。不存参数。 */
    private String title;

    /** 开着是 OPEN。通过后 DONE，拒绝后 CANCELLED。 */
    private String status;

    /** 主键。 */
    public Long getId() {
        return id;
    }

    /** 写入主键。 */
    public void setId(Long id) {
        this.id = id;
    }

    /** 用户。 */
    public String getUserId() {
        return userId;
    }

    /** 写入用户。 */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /** 待办类型。 */
    public String getTodoType() {
        return todoType;
    }

    /** 写入待办类型。 */
    public void setTodoType(String todoType) {
        this.todoType = todoType;
    }

    /** 关联标识。 */
    public String getRefId() {
        return refId;
    }

    /** 写入关联标识。 */
    public void setRefId(String refId) {
        this.refId = refId;
    }

    /** 标题。 */
    public String getTitle() {
        return title;
    }

    /** 写入标题。 */
    public void setTitle(String title) {
        this.title = title;
    }

    /** 状态。 */
    public String getStatus() {
        return status;
    }

    /** 写入状态。 */
    public void setStatus(String status) {
        this.status = status;
    }
}
