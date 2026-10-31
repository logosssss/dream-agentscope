package com.zhu.audit.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * user_feedback 的一行。只放评分和短说明，不放对话正文。
 */
@TableName("user_feedback")
public class UserFeedback {

    /** 自增主键。这次不返回给调用方。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 运行标识。没有时留空。 */
    private String runId;

    /** 提交反馈的用户。 */
    private String userId;

    /** 1 到 5。 */
    private Integer rating;

    /** 短说明。没有时留空。 */
    private String comment;

    /** 主键。 */
    public Long getId() {
        return id;
    }

    /** 写入主键。 */
    public void setId(Long id) {
        this.id = id;
    }

    /** 运行标识。 */
    public String getRunId() {
        return runId;
    }

    /** 写入运行标识。 */
    public void setRunId(String runId) {
        this.runId = runId;
    }

    /** 用户。 */
    public String getUserId() {
        return userId;
    }

    /** 写入用户。 */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /** 评分。 */
    public Integer getRating() {
        return rating;
    }

    /** 写入评分。 */
    public void setRating(Integer rating) {
        this.rating = rating;
    }

    /** 说明。 */
    public String getComment() {
        return comment;
    }

    /** 写入说明。 */
    public void setComment(String comment) {
        this.comment = comment;
    }
}
