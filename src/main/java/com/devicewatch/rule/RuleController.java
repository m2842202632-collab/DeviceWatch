package com.devicewatch.rule;

import com.devicewatch.common.PageResult;
import com.devicewatch.common.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rules")
public class RuleController {
    private final RuleService ruleService;

    public RuleController(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    @GetMapping
    public Result<PageResult<AlertRule>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.ok(ruleService.page(keyword, page, size));
    }

    @GetMapping("/all")
    public Result<List<AlertRule>> all() {
        return Result.ok(ruleService.listAll());
    }

    @PostMapping
    public Result<AlertRule> create(@RequestBody AlertRule r) {
        try {
            return Result.ok(ruleService.create(r));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Result<AlertRule> update(@PathVariable Long id, @RequestBody AlertRule r) {
        try {
            return Result.ok(ruleService.update(id, r));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        ruleService.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/toggle")
    public Result<Void> toggle(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        try {
            ruleService.toggle(id, Boolean.TRUE.equals(body.get("enabled")));
            return Result.ok();
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }
}
