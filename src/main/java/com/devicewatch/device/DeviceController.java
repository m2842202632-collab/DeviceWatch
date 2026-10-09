package com.devicewatch.device;

import com.devicewatch.common.PageResult;
import com.devicewatch.common.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {
    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping
    public Result<PageResult<Device>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.ok(deviceService.page(keyword, type, page, size));
    }

    @GetMapping("/all")
    public Result<List<Device>> all() {
        return Result.ok(deviceService.listAll());
    }

    @GetMapping("/meta")
    public Result<Map<String, Object>> meta() {
        return Result.ok(deviceService.meta());
    }

    @PostMapping
    public Result<Device> create(@RequestBody Device d) {
        try {
            return Result.ok(deviceService.create(d));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Result<Device> update(@PathVariable Long id, @RequestBody Device d) {
        try {
            return Result.ok(deviceService.update(id, d));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        deviceService.delete(id);
        return Result.ok();
    }
}
