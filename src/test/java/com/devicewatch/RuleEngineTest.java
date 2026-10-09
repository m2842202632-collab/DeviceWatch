package com.devicewatch;

import com.devicewatch.alert.AlertService;
import com.devicewatch.alert.RuleEngine;
import com.devicewatch.device.Device;
import com.devicewatch.rule.AlertRule;
import com.devicewatch.rule.RuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 规则引擎防抖逻辑：连续超标 N 次才触发；偶发毛刺不触发；
 * 触发后计数清零；持续正常自动恢复走 AlertService。
 */
@ExtendWith(MockitoExtension.class)
class RuleEngineTest {
    @Mock RuleService ruleService;
    @Mock AlertService alertService;

    private RuleEngine engine;
    private Device device;
    private AlertRule rule;

    @BeforeEach
    void setup() {
        engine = new RuleEngine(ruleService, alertService);
        device = new Device();
        device.setId(1L);
        device.setCode("PUMP-01");
        device.setType("PUMP");
        rule = new AlertRule();
        rule.setId(10L);
        rule.setMetricCode("temperature");
        rule.setOperator("GT");
        rule.setThreshold(80.0);
        rule.setDurationTimes(3);
        rule.setLevel("WARN");
        rule.setSilenceMinutes(5);
        lenient().when(ruleService.matchRules(eq(1L), eq("temperature")))
                .thenReturn(Collections.singletonList(rule));
        lenient().when(alertService.hasFiringAlert(anyLong(), anyLong())).thenReturn(false);
    }

    @Test
    void firesAfterConsecutiveBreaches() {
        engine.evaluate(device, "temperature", 81);
        engine.evaluate(device, "temperature", 82);
        verify(alertService, never()).fireOrSuppress(any(), any(), any(), anyDouble());
        engine.evaluate(device, "temperature", 83);
        verify(alertService, times(1)).fireOrSuppress(eq(rule), eq(device), eq("temperature"), eq(83.0));
    }

    @Test
    void sporadicSpikeDoesNotFire() {
        engine.evaluate(device, "temperature", 81);
        engine.evaluate(device, "temperature", 70); // 一次正常就清零
        engine.evaluate(device, "temperature", 82);
        engine.evaluate(device, "temperature", 71);
        engine.evaluate(device, "temperature", 83);
        verify(alertService, never()).fireOrSuppress(any(), any(), any(), anyDouble());
    }

    @Test
    void counterResetsAfterFire() {
        // 触发一次后计数清零，需要重新攒够 3 次才再次触发
        for (int i = 0; i < 3; i++) engine.evaluate(device, "temperature", 85);
        engine.evaluate(device, "temperature", 85);
        engine.evaluate(device, "temperature", 85);
        verify(alertService, times(1)).fireOrSuppress(any(), any(), any(), anyDouble());
        engine.evaluate(device, "temperature", 85);
        verify(alertService, times(2)).fireOrSuppress(any(), any(), any(), anyDouble());
    }

    @Test
    void ltOperatorWorks() {
        rule.setOperator("LT");
        rule.setThreshold(10.0);
        for (int i = 0; i < 3; i++) engine.evaluate(device, "temperature", 5);
        verify(alertService, times(1)).fireOrSuppress(eq(rule), eq(device), eq("temperature"), eq(5.0));
    }

    @Test
    void unmatchedMetricIsIgnored() {
        lenient().when(ruleService.matchRules(eq(1L), eq("vibration")))
                .thenReturn(Collections.emptyList());
        engine.evaluate(device, "vibration", 99);
        verify(alertService, never()).fireOrSuppress(any(), any(), any(), anyDouble());
        verify(alertService, never()).hasFiringAlert(anyLong(), anyLong());
    }
}
