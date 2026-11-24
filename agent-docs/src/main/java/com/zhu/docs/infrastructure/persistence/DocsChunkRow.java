package com.zhu.docs.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * docs_chunk 的一行。{@code text} 是保留字，列名需要单独标出来。
 */
@TableName("docs_chunk")
public class DocsChunkRow {

    @TableId
    private String id;

    private String docId;

    private String heading;

    private String anchor;

    private Integer ordinal;

    private String textHash;

    private String embeddedHash;

    @TableField("`text`")
    private String text;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDocId() {
        return docId;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }

    public String getHeading() {
        return heading;
    }

    public void setHeading(String heading) {
        this.heading = heading;
    }

    public String getAnchor() {
        return anchor;
    }

    public void setAnchor(String anchor) {
        this.anchor = anchor;
    }

    public Integer getOrdinal() {
        return ordinal;
    }

    public void setOrdinal(Integer ordinal) {
        this.ordinal = ordinal;
    }

    public String getTextHash() {
        return textHash;
    }

    public void setTextHash(String textHash) {
        this.textHash = textHash;
    }

    public String getEmbeddedHash() {
        return embeddedHash;
    }

    public void setEmbeddedHash(String embeddedHash) {
        this.embeddedHash = embeddedHash;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
