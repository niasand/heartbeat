---
title: "MVVM 数据流与架构"
category: "architecture"
tags: ["mvvm", "architecture", "data-flow", "stateflow"]
created: "2026-05-18"
updated: "2026-05-18"
---

HeartRate Monitor 采用 MVVM + Clean Architecture，通过 StateFlow 实现单向数据流。

## 架构概览

```
┌─────────────┐     collect     ┌──────────────┐     observe     ┌──────────────────┐
│  Compose UI  │ ◄──────────── │  ViewModel    │ ◄───────────── │  Repository       │
│  (Screens)   │               │ (StateFlow)   │                │  (Data Layer)     │
└──────┬───────┘               └──────────────┘                └──────┬───────────┘
       │ emit events                                                   │
       │                                                               │
       ▼                                                               ▼
  User Actions                                              ┌──────────────────┐
                                                           │  Room Database    │
                                                           │  BLE Service      │
                                                           │  Preferences      │
                                                           │  Cloud Sync       │
                                                           └──────────────────┘
```

## 核心组件

| 层 | 组件 | 职责 |
|----|------|------|
| UI | Compose Screens (`ui/screens/`) | 声明式 UI，只读 StateFlow |
| ViewModel | `HeartRateViewModel` | 状态管理、业务逻辑、数据转换 |
| Repository | `HeartRateRepository` / `TimerSessionRepository` | 数据访问抽象层 |
| Data | Room DAOs + BLE + Preferences | 持久化和数据源 |

## 数据流

### 心率数据流

```
BLE Device → BleConnectionManager (StateFlow<Int>)
  → HeartRateViewModel.updateCurrentHeartRate()
    → HeartRateRepository.saveHeartRate()
      → HeartRateDao.insert() → Room DB
    → UI: currentHeartRate StateFlow 更新
```

### 历史数据流

```
Room DB → HeartRateDao (Flow<List<HeartRateEntity>>)
  → HeartRateRepository
    → HeartRateViewModel.heartRateHistory StateFlow
      → HeartRateHistoryScreen (Compose)
```

### 计时器数据流

```
TimerCountdownService (Foreground Service)
  → serviceState StateFlow (IDLE/RUNNING/PAUSED/COMPLETED)
    → HeartRateViewModel
      → TimerHistoryScreen
```

## 设计决策

| 决策 | 原因 | 备选方案 |
|------|------|---------|
| 单 ViewModel | App 复杂度不高，单 VM 简化状态管理 | 多 ViewModel + SharedViewModel |
| StateFlow | Lifecycle-aware，适合 Compose | LiveData（过时） |
| Room + Flow | 自动响应数据变化，无缝对接 Compose | 手动 SQL + Callback |
| Hilt DI | Google 官方推荐，编译期检查 | Koin（运行时） |

## 关键类

| 类 | 路径 | 说明 |
|----|------|------|
| `HeartRateViewModel` | `viewmodel/HeartRateViewModel.kt` | 核心 ViewModel，管理所有状态 |
| `HeartRateRepository` | `data/repository/HeartRateRepository.kt` | 心率数据仓库 |
| `TimerSessionRepository` | `data/repository/TimerSessionRepository.kt` | 计时器会话仓库 |
| `HeartRateDatabase` | `data/database/HeartRateDatabase.kt` | Room 数据库定义 |

---

## 资源导航

| 类型 | 链接/路径 | 说明 |
|------|----------|------|
| 代码路径 | `app/src/main/java/com/heartratemonitor/viewmodel/` | ViewModel 层 |
| 代码路径 | `app/src/main/java/com/heartratemonitor/data/repository/` | Repository 层 |
| 相关 Wiki | [BLE 连接](../features/ble-connection.md) | BLE 数据源 |
| 相关 Wiki | [数据库](../infrastructure/database.md) | Room 数据库 |
