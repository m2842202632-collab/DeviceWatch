# DEMO 演示指南（5 分钟跑通）

## 第 1 步：先启动后端，再开浏览器

> 顺序不能反：双击 html 文件看到的是空白页，必须先起后端。

```bash
cd device-watch
mvn spring-boot:run -Dspring-boot.run.profiles=demo
```

看到 `Started DeviceWatchApplication` 即启动成功。
（IDEA 里：右键 `DeviceWatchApplication` → Run，VM options 加
`-Dspring.profiles.active=demo`，JDK 选 25 即可，字节码目标是 17。）

## 第 2 步：浏览器打开

```
http://localhost:8080/login.html
```

账号 `admin`，密码 `123456`。

## 第 3 步：看什么（建议路线）

1. **实时监控**（首页）：12 台设备的实时测点值每 2 秒跳动；
   等 1~2 分钟，模拟器注入故障后顶部出现告警滚动条，
   设备卡片变红。点卡片看历史曲线。
2. **告警中心**：筛选"告警中"，点"确认"→"关闭"走一遍；
   注意"抑制次数"列——静默期内的重复超标只计数不打扰。
3. **规则管理**：把"轴承温度过高预警"的阈值从 75 改成 50，
   保存后立即生效，很快会刷出新告警（规则热更新）。
4. **统计分析**：健康度排行、24 小时趋势、MTBF/MTTR。

## 常见问题

- **端口被占**：`application.yml` 改 `server.port`。
- **想看 Kafka 真实链路**：`docker compose up -d` 起 Kafka，
  `application.yml` 里 `devicewatch.kafka.enabled` 改 `true`，重启。
- **MySQL 8.0**：建库后执行 `sql/schema.sql`，
  `application.yml` 改数据源密码，直接 `mvn spring-boot:run`（默认 profile）。
