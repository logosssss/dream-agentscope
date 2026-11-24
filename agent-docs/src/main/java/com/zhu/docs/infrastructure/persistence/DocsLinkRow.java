package com.zhu.docs.infrastructure.persistence;

/**
 * 一条可见文档的路径和其中一个锚点。一篇有多节时会出现多行。
 * 没有小节时 anchor 为空，路径仍然要留下来，否则无目录的文档链不过去。
 */
public class DocsLinkRow {

    private String id;

    private String path;

    private String anchor;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getAnchor() {
        return anchor;
    }

    public void setAnchor(String anchor) {
        this.anchor = anchor;
    }
}
