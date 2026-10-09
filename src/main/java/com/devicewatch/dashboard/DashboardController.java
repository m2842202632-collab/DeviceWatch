package com.devicewatch.dashboard;

import com.devicewatch.common.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/snapshot")
    public Result<List<Map<String, Object>>> snapshot() {
        return Result.ok(dashboardService.snapshot());
    }

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.ok(dashboardService.overview());
    }

    @GetMapping("/alert-trend")
    public Result<List<Map<String, Object>>> alertTrend() {
        return Result.ok(dashboardService.alertTrend());
    }

    @GetMapping("/device-health")
    public Result<List<Map<String, Object>>> deviceHealth() {
        return Result.ok(dashboardService.deviceHealth());
    }

    @GetMapping("/mtbf")
    public Result<List<Map<String, Object>>> mtbf() {
        return Result.ok(dashboardService.mtbf());
    }

    @GetMapping("/history")
    public Result<Map<String, Object>> history(@RequestParam Long deviceId,
                                              @RequestParam String metricCode,
                                              @RequestParam(defaultValue = "60") int minutes) {
        try {
            return Result.ok(dashboardService.history(deviceId, metricCode, minutes));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }
}
