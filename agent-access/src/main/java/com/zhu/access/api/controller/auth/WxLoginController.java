package com.zhu.access.api.controller.auth;

import com.zhu.access.application.auth.WxLoginService;
import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 微信扫码。网站出码和查票走统一响应。小程序确认保持原来的地址和返回格式。
 */
@RestController
public class WxLoginController {

    private final WxLoginService wx;

    /** 出码、查票和确认都交给扫码服务。 */
    public WxLoginController(WxLoginService wx) {
        this.wx = wx;
    }

    /**
     * 告诉网页要不要显示扫码。没配密钥时为 false。
     */
    @GetMapping("/api/auth/config")
    public ApiResult<AuthConfigView> config() {
        return ApiResult.ok(new AuthConfigView(wx.enabled()));
    }

    /**
     * 网站申请一张票。仍要调用方令牌。
     */
    @PostMapping("/api/auth/wx/ticket")
    public ApiResult<WxLoginService.WxTicket> ticket() {
        return ApiResult.ok(wx.ticket());
    }

    /**
     * 网站查询这张票。确认后的 data.login 是会话。
     */
    @GetMapping("/api/auth/wx/ticket/{ticketId}")
    public ApiResult<WxLoginService.WxPoll> poll(@PathVariable String ticketId) {
        return ApiResult.ok(wx.poll(ticketId));
    }

    /**
     * 小程序确认。不带调用方令牌。成功时 code 为 200。
     * 失败用 HTTP 状态和 message，不走统一的 ApiResult。
     */
    @PostMapping("/api/public/auth/wx/confirm")
    public ResponseEntity<Map<String, Object>> confirm(@RequestBody(required = false) WxConfirmRequest request) {
        String ticketId = request == null ? null : request.ticketId();
        String code = request == null ? null : request.code();
        try {
            wx.confirm(ticketId, code);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("ok", true);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("code", 200);
            body.put("data", data);
            return ResponseEntity.ok(body);
        } catch (BusinessException ex) {
            int status = ex.errorCode() == ErrorCode.UNAVAILABLE ? 503 : 400;
            return ResponseEntity.status(status).body(Map.of("message", ex.getMessage()));
        }
    }

    /**
     * 扫码开关。
     *
     * @param wxScanLoginEnabled 密钥配齐时为 true
     */
    public record AuthConfigView(boolean wxScanLoginEnabled) {
    }

    /**
     * 小程序确认入参。字段名保持 ticketId 和 code。
     *
     * @param ticketId 网站发出的票
     * @param code     wx.login 的 code
     */
    public record WxConfirmRequest(String ticketId, String code) {
    }
}
