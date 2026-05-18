---
title: "数据库设计 (Room)"
category: "infrastructure"
tags: ["room", "database", "sqlite", "entity", "dao"]
created: "2026-05-18"
updated: "2026-05-18"
---

Room 数据库存储心率记录和计时器会话，通过 Flow 实现响应式数据访问。

## 数据库概览

- **数据库类**: `HeartRateDatabase`
- **版本**: 当前版本（含 Migration）
- **TypeConverters**: `Converters`（List<Long> ↔ String）

## 实体表

### heart_rate

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long (PK, 自增) | 主键 |
| heartRate | Int | 心率值 (BPM) |
| timestamp | Long | 测量时间戳 |
| date | String | 日期字符串 |

### timer_session

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long (PK, 自增) | 主键 |
| timestamp | Long | 开始时间戳 |
| durationSeconds | Int | 计时时长（秒） |
| tag | String? | 训练标签 |

## DAO 接口

### HeartRateDao

| 方法 | 返回类型 | 说明 |
|------|---------|------|
| `insert()` | Unit | 插入单条记录 |
| `insertAll()` | Unit | 批量插入 |
| `getAllHeartRates()` | Flow<List> | 全部记录（时间倒序） |
| `getHeartRatesBetween()` | Flow<List> | 时间范围查询 |
| `getRecentHeartRates(limit)` | Flow<List> | 最近 N 条 |
| `getHeartRateStats()` | Flow<HeartRateStats> | 统计（avg/max/min/count） |
| `getDailyStats()` | Flow<List<DailyHeartRateStats>> | 按天聚合统计 |
| `deleteBefore()` | Unit | 删除旧数据 |
| `getAllSync()` | List | 同步用，全量读取 |
| `getAllTimestamps()` | List<Long> | 去重用 |

### TimerSessionDao

| 方法 | 返回类型 | 说明 |
|------|---------|------|
| `insert()` | Unit | 插入训练记录 |
| `getCountByDate()` | Flow<List<DateCountPair>> | 按日统计训练次数 |
| `getCountByDateAfter()` | Flow<List<DateCountPair>> | 指定时间后按日统计 |
| `getSessionsAfter()` | Flow<List> | 获取指定时间后的会话 |
| `deleteByTimestamp()` | Unit | 按时间戳删除 |
| `getAllSync()` | List | 同步用 |

## Repository 层

Repository 封装 DAO 操作，提供业务语义接口：

- `HeartRateRepository` — `saveHeartRate()`, `getHeartRateStats()`, `getDailyStats()`
- `TimerSessionRepository` — `saveSession()`, `getCountByDate()`, `getCountByDateAfter()`

## DI 配置

`DatabaseModule` 提供：
- `HeartRateDatabase` 单例
- `HeartRateDao` / `TimerSessionDao`
- `HeartRateRepository` / `TimerSessionRepository`

## 迁移规则

新增实体必须：
1. 更新 `HeartRateDatabase` 的 `@Database(entities = [...])` 列表
2. 递增 `version`
3. 编写 `Migration` 对象

## 关键类

| 类 | 路径 | 说明 |
|----|------|------|
| `HeartRateDatabase` | `data/database/HeartRateDatabase.kt` | 数据库定义 |
| `Converters` | `data/database/Converters.kt` | 类型转换器 |
| `HeartRateEntity` | `data/entity/HeartRateEntity.kt` | 心率实体 |
| `TimerSessionEntity` | `data/entity/TimerSessionEntity.kt` | 训练实体 |
| `HeartRateDao` | `data/dao/HeartRateDao.kt` | 心率 DAO |
| `TimerSessionDao` | `data/dao/TimerSessionDao.kt` | 训练 DAO |

---

## 资源导航

| 类型 | 链接/路径 | 说明 |
|------|----------|------|
| 代码路径 | `app/src/main/java/com/heartratemonitor/data/` | 数据层全部代码 |
| 相关 Wiki | [MVVM 数据流](../architecture/mvvm-data-flow.md) | 数据如何流向 UI |
| 相关 Wiki | [DI 配置](di.md) | Hilt 依赖注入 |
