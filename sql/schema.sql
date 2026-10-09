-- device-watch 表结构（MySQL 8.0 / H2 通用）

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(128) NOT NULL,
    salt VARCHAR(64) NOT NULL,
    real_name VARCHAR(64),
    status INT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL,
    CONSTRAINT uq_user_username UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS device (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    type VARCHAR(32) NOT NULL,
    location VARCHAR(128),
    status VARCHAR(16) NOT NULL DEFAULT 'OFFLINE',
    last_seen_at DATETIME,
    created_at DATETIME NOT NULL,
    CONSTRAINT uq_device_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS alert_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    device_id BIGINT,
    metric_code VARCHAR(64) NOT NULL,
    operator VARCHAR(8) NOT NULL,
    threshold DOUBLE NOT NULL,
    duration_times INT NOT NULL DEFAULT 3,
    level VARCHAR(16) NOT NULL DEFAULT 'WARN',
    silence_minutes INT NOT NULL DEFAULT 5,
    escalate_minutes INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS alert_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL,
    metric_code VARCHAR(64) NOT NULL,
    level VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'FIRING',
    trigger_value DOUBLE,
    max_value DOUBLE,
    fire_count INT NOT NULL DEFAULT 1,
    suppressed_count INT NOT NULL DEFAULT 0,
    first_fired_at DATETIME NOT NULL,
    last_fired_at DATETIME NOT NULL,
    ack_by VARCHAR(64),
    ack_at DATETIME,
    resolved_at DATETIME,
    remark VARCHAR(512)
);
CREATE INDEX IF NOT EXISTS idx_alert_status ON alert_record (status);
CREATE INDEX IF NOT EXISTS idx_alert_device ON alert_record (device_id);

CREATE TABLE IF NOT EXISTS telemetry (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT NOT NULL,
    metric_code VARCHAR(64) NOT NULL,
    metric_value DOUBLE NOT NULL,
    ts DATETIME NOT NULL,
    CONSTRAINT uq_telemetry UNIQUE (device_id, metric_code, ts)
);
CREATE INDEX IF NOT EXISTS idx_telemetry_query ON telemetry (device_id, metric_code, ts);
