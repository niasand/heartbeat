[AI-REVIEW] Large commit detected: 460 lines added. Consider reviewing for AI Psychosis.
[AI-REVIEW] Large commit detected: 462 lines added. Consider reviewing for AI Psychosis.
[AI-REVIEW] Large commit detected: 223 lines added. Consider reviewing for AI Psychosis.

## [2026-09-25] setup-android v3 CI 全挂：Google 下架 legacy tools 包

- **现象**：push 到 main 后 CI 在 `Setup Android SDK` 步骤必挂，报 `Warning: Failed to find package 'tools'` → `sdkmanager exit code 1`；三个 Android 仓库同一天集体复发。
- **根因**：`android-actions/setup-android@v3` 内部会安装已被 Google 从 SDK 仓库移除的 legacy `tools` 包；runner 镜像更新到 cmdline-tools 16.0 后该包彻底不存在。
- **修复方案**：统一升级 `android-actions/setup-android@v3` → `@v4`（v4.0.4，2026-09-17 发布）；PhotoSearch 显式 `sdkmanager "tools"` 一并移除。
- **涉及文件**：`.github/workflows/build-release.yml` / `.github/workflows/android-ci.yml` / `.github/workflows/release.yml`
- **验证证据**：commit `Bump setup-android to v4 to fix missing tools package` 后 CI 通过（见 Actions run）。
- **教训**：第三方 action 钉大版本（@v3）不等于稳定；SDK 供应商下架包会让老 action 整体挂掉。Android CI 至少每季度空跑一次 main，挂了先看 action 上游 release 而不是先怀疑自己的代码。
