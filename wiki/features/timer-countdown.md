---
title: "倒计时训练"
category: "features"
tags: ["timer", "countdown", "training", "foreground-service"]
created: "2026-05-18"
updated: "2026-05-18"
---

倒计时训练功能，支持设置时长、暂停/恢复、完成后保存记录。

## 功能描述

- 设置倒计时时长（秒）
- 开始/暂停/恢复/停止控制
- 后台可靠计时（Foreground Service + AlarmManager + WakeLock）
- 完成时播放声音 + 震动
- 自动保存训练记录到数据库
- 训练标签（可选）

## 核心流程

```
用户设置时长 → HeartRateViewModel.startTimerService()
  → TimerCountdownService.startCountdown()
    → acquireWakeLock() + setExactAlarm()
    → 通知栏显示倒计时
    → 到时 → onTimerComplete()
      → playCompletionSound() + vibrate()
      → TimerSessionRepository.saveSession()
      → serviceState = COMPLETED
```

## 状态机

```
IDLE → RUNNING → PAUSED → RUNNING (resume)
                   ↓
                COMPLETED
                   ↑
              RUNNING (timer complete)
```

`TimerServiceState` sealed class:
- `IDLE` — 初始/停止状态
- `RUNNING(remainingSeconds)` — 运行中
- `PAUSED(remainingSeconds)` — 暂停
- `COMPLETED(durationSeconds, tag)` — 完成

## 后台可靠性保障

| 机制 | 作用 |
|------|------|
| Foreground Service | 系统保活，不被后台限制杀掉 |
| AlarmManager (setExact) | 精确到秒的定时器 |
| WakeLock | Doze 模式下保持 CPU 唤醒 |
| Notification | 前台通知，Android 强制要求 |

## 训练记录

`TimerSessionEntity` 字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long (PK) | 自增主键 |
| timestamp | Long | 开始时间戳 |
| durationSeconds | Int | 计时时长 |
| tag | String? | 训练标签 |

## 历史与图表

`TimerHistoryScreen` 展示训练历史：
- 按日期/标签过滤
- 柱状图展示训练频次
- 支持按天/按月聚合

## 关键类

| 类 | 路径 | 说明 |
|----|------|------|
| `TimerCountdownService` | `service/TimerCountdownService.kt` | 倒计时前台服务 |
| `TimerSessionEntity` | `data/entity/TimerSessionEntity.kt` | 训练记录实体 |
| `TimerSessionDao` | `data/dao/TimerSessionDao.kt` | 数据访问 |
| `TimerSessionRepository` | `data/repository/TimerSessionRepository.kt` | 仓库层 |
| `TimerHistoryScreen` | `ui/screens/TimerHistoryScreen.kt` | 历史和图表 UI |

---

## 资源导航

| 类型 | 链接/路径 | 说明 |
|------|----------|------|
| 代码路径 | `service/TimerCountdownService.kt` | 倒计时服务实现 |
| 代码路径 | `ui/screens/TimerHistoryScreen.kt` | 历史图表 UI |
| 相关 Wiki | [前台服务](../concepts/foreground-service.md) | Foreground Service 机制 |
| 相关 Wiki | [数据库](../infrastructure/database.md) | 训练记录存储 |
