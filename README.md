# DeviceWatch 设备智能监控告警平台

基于 Kafka 的工业设备实时监控与智能告警平台：设备遥测经 Kafka 接入，
规则引擎实时判定，告警风暴抑制 + 自动升级 + 离线检测，大屏实时展示。

## 技术栈

| 技术 | 版本 | 说明 |
|---|---|---|
| Spring Boot | 3.2.5 | 字节码 Java 17，可跑在 JDK 17/21/25 上 |
| spring-kafka | 3.1.4 | 遥测接入，手动提交 offset |
| MyBatis-Plus | 3.5.7 | 持久层 |
| MySQL | 8.0 | 生产数据库（`sql/` 下有表结构） |
| H2 | 2.x | demo 模式零依赖启动 |
| WebSocket | — | 遥测/告警实时推送大屏 |
| ECharts | 5.5 | 前端图表（已本地化，无需外网） |

> **关于 JDK 25**：本机装的可以是 JDK 25，但 pom 里 `java.version` 保持 17。
> Spring Boot 3.2.5 内嵌的 ASM 最高只识别 class file v67（Java 23），
> 打成 Java 25 字节码（v69）会在启动扫描时直接崩溃。字节码 17 可在 JDK 17/21/25 上运行。

## 两种运行模式

### A. 演示模式（零依赖，推荐先跑通）

```bash
# 1. 先启动后端（demo profile：H2 内存库 + 内置设备模拟器，无需 MySQL/Kafka）
mvn spring-boot:run -Dspring-boot.run.profiles=demo

# 2. 再开浏览器访问（不要双击 html 文件）
http://localhost:8080/login.html   # admin / 123456
```

模拟器每 2 秒为 12 台设备生成遥测数据并随机注入故障，
1 分钟内就能在「实时监控」看到曲线、在「告警中心」看到告警全链路。

### B. 生产模式（MySQL 8.0 + 真实 Kafka）

```bash
# 1. 建库并执行 sql/schema.sql（MySQL 8.0）
mysql -u root -p -e "CREATE DATABASE device_watch CHARACTER SET utf8mb4;"
mysql -u root -p device_watch < sql/schema.sql

# 2. 启动 Kafka（单容器 KRaft，无需 Zookeeper）
docker compose up -d

# 3. 修改 application.yml：devicewatch.kafka.enabled=true，数据源密码
# 4. 启动
mvn spring-boot:run
```

Kafka 模式下：模拟器 → `device-telemetry` Topic（key=deviceId，6 分区）
→ 消费者（手动 ack）→ 接入层。外部设备也可直接往 Topic 里发 JSON：
`{"deviceId":"PUMP-01","metric":"temperature","value":72.5,"ts":1728000000000}`

## 核心设计（面试可讲）

1. **分区有序**：生产者 key=deviceId，同一设备数据落同一分区，分区内有序，
   规则引擎的"连续超标 N 次"判定不被乱序打乱。
2. **at-least-once + 业务幂等**：消费者手动提交 offset（处理成功才 ack）；
   `telemetry` 表对 `(device_id, metric_code, ts)` 建唯一键，Kafka 重复投递直接丢弃。
3. **毒消息隔离**：JSON 解析失败记日志后直接 ack，不卡死分区。
4. **告警防抖**：连续超标 N 次才触发，偶发毛刺不告警；一次正常即清零。
5. **风暴抑制**：静默期内重复触发不新增记录、不推送，只累加 `suppressed_count`。
6. **自动升级**：WARN 告警超时未确认自动升级 CRITICAL；测点恢复后自动关闭。
7. **离线检测**：心跳超时（默认 90s）判离线并告警，心跳恢复自动关闭。
8. **规则热更新**：启用中的规则缓存在内存（CopyOnWriteArrayList），
   增删改后即时生效并清理引擎状态。

## 接口一览

- `POST /api/auth/login` 登录（`X-Token` 请求头鉴权）
- `/api/devices` 设备 CRUD，`/api/devices/meta` 测点字典
- `/api/rules` 规则 CRUD + 启用/停用
- `/api/alerts` 告警查询/确认/关闭
- `/api/dashboard/*` 快照/总览/趋势/健康度/MTBF/历史曲线
- `WS /ws/monitor` 遥测与告警实时推送

## 测试

```bash
mvn test
```

- `RuleEngineTest`：防抖计数、毛刺过滤、触发后清零、LT 算子
- `AlertFlowTest`：H2 端到端（触发→静默抑制→自动恢复→确认/关闭→幂等→离线）
- `KafkaConsumerPathTest`：直调 `@KafkaListener` 方法，验证消费路径与毒消息处理
