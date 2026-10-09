package com.devicewatch.alert;

import com.devicewatch.device.Device;
import com.devicewatch.rule.AlertRule;
import com.devicewatch.rule.RuleService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 规则引擎：对每条遥测数据做规则匹配与判定。
 *
 * 判定逻辑（防抖）：
 * - 连续超标 durationTimes 次才触发一次 fireOrSuppress（偶发毛刺不告警）
 * - 一次正常值就把连续计数清零
 * - 触发后若测点持续正常 RECOVER_TIMES 次，自动关闭告警
 *
 * 状态全在内存（ConcurrentHashMap），单条判定 O(规则数)，
 * 支撑每秒数万条遥测；规则变更时 clearState()。
 */
@Component
public class RuleEngine {
    /** 连续几次正常算恢复 */
    private static final int RECOVER_TIMES = 3;

    private final RuleService ruleService;
    private final AlertService alertService;
    /** (ruleId:deviceId) -> 连续超标次数 */
    private final ConcurrentHashMap<String, AtomicInteger> breachCounters = new ConcurrentHashMap<>();
    /** (ruleId:deviceId) -> 连续正常次数（用于自动恢复） */
    private final ConcurrentHashMap<String, AtomicInteger> recoverCounters = new ConcurrentHashMap<>();

    public RuleEngine(RuleService ruleService, AlertService alertService) {
        this.ruleService = ruleService;
        this.alertService = alertService;
    }

    public void clearState() {
        breachCounters.clear();
        recoverCounters.clear();
        alertService.clearState();
    }

    public void evaluate(Device device, String metricCode, double value) {
        List<AlertRule> rules = ruleService.matchRules(device.getId(), metricCode);
        for (AlertRule rule : rules) {
            String key = rule.getId() + ":" + device.getId();
            boolean breach = isBreach(rule.getOperator(), value, rule.getThreshold());
            if (breach) {
                recoverCounters.remove(key);
                int n = breachCounters.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
                if (n >= rule.getDurationTimes()) {
                    // 触发后清零：下一次触发需要重新攒够连续次数，
                    // 配合静默期形成"触发->抑制->再触发"的风暴抑制节奏
                    breachCounters.remove(key);
                    alertService.fireOrSuppress(rule, device, metricCode, value);
                }
            } else {
                breachCounters.remove(key);
                if (alertService.hasFiringAlert(rule.getId(), device.getId())) {
                    int r = recoverCounters.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
                    if (r >= RECOVER_TIMES) {
                        recoverCounters.remove(key);
                        alertService.autoResolve(rule.getId(), device.getId());
                    }
                } else {
                    recoverCounters.remove(key);
                }
            }
        }
    }

    private boolean isBreach(String operator, double value, double threshold) {
        if ("GT".equals(operator)) return value > threshold;
        if ("LT".equals(operator)) return value < threshold;
        return false;
    }
}
