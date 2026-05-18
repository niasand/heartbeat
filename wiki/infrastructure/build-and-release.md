---
title: "构建与发布流程"
category: "infrastructure"
tags: ["gradle", "build", "release", "signing", "apk"]
created: "2026-05-18"
updated: "2026-05-18"
---

Gradle 构建系统、签名配置和版本发布流程。

## 构建配置

| 配置 | 值 |
|------|-----|
| AGP | 8.5.2 |
| Kotlin | 1.9.24 |
| Java | 17 |
| Min SDK | 29 (Android 10) |
| Target SDK | 34 |
| 构建工具 | Gradle Kotlin DSL |

## 常用命令

```bash
./gradlew assembleDebug          # 构建 debug APK
./gradlew installDebug           # 安装到设备
./gradlew assembleRelease        # 构建 release APK（需签名）
./gradlew test                   # 单元测试
./gradlew lint                   # 代码检查
```

## 签名配置

- 签名文件：`release.keystore`（项目根目录）
- Release APK 使用该 keystore 签名

## 版本管理

版本号格式：`1.0.N`（N 为递增序号）

每次发布更新 `release.md`：
- 版本号
- 日期
- 更新内容（变更描述）

## 版本历史摘要

| 版本范围 | 日期 | 关键变更 |
|---------|------|---------|
| 1.0.0-1.0.1 | 2025-02-12 | 初始发布，基础功能 |
| 1.0.2-1.0.8 | 2025-02-12 | Bug 修复，主题色，长按停止 |
| 1.1.0 | 2025-02-13 | 自动重连/连接，历史图表优化 |
| 1.2.0 | 2025-02-13 | 历史图表自动显示最新 60min |
| 1.2.1-1.2.2 | 2025-02-13 | 自动重连修复和优化 |
| 1.2.3-1.2.4 | 2025-02-13 | 心率显示 0 修复，设备兼容性 |

## 日志查看

```bash
adb logcat -s HeartRateMonitor     # 应用日志
adb logcat -s BleConnectionManager # BLE 日志
```

---

## 资源导航

| 类型 | 链接/路径 | 说明 |
|------|----------|------|
| 代码路径 | `build.gradle.kts` | 根构建配置 |
| 代码路径 | `app/build.gradle.kts` | App 模块配置 |
| 文件 | `release.md` | 完整版本历史 |
| 文件 | `release.keystore` | 签名文件 |
