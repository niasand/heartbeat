# HeartRateMonitor — PRODUCT.md

## Project Identity

心率监测 Android 应用，通过 BLE 连接蓝牙心率设备，实时显示心率、历史记录、倒计时训练。

## Tech Stack

- **Language**: Kotlin 1.9.24
- **Min SDK**: 29 (Android 10) / Target SDK: 34
- **UI**: Jetpack Compose + Material 3
- **DI**: Dagger Hilt 2.52
- **Database**: Room (SQLite)
- **Architecture**: MVVM + Clean Architecture
- **BLE**: Android Bluetooth Low Energy API
- **Build**: Gradle Kotlin DSL, AGP 8.5.2, Java 17

## Architecture

```
app/src/main/java/com/heartratemonitor/
├── ui/
│   ├── screens/          # Compose screens (HeartRate, History, Settings, Timer)
│   ├── components/       # Reusable Compose components
│   └── theme/            # Color, Theme, Typography
├── viewmodel/            # ViewModel (StateFlow)
├── di/                   # Hilt modules (App, Database, Network)
├── ble/                  # BLE connection, scanning, data models
├── service/              # Foreground services (BLE HeartRate, Timer)
└── data/
    ├── entity/           # Room entities
    ├── dao/              # Room DAOs
    ├── database/         # Room database definition
    ├── repository/       # Data repositories
    ├── pref/             # SharedPreferences wrapper
    └── sync/             # Cloud sync (API client, models)
```

## Key Conventions

- **Data flow**: BLE Device → BleConnectionManager → Repository → Room → ViewModel (StateFlow) → Compose UI
- **DI**: All dependencies through Hilt (`@Inject constructor`, `@Module`)
- **Database**: Room entities in `data/entity/`, DAOs in `data/dao/`, single database class in `data/database/`
- **BLE**: Central role, scan + connect + subscribe to heart rate characteristic
- **Foreground Service**: BLE 和 Timer 各一个 foreground service，保证后台运行
- **Preferences**: `PreferencesManager` 封装 SharedPreferences，通过 Hilt 注入

## Build & Run

```bash
./gradlew assembleDebug                    # 构建 debug APK
./gradlew installDebug                     # 安装到设备
./gradlew test                             # 单元测试
./gradlew lint                             # 代码检查
adb logcat -s HeartRateMonitor            # 查看日志
```

## Rules

- UI 层（screens/）只读 ViewModel 的 StateFlow，不直接操作数据
- BLE 回调在主线程，数据转换在 repository 层
- 数据库操作异步（Room 自动处理， suspend 函数）
- 新增实体必须同时更新 Database 类的 `@Database` entities 列表 + 写 migration
- BLE 设备断连后自动重连，重连逻辑在 BleConnectionManager
- 前台通知必须显示（Android 要求 foreground service 必须有通知）

## Git Commit Format

```
类型: 简要描述
```

类型：`feat:`, `fix:`, `refactor:`, `perf:`, `docs:`, `chore:`, `test:`
