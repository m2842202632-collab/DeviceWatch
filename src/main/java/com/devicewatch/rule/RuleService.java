package com.devicewatch.rule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devicewatch.alert.RuleEngine;
import com.devicewatch.common.PageResult;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 规则服务。启用中的规则缓存在内存（CopyOnWriteArrayList），
 * 规则引擎每条遥测都要匹配，走 DB 的话 QPS 直接被打穿。
 * 增删改后失效缓存 + 通知规则引擎清理状态。
 */
@Service
public class RuleService {
    private final AlertRuleMapper ruleMapper;
    private final RuleEngine ruleEngine;
    private volatile List<AlertRule> cache = new CopyOnWriteArrayList<>();

    public RuleService(AlertRuleMapper ruleMapper, @Lazy RuleEngine ruleEngine) {
        this.ruleMapper = ruleMapper;
        this.ruleEngine = ruleEngine;
        refreshCache();
    }

    public synchronized void refreshCache() {
        cache = new CopyOnWriteArrayList<>(ruleMapper.findEnabled());
    }

    /** 匹配某设备某测点的规则：deviceId 为空 = 全局规则 */
    public List<AlertRule> matchRules(Long deviceId, String metricCode) {
        List<AlertRule> hit = new ArrayList<>();
        for (AlertRule r : cache) {
            if (!metricCode.equals(r.getMetricCode())) continue;
            if (r.getDeviceId() != null && !r.getDeviceId().equals(deviceId)) continue;
            hit.add(r);
        }
        return hit;
    }

    public PageResult<AlertRule> page(String keyword, long page, long size) {
        LambdaQueryWrapper<AlertRule> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) w.like(AlertRule::getName, keyword);
        w.orderByAsc(AlertRule::getId);
        Page<AlertRule> p = ruleMapper.selectPage(new Page<>(page, size), w);
        return PageResult.of(p.getRecords(), p.getTotal(), page, size);
    }

    public List<AlertRule> listAll() {
        return ruleMapper.selectList(new LambdaQueryWrapper<AlertRule>().orderByAsc(AlertRule::getId));
    }

    public AlertRule create(AlertRule r) {
        validate(r);
        r.setId(null);
        r.setCreatedAt(LocalDateTime.now());
        if (r.getEnabled() == null) r.setEnabled(true);
        ruleMapper.insert(r);
        afterChange();
        return r;
    }

    public AlertRule update(Long id, AlertRule r) {
        AlertRule old = ruleMapper.selectById(id);
        if (old == null) throw new IllegalArgumentException("规则不存在");
        validate(r);
        r.setId(id);
        r.setCreatedAt(old.getCreatedAt());
        r.setEnabled(old.getEnabled()); // 启用状态只允许走 toggle 接口变更，编辑不许顺带复活
        ruleMapper.updateById(r);
        afterChange();
        return r;
    }

    public void delete(Long id) {
        ruleMapper.deleteById(id);
        afterChange();
    }

    public void toggle(Long id, boolean enabled) {
        AlertRule r = ruleMapper.selectById(id);
        if (r == null) throw new IllegalArgumentException("规则不存在");
        r.setEnabled(enabled);
        ruleMapper.updateById(r);
        afterChange();
    }

    private void afterChange() {
        refreshCache();
        ruleEngine.clearState();
    }

    private void validate(AlertRule r) {
        if (!StringUtils.hasText(r.getName())) throw new IllegalArgumentException("规则名称不能为空");
        if (!StringUtils.hasText(r.getMetricCode())) throw new IllegalArgumentException("测点不能为空");
        if (!"GT".equals(r.getOperator()) && !"LT".equals(r.getOperator()))
            throw new IllegalArgumentException("比较符只能是 GT/LT");
        if (r.getThreshold() == null) throw new IllegalArgumentException("阈值不能为空");
        if (!"WARN".equals(r.getLevel()) && !"CRITICAL".equals(r.getLevel()))
            throw new IllegalArgumentException("级别只能是 WARN/CRITICAL");
        if (r.getDurationTimes() == null || r.getDurationTimes() < 1) r.setDurationTimes(1);
        if (r.getSilenceMinutes() == null || r.getSilenceMinutes() < 0) r.setSilenceMinutes(5);
        if (r.getEscalateMinutes() == null || r.getEscalateMinutes() < 0) r.setEscalateMinutes(0);
    }
}
