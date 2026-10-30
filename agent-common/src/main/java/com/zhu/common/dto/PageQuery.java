package com.zhu.common.dto;

/**
 * 分页入参。页码从 1 开始，非法值在构造时直接拒绝。
 *
 * @param page 页码，最小为 1
 * @param size 每页条数，最小为 1
 */
public record PageQuery(int page, int size) {

    public PageQuery {
        if (page < 1 || size < 1) {
            throw new IllegalArgumentException("page and size must be positive");
        }
    }
}
