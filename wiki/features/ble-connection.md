---
title: "BLE 连接与扫描"
category: "features"
tags: ["ble", "bluetooth", "scanning", "connection", "reconnect"]
created: "2026-05-18"
updated: "2026-05-18"
---

BLE 连接模块负责扫描、连接、自动重连心率带设备，是数据采集的核心入口。

## 功能描述

- 扫描附近 BLE 心率带设备（支持 COROS、POLAR、Wahoo 等品牌）
- 连接指定设备并订阅心率数据通知
- 自动重连（最多 100 次重试）
- 心跳超时检测（连接健康监控）
- 蜂鸣反馈（根据心率调节频率）

## 核心流程

```
App 启动 → BleScanner.startScan()
  → 发现设备 → DeviceInfo.isCorosDevice() 过滤
    → 用户选择 / 自动连接上次设备
      → BleConnectionManager.connectToDevice()
        → 发现 GATT Service (UUID: 180D)
          → 订阅心率特征值 (UUID: 2A37)
            → 收到心率数据 → StateFlow 通知 UI
```

## 关键类和接口

| 类 | 路径 | 说明 |
|----|------|------|
| `BleScanner` | `ble/BleScanner.kt` | BLE 设备扫描管理 |
| `BleConnectionManager` | `ble/BleConnectionManager.kt` | BLE 连接、重连、心跳监控 |
| `DeviceInfo` | `ble/DeviceInfo.kt` | 设备信息数据类 + 过滤逻辑 |
| `HeartRateData` | `ble/HeartRateData.kt` | 心率数据解析（BLE 协议） |
| `BleHeartRateService` | `service/BleHeartRateService.kt` | BLE 前台服务（后台运行） |

## BLE UUID

| UUID | 用途 |
|------|------|
| `0000180D-...-00805F9B34FB` | Heart Rate Service |
| `00002A37-...-00805F9B34FB` | Heart Rate Measurement |

## 设备过滤关键词

`COROS`, `POLAR`, `EPIC`, `HEART`, `HRM`, `心率带`, `HR-`, `HR`, `PULSE`, `CHEST`, `STRAP`, `MONITOR`, `BAND`, `SENSOR`, `Wahoo`, `Garmin`, `Suunto`

## 自动重连机制

1. 连接成功后保存设备地址到 PreferencesManager
2. 断连后启动重连循环（最多 100 次）
3. 每次重连间隔递增
4. App 启动时检查是否有已保存的设备地址，有则自动连接

## 心跳超时检测

- 连接成功后启动心跳监控
- 超过一定时间未收到心率数据 → 判定连接异常
- 触发自动重连

## Foreground Service

`BleHeartRateService` 作为前台服务运行：
- 保证后台持续监测心率
- 通知栏显示当前心率和连接状态
- 阈值告警（震动 + 语音 + 声音），30 秒冷却

## 边界情况

- 设备名称为 null 时跳过（某些 BLE 广播不含名称）
- 心率值 0 或超出 30-300 BPM 范围视为无效
- Android 12+ 需要 BLUETOOTH_SCAN/CONNECT 权限
- 扫描失败回调需处理（位置服务未开启等）

---

## 资源导航

| 类型 | 链接/路径 | 说明 |
|------|----------|------|
| 代码路径 | `app/src/main/java/com/heartratemonitor/ble/` | BLE 模块全部代码 |
| 代码路径 | `app/src/main/java/com/heartratemonitor/service/BleHeartRateService.kt` | BLE 前台服务 |
| 相关 Wiki | [MVVM 数据流](../architecture/mvvm-data-flow.md) | 数据如何流向 UI |
| 相关 Wiki | [BLE 协议概念](../concepts/ble-protocol.md) | BLE GATT 概念说明 |
| 相关 Wiki | [前台服务](../concepts/foreground-service.md) | Android 前台服务机制 |
