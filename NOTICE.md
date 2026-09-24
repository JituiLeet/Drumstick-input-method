# 鸡腿输入法 NOTICE

本项目为 Android TV 输入法前端，包名 `com.jituileet.inputmethod`。

完整 Rime 引擎构建由 GitHub Actions 在构建时从公开上游仓库获取。项目不会把 Trime 的 GPL JNI 前端代码链接进最终 `liblibrime.so`；Trime 的 CMake 依赖图仅作为 Android 构建依赖编排方式。最终 Rime 核心来自 BSD 3-Clause 许可的 `rime/librime`，同时会静态链接其所需的开源依赖。

请在重新分发 APK 时一并保留 `THIRD_PARTY_LICENSES.md` 和上游许可证/NOTICE。
