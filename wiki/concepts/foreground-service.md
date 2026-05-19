---
title: "Android Foreground Service"
category: "concepts"
tags: ["foreground-service", "android", "background", "notification", "wake-lock"]
created: "2026-05-18"
updated: "2026-05-18"
---

Android Foreground Service 保证 App 在后台持续运行，是心率监测和倒计时的核心机制。

## 为什么需要 Foreground Service

- Android 系统会杀后台 App 回收资源
- Foreground Service 拥有更高优先级，几乎不会被杀
- 必须显示持续通知（Android 强制要求）
- 即使屏幕关闭也能继续运行

## 项目中的 Foreground Service

| Service | 路径 | 用途 |
|---------|------|------|
| `BleHeartRateService` | `service/BleHeartRateService.kt` | 后台持续监测心率 |
| `TimerCountdownService` | `service/TimerCountdownService.kt` | 后台倒计时 |

## 生命周期

```
startForegroundService()
  → onCreate() → createNotificationChannel()
  → onStartCommand()
    → startForeground(NOTIFICATION_ID, notification)
    → 开始工作...
  → 工作...
  → stopSelf() / stopService()
```

## 关键组件

### Notification Channel

Android 8.0+ 必须创建通知渠道：
```kotlin
NotificationChannel(CHANNEL_ID, CHANNEL_NAME, IMPORTANCE_LOW)
```

### Notification

前台通知必须持续显示，本项目显示：
- BLE Service：当前心率 + 连接状态
- Timer Service：倒计时剩余时间

### WakeLock

`TimerCountdownService` 使用 WakeLock：
- 保证 Doze 模式下计时器仍然运行
- `PARTIAL_WAKE_LOCK` — 保持 CPU 运行，允许关屏

### AlarmManager

`TimerCountdownService` 使用 `setExact()`：
- 精确到秒的定时触发
- Doze 模式下的可靠唤醒

## 注意事项

- **Android 12+**：Foreground Service 需声明 `foregroundServiceType`
- **Android 14+**：启动前台服务有更严格限制
- 通知 ID 不能重复，否则会覆盖
- `stopSelf()` 后通知自动消失
- 退出 App 不等于停止 Service（除非用户主动停止或 App 被杀）

---

## 资源导航

| 类型 | 链接/路径 | 说明 |
|------|----------|------|
| 代码路径 | `service/BleHeartRateService.kt` | BLE 前台服务 |
| 代码路径 | `service/TimerCountdownService.kt` | 计时器前台服务 |
| 相关 Wiki | [BLE 连接](../features/ble-connection.md) | BLE 功能 |
| 相关 Wiki | [倒计时训练](../features/timer-countdown.md) | 计时器功能 |
