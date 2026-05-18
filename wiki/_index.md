# HeartRate Monitor 知识库

> AI Agent 导航型知识库。为 Claude Code 提供结构化的项目知识。

## 分类导航

### architecture — 系统架构

整体架构、数据流、组件交互设计。

<!-- 文档列表（由 /add_wiki 自动维护） -->

- [MVVM 数据流与架构](architecture/mvvm-data-flow.md) — MVVM + Clean Architecture，StateFlow 单向数据流

### features — 功能模块

各功能模块的详细文档（BLE 连接、心率监测、倒计时、历史记录、设置等）。

<!-- 文档列表（由 /add_wiki 自动维护） -->

- [BLE 连接与扫描](features/ble-connection.md) — BLE 设备扫描、连接、自动重连、心跳超时检测
- [心率监测与告警](features/heart-rate-monitoring.md) — 实时心率显示、阈值告警、历史统计
- [倒计时训练](features/timer-countdown.md) — 倒计时训练，后台可靠计时，训练记录

### infrastructure — 基础设施

构建系统、CI/CD、部署、依赖管理。

<!-- 文档列表（由 /add_wiki 自动维护） -->

- [数据库设计 (Room)](infrastructure/database.md) — Room 数据库实体、DAO、Repository 层
- [依赖注入 (Hilt)](infrastructure/di.md) — Hilt DI 模块配置和依赖关系图
- [构建与发布流程](infrastructure/build-and-release.md) — Gradle 构建、签名配置、版本历史

### workflows — 流程文档

开发流程、发布流程、调试指南。

<!-- 文档列表（由 /add_wiki 自动维护） -->

_暂无文档_

### concepts — 领域概念

BLE 协议、心率区间、Android 开发概念。

<!-- 文档列表（由 /add_wiki 自动维护） -->

- [BLE 协议与 GATT](concepts/ble-protocol.md) — BLE GATT 结构、Heart Rate Profile、数据解析
- [Android Foreground Service](concepts/foreground-service.md) — 前台服务机制、WakeLock、AlarmManager

### external — 外部资源

第三方库文档、API 参考链接。

<!-- 文档列表（由 /add_wiki 自动维护） -->

_暂无文档_

### qa — 质量保证

Bug 记录、踩坑经验、测试问题。

<!-- 文档列表（由 /add_wiki 自动维护） -->

- [Bug & Issue 记录](qa/issue.md) — 每次修复后自动追加的踩坑记录
