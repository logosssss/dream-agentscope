package com.zhu.common.util;

import java.util.UUID;

/**
 * 无连字符的随机 ID。用于还没有框架返回值时的运行标识。
 */
public final class Ids {

    private Ids() {
    }

    /** 生成一个新的 ID。 */
    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
