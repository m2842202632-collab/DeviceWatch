package com.devicewatch.alert;

import com.devicewatch.auth.SysUser;
import com.devicewatch.common.PageResult;
import com.devicewatch.common.Result;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {
    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public Result<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) Long deviceId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.ok(alertService.page(status, level, deviceId, page, size));
    }

    @PostMapping("/{id}/ack")
    public Result<Void> ack(@PathVariable Long id, @RequestAttribute("loginUser") SysUser user) {
        try {
            alertService.ack(id, user.getRealName());
            return Result.ok();
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    @PostMapping("/{id}/resolve")
    public Result<Void> resolve(@PathVariable Long id,
                               @RequestAttribute("loginUser") SysUser user,
                               @RequestBody(required = false) Map<String, String> body) {
        try {
            String remark = body == null ? null : body.get("remark");
            alertService.resolve(id, user.getRealName(), remark);
            return Result.ok();
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }
}
