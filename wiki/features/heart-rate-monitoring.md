---
title: "心率监测与告警"
category: "features"
tags: ["heart-rate", "monitoring", "alert", "threshold", "tts"]
created: "2026-05-18"
updated: "2026-05-18"
---

实时心率监测、数据记录、阈值告警（震动/语音/声音）。

## 功能描述

- 实时显示当前心率数值
- 心率波形动画（HeartRateWaveView）
- 历史心率折线图（均值/最大/最小标注）
- 阈值告警：心率超出高/低阈值时触发多模态提醒
- 后台持续监测（Foreground Service）

## 核心流程

```
BLE 心率数据 → BleConnectionManager.currentHeartRate (StateFlow)
  → BleHeartRateService.checkThresholds()
    ├─ 超出阈值 → triggerAlert() → 震动 + TTS + 声音（30s 冷却）
    └─ 正常 → updateNotification() → 通知栏更新
  → HeartRateViewModel.updateCurrentHeartRate()
    → HeartRateRepository.saveHeartRate() → Room 持久化
    → UI StateFlow 更新
```

## 心率数据解析

`HeartRateData.parseFromBLEData()` 按 Bluetooth Heart Rate Profile 标准解析：
- 读取 Flags byte 判断数据格式
- 支持 8-bit（UINT8）和 16-bit（UINT16）心率值
- 验证范围 30-300 BPM，超出视为无效

## 阈值告警

| 参数 | 默认值 | 说明 |
|------|--------|------|
| 高阈值 | 用户可配 | 超过触发高心率告警 |
| 低阈值 | 用户可配 | 低于触发低心率告警 |
| 冷却时间 | 30 秒 | 两次告警最小间隔 |
| TTS 语言 | 中文 | 语音提示内容 |

告警模态：
1. **震动** — Pattern 振动
2. **声音** — 系统默认通知音
3. **语音** — TextToSpeech 中文语音提醒

## 历史统计

`HeartRateDao` 提供聚合查询：
- `getHeartRateStats()` → 平均/最大/最小/总数
- `getDailyStats()` → 按天聚合（最近 7 天）
- `getHeartRatesBetween()` → 时间范围查询

## 关键类

| 类 | 路径 | 说明 |
|----|------|------|
| `BleHeartRateService` | `service/BleHeartRateService.kt` | 心率前台服务 |
| `HeartRateViewModel` | `viewmodel/HeartRateViewModel.kt` | 状态管理和统计 |
| `HeartRateWaveView` | `ui/components/HeartRateWaveView.kt` | 心率波形动画 |
| `HeartRateScreen` | `ui/screens/HeartRateScreen.kt` | 主界面 |
| `HeartRateHistoryScreen` | `ui/screens/HeartRateHistoryScreen.kt` | 历史折线图 |

---

## 资源导航

| 类型 | 链接/路径 | 说明 |
|------|----------|------|
| 代码路径 | `service/BleHeartRateService.kt` | 告警和前台服务逻辑 |
| 代码路径 | `ble/HeartRateData.kt` | BLE 数据解析 |
| 相关 Wiki | [BLE 连接](ble-connection.md) | 设备连接流程 |
| 相关 Wiki | [数据库](../infrastructure/database.md) | 心率数据存储 |
