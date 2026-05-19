---
title: "BLE 协议与 GATT"
category: "concepts"
tags: ["ble", "bluetooth", "gatt", "protocol", "heart-rate-profile"]
created: "2026-05-18"
updated: "2026-05-18"
---

BLE (Bluetooth Low Energy) 协议基础，以及本项目中使用的 Heart Rate Profile。

## BLE 基本概念

### 角色

| 角色 | 说明 | 本项目中的角色 |
|------|------|--------------|
| Central | 发起连接的设备 | Android 手机 |
| Peripheral | 被连接的设备 | COROS 心率带 |

### GATT 结构

```
GATT Server (心率带)
├── Service: Heart Rate (0x180D)
│   ├── Characteristic: Heart Rate Measurement (0x2A37) [Notify]
│   └── Characteristic: Body Sensor Location (0x2A38) [Read]
└── Service: Device Information (0x180A)
    └── ...
```

### 关键术语

- **Service** — 一组相关 Characteristic 的集合
- **Characteristic** — 具体的数据值，有 Read/Write/Notify 属性
- **Descriptor** — Characteristic 的配置信息（如启用 Notify）
- **UUID** — Service/Characteristic 的唯一标识

## Heart Rate Measurement 解析

项目在 `HeartRateData.parseFromBLEData()` 中解析：

```
Byte 0: Flags
  Bit 0: Heart Rate Format (0=UINT8, 1=UINT16)
  Bit 1-2: Sensor Contact Status
  Bit 3: Energy Expended Present
  Bit 4: RR-Interval Present

Byte 1 (or 1-2): Heart Rate Value
  UINT8: 直接读取
  UINT16: Little-Endian 读取
```

## 连接流程

```
1. scan (过滤 Heart Rate Service UUID)
2. connectGatt()
3. discoverServices()
4. 找到 Heart Rate Service (0x180D)
5. 找到 Heart Rate Measurement (0x2A37)
6. setCharacteristicNotification(enabled=true)
7. writeDescriptor(ENABLE_NOTIFICATION_VALUE)
8. onCharacteristicChanged() 回调收到心率数据
```

## Android 权限

| 权限 | 最低版本 | 说明 |
|------|---------|------|
| `BLUETOOTH` | API 1 | 基础蓝牙 |
| `BLUETOOTH_ADMIN` | API 1 | 扫描和连接 |
| `ACCESS_FINE_LOCATION` | API 23 | 扫描需要位置权限 |
| `BLUETOOTH_SCAN` | API 31 | Android 12+ 扫描 |
| `BLUETOOTH_CONNECT` | API 31 | Android 12+ 连接 |

---

## 资源导航

| 类型 | 链接/路径 | 说明 |
|------|----------|------|
| 代码路径 | `ble/HeartRateData.kt` | 数据解析实现 |
| 代码路径 | `ble/BleConnectionManager.kt` | GATT 连接逻辑 |
| 相关 Wiki | [BLE 连接](../features/ble-connection.md) | 项目 BLE 功能实现 |
