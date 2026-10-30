package com.zhu.common.dto;

import java.util.List;

/**
 * 分页结果。{@code total} 是过滤后的总条数，不是本页条数。
 *
 * @param items 当前页数据
 * @param total 总条数
 * @param page  当前页码
 * @param size  每页条数
 */
public record PageResult<T>(List<T> items, long total, int page, int size) {
}
