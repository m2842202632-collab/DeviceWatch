package com.devicewatch.device;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devicewatch.common.PageResult;
import com.devicewatch.ingest.TelemetryIngestService;
import com.devicewatch.metric.MetricDef;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class DeviceService {
    private final DeviceMapper deviceMapper;
    private final TelemetryIngestService ingestService;

    public DeviceService(DeviceMapper deviceMapper, TelemetryIngestService ingestService) {
        this.deviceMapper = deviceMapper;
        this.ingestService = ingestService;
    }

    public PageResult<Device> page(String keyword, String type, long page, long size) {
        LambdaQueryWrapper<Device> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.like(Device::getCode, keyword).or().like(Device::getName, keyword);
        }
        if (StringUtils.hasText(type)) w.eq(Device::getType, type);
        w.orderByAsc(Device::getCode);
        Page<Device> p = deviceMapper.selectPage(new Page<>(page, size), w);
        return PageResult.of(p.getRecords(), p.getTotal(), page, size);
    }

    public List<Device> listAll() {
        return deviceMapper.selectList(new LambdaQueryWrapper<Device>().orderByAsc(Device::getCode));
    }

    public Device create(Device d) {
        if (!StringUtils.hasText(d.getCode()) || !StringUtils.hasText(d.getName())) {
            throw new IllegalArgumentException("设备编码和名称不能为空");
        }
        if (deviceMapper.findByCode(d.getCode()) != null) {
            throw new IllegalArgumentException("设备编码已存在: " + d.getCode());
        }
        if (!MetricDef.types().contains(d.getType())) {
            throw new IllegalArgumentException("未知设备类型: " + d.getType());
        }
        d.setId(null);
        d.setStatus("OFFLINE");
        d.setCreatedAt(LocalDateTime.now());
        deviceMapper.insert(d);
        ingestService.refreshDeviceCache(); // 同编码曾被删除重建时，防旧缓存把数据写到已删除的 id 上
        return d;
    }

    public Device update(Long id, Device d) {
        Device old = deviceMapper.selectById(id);
        if (old == null) throw new IllegalArgumentException("设备不存在");
        old.setName(d.getName());
        old.setLocation(d.getLocation());
        deviceMapper.updateById(old);
        ingestService.refreshDeviceCache(); // 名称变更后缓存同步
        return old;
    }

    public void delete(Long id) {
        deviceMapper.deleteById(id);
        ingestService.refreshDeviceCache(); // 否则模拟器/Kafka 数据会继续写入已删除设备的缓存 id
    }

    /** 测点字典 + 设备类型，供前端下拉框 */
    public Map<String, Object> meta() {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("types", MetricDef.typeNames());
        Map<String, List<Map<String, String>>> metrics = new java.util.LinkedHashMap<>();
        for (String t : MetricDef.types()) {
            List<Map<String, String>> list = new java.util.ArrayList<>();
            for (MetricDef md : MetricDef.ofType(t)) {
                Map<String, String> e = new java.util.LinkedHashMap<>();
                e.put("code", md.code); e.put("name", md.name); e.put("unit", md.unit);
                list.add(e);
            }
            metrics.put(t, list);
        }
        m.put("metrics", metrics);
        return m;
    }
}
