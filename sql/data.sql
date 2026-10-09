-- device-watch 演示数据（demo profile 自动装载）

INSERT INTO sys_user (username, password_hash, salt, real_name, status, created_at) VALUES
('admin', 'ddc24f2a3ac7a7c94013dd85ef323196078754d672edc6f189b66d046edf7832', '955e766a2949bb134b0edc15ee525f3e', '管理员', 1, NOW());

INSERT INTO device (code, name, type, location, status, created_at) VALUES
('PUMP-01', '1# 循环水泵', 'PUMP', '动力车间', 'OFFLINE', NOW()),
('PUMP-02', '2# 循环水泵', 'PUMP', '动力车间', 'OFFLINE', NOW()),
('PUMP-03', '消防稳压泵', 'PUMP', '消防泵房', 'OFFLINE', NOW()),
('PUMP-04', '冷却塔补水泵', 'PUMP', '冷却塔', 'OFFLINE', NOW()),
('FAN-01', '1# 引风机', 'FAN', '锅炉房', 'OFFLINE', NOW()),
('FAN-02', '2# 引风机', 'FAN', '锅炉房', 'OFFLINE', NOW()),
('FAN-03', '除尘风机', 'FAN', '除尘站', 'OFFLINE', NOW()),
('COMP-01', '1# 空压机', 'COMPRESSOR', '空压站', 'OFFLINE', NOW()),
('COMP-02', '2# 空压机', 'COMPRESSOR', '空压站', 'OFFLINE', NOW()),
('CV-01', '1# 输煤皮带', 'CONVEYOR', '输煤栈桥', 'OFFLINE', NOW()),
('CV-02', '2# 输煤皮带', 'CONVEYOR', '输煤栈桥', 'OFFLINE', NOW()),
('CV-03', '成品输送皮带', 'CONVEYOR', '包装车间', 'OFFLINE', NOW());

-- 全局规则（device_id 为空 = 全部设备）
INSERT INTO alert_rule (name, device_id, metric_code, operator, threshold, duration_times, level, silence_minutes, escalate_minutes, enabled, created_at) VALUES
('轴承温度过高预警', NULL, 'temperature', 'GT', 75, 3, 'WARN', 10, 30, TRUE, NOW()),
('轴承温度严重超标', NULL, 'temperature', 'GT', 90, 2, 'CRITICAL', 10, 15, TRUE, NOW()),
('振动速度超标', NULL, 'vibration', 'GT', 4.5, 3, 'WARN', 10, 60, TRUE, NOW()),
('出口压力异常偏高', NULL, 'pressure', 'GT', 0.85, 5, 'WARN', 15, 0, TRUE, NOW());
