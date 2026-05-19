---
title: "依赖注入 (Hilt)"
category: "infrastructure"
tags: ["hilt", "di", "dagger", "dependency-injection"]
created: "2026-05-18"
updated: "2026-05-18"
---

Dagger Hilt 管理所有组件的依赖注入，编译期检查，无运行时开销。

## DI 模块

### AppModule

| Provides | 类型 | 说明 |
|----------|------|------|
| `provideBleScanner()` | BleScanner (Singleton) | BLE 扫描器 |
| `provideBleConnectionManager()` | BleConnectionManager (Singleton) | BLE 连接管理 |

### DatabaseModule

| Provides | 类型 | 说明 |
|----------|------|------|
| `provideHeartRateDatabase()` | HeartRateDatabase (Singleton) | Room 数据库 |
| `provideHeartRateDao()` | HeartRateDao | 心率 DAO |
| `provideTimerSessionDao()` | TimerSessionDao | 训练 DAO |
| `provideHeartRateRepository()` | HeartRateRepository | 心率仓库 |
| `provideTimerSessionRepository()` | TimerSessionRepository | 训练仓库 |

### NetworkModule

| Provides | 类型 | 说明 |
|----------|------|------|
| `provideSyncApiClient()` | SyncApiClient | 云同步 API 客户端 |
| `provideSyncRepository()` | SyncRepository | 云同步仓库 |

## 依赖关系图

```
AppModule
  ├─ BleScanner ←── HeartRateViewModel
  └─ BleConnectionManager ←── HeartRateViewModel, BleHeartRateService

DatabaseModule
  ├─ HeartRateDatabase
  │   ├─ HeartRateDao ←── HeartRateRepository ←── HeartRateViewModel
  │   └─ TimerSessionDao ←── TimerSessionRepository ←── TimerCountdownService
  └─ PreferencesManager ←── HeartRateViewModel, Services

NetworkModule
  ├─ SyncApiClient ←── SyncRepository ←── HeartRateViewModel
  └─ LocalBackupManager
```

## 使用约定

- 所有注入通过 `@Inject constructor`
- Singleton 通过 `@Module` + `@Provides` + `@Singleton`
- Context 注入用 `@ApplicationContext`
- ViewModel 用 `@HiltViewModel`

## 关键类

| 类 | 路径 | 说明 |
|----|------|------|
| `AppModule` | `di/AppModule.kt` | BLE 组件注入 |
| `DatabaseModule` | `di/DatabaseModule.kt` | 数据库和仓库注入 |
| `NetworkModule` | `di/NetworkModule.kt` | 网络和同步注入 |
| `PreferencesEntryPoint` | `di/PreferencesEntryPoint.kt` | Preferences 访问入口 |
| `HeartRateMonitorApp` | `HeartRateMonitorApp.kt` | Application + @HiltAndroidApp |

---

## 资源导航

| 类型 | 链接/路径 | 说明 |
|------|----------|------|
| 代码路径 | `app/src/main/java/com/heartratemonitor/di/` | DI 模块全部代码 |
| 相关 Wiki | [数据库](database.md) | Room 数据库配置 |
| 相关 Wiki | [MVVM 数据流](../architecture/mvvm-data-flow.md) | 整体架构 |
