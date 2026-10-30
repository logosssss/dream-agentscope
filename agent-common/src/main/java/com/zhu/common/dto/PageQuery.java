package com.zhu.common.dto;

public record PageQuery(int page, int size) {

    public PageQuery {
        if (page < 1 || size < 1) {
            throw new IllegalArgumentException("page and size must be positive");
        }
    }
}
