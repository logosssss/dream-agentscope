package com.zhu.runtime.api.agent;

/**
 * 长驻 Agent 重建结果。运行层对外端口 DTO：供 Nacos 热更日志与调用方分支，不改变排水「超时仍重建」语义。
 *
 * @param status  结果态
 * @param message 可选说明；无则空串
 */
public record RebuildResult(Status status, String message) {

    /**
     * 重建结果态。TIMEOUT 表示排水超时后仍完成了重建（在途风险）。
     */
    public enum Status {
        /** 排水干净且重建成功。 */
        SUCCESS,
        /** 尚未懒启动，空操作。 */
        SKIPPED,
        /** 排水超时后仍重建成功。 */
        TIMEOUT,
        /** 停机或启动失败。 */
        FAILED
    }

    /**
     * 防御性规范化 message。
     */
    public RebuildResult {
        status = status == null ? Status.FAILED : status;
        message = message == null ? "" : message;
    }

    /** 排水干净且重建成功。 */
    public static RebuildResult success() {
        return new RebuildResult(Status.SUCCESS, "");
    }

    /** 尚未启动。 */
    public static RebuildResult skipped(String message) {
        return new RebuildResult(Status.SKIPPED, message);
    }

    /** 排水超时后仍重建成功。 */
    public static RebuildResult timeout(String message) {
        return new RebuildResult(Status.TIMEOUT, message);
    }

    /** 重建失败。 */
    public static RebuildResult failed(String message) {
        return new RebuildResult(Status.FAILED, message);
    }
}
