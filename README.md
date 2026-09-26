# 鸡腿输入法 · Drumstick Input Method

Android TV 输入法项目，包名：`com.jituileet.inputmethod`。

## 1.0.0 TV 输入修复

本修订保持版本号 `1.0.0` 不变，针对 Android TV 遥控器和软键盘输入路径修复：

- 中文拼音输入改为通过 JNI `set_input` 直接更新 Rime composition，避免电视软键盘字母无法进入 Rime 的问题。
- Rime 不可用时，内置 `luna_pinyin` 词库提供可用的本地候选词回退。
- 26 键与 9 键共用同一个中英文状态；中/英键在有拼音预输入时也仍然执行中英文切换。
- 9 键 `?123` 可稳定进入符号/数字页，数字页的 `ABC` 返回 9 键。
- 26 键和 9 键同一行的左右移动支持首尾循环。
- 26 键上下移动到顶部工具栏时按屏幕实际位置匹配，不再把一整行错误地跳到“隐藏输入法”。
- 设置、语言、键盘模式、标点选择等 IME 内面板统一使用空间焦点算法，并排除面板根节点，修复遥控器上下焦点跳回第一个按钮的问题。
- 已更新 Rime 数据安装标记，安装修订版后会重新部署新的内置配置。

## 当前状态

**Rime 已正式接入。** `ChineseEngine` 不再把“候选词数组”作为主实现；运行时优先加载 ABI 对应的 `liblibrime.so`，通过 JNI 调用 Rime session、composition、candidate、selection、ASCII mode 和 deployment API。只有在 native runtime 没有被构建/打包时，开发环境才使用一个极小的 fallback，避免前端工程无法启动。

Rime 核心 API 采用官方 `rime_api.h` 的版本化 API 结构。Rime 官方文档/API 提供 `setup`、`initialize`、session、`simulate_key_sequence`、`get_context`、`select_candidate`、`set_option` 等接口；本项目 JNI 只依赖这些稳定的 C API。

## 功能

- Android 4.4 / API 19 起
- 目标 Android 14 / API 34
- `armeabi-v7a` + `arm64-v8a`
- 中文（简体）/ English
- 跟随系统语言，也可手动切换
- AOSP 风格横向 TV 键盘
- 设置 / 复制 / 剪贴板 / 语音 / 隐藏
- 外观颜色、深色模式、恢复默认外观
- 内置 Rime 词库
- `.dict.yaml` 词库导入、选择、重新部署
- 错误词库 / 空词库提示
- 物理键盘模式
- Shift 中英切换、Caps Lock 英文大写
- 手机局域网输入
- GitHub Actions 自动构建 APK 和 Rime native artifacts

## Rime 架构

```text
Android TV keyboard UI
        │
        ▼
ChineseEngine.java
        │
        ▼
RimeNative.java (JNI)
        │
        ├── librime.so   ← 本项目 JNI bridge
        │
        └── liblibrime.so          ← CI 构建的真实 Rime engine
                    │
                    ├── Boost
                    ├── LevelDB
                    ├── marisa-trie
                    ├── yaml-cpp
                    ├── OpenCC
                    └── glog / 其他 Rime 依赖
```

Rime 官方仓库采用 BSD 3-Clause License；本项目的 CI 默认跟随当前上游 Android CMake 依赖图构建 librime，而不是下载一个闭源预编译引擎。

Android 上通过 JNI 使用 librime 是成熟的 Rime 前端路线；Trime 官方项目就是 Android + JNI + librime 的参考实现。

## CI 构建修复（v0.2.1）

上一版 CI 在 `Build real liblibrime.so for TV ABIs` 阶段失败。失败并不是 Android Gradle 或 APK 工程，而是 Trime 当前的 `cmake/Rime.cmake` 在关闭可选插件后仍然无条件执行 `target_compile_options(rime-lua-objs ...)`；由于 `rime-lua-objs` 没有创建，CMake 直接报 `Cannot specify compile options for target "rime-lua-objs" which is not built by this project.`。

本版在 CI 下载 Trime 源码后，会在配置 CMake 之前自动生成一个最小 Rime 构建：

- 关闭 Lua / Octagram / Predict 可选插件；
- 对可选 object target 使用 `if(TARGET ...)` 保护；
- 明确构建 `rime-static`，再生成鸡腿输入法需要的 `liblibrime.so`；
- 优先使用 GitHub Actions 安装的 CMake 3.22.1，而不是 runner 系统的 CMake；
- 保留 `armeabi-v7a` API 19 和 `arm64-v8a` API 21。

这样可以绕过日志中的真正错误；CMake 的 `< 3.10` 兼容性提示属于 warning，不是导致作业失败的原因。

## GitHub 编译

GitHub Actions 会执行：

1. 安装 JDK 17。
2. 安装 Android SDK 35、CMake 3.22.1、NDK 23.2.8568313。
3. 获取 Android Rime native 构建所需的开源依赖。
4. 构建 `armeabi-v7a` 的 `liblibrime.so`，API 19。
5. 构建 `arm64-v8a` 的 `liblibrime.so`，API 21。
6. 拉取 `rime-prelude` 和 `rime-luna-pinyin` 数据。
7. Gradle 构建鸡腿输入法 APK。
8. 上传 APK 和 Rime native libraries Artifact。

这里有一个 Android 平台的现实限制：**arm64-v8a 在 Android 4.4 上不存在**，因此 arm64 native library 使用 API 21；Android 4.4 设备使用 `armeabi-v7a`。APK 本身仍保持 `minSdk 19`。

Trime 的公开 CI 也采用 Android NDK/CMake 构建 JNI native runtime；社区的 librime Android 构建方案同样采用按 ABI 指定 Android API 的方式。

## 本地编译

### 普通前端开发

安装 Android Studio、Android SDK 35、NDK 23.2.8568313 和 CMake 3.22.1，然后：

```bash
gradle :app:assembleDebug
```

如果没有 `app/src/main/jniLibs/<abi>/liblibrime.so`，前端仍可编译，但运行时会进入 fallback engine。

### 完整 Rime 编译

```bash
bash scripts/build-rime-native.sh
bash scripts/fetch-rime-data.sh
gradle :app:assembleDebug
```

`build-rime-native.sh` 会在构建目录中拉取 Android Rime 依赖并生成：

```text
app/src/main/jniLibs/armeabi-v7a/liblibrime.so
app/src/main/jniLibs/arm64-v8a/liblibrime.so
```

最终 APK 同时包含：

```text
lib/armeabi-v7a/liblibrime.so
lib/armeabi-v7a/liblibrime.so
lib/arm64-v8a/liblibrime.so
lib/arm64-v8a/liblibrime.so
```

## Rime 数据目录

首次运行时，APK assets 中的 `rime/` 会复制到：

```text
/data/data/com.jituileet.inputmethod/files/rime/
```

用户数据放在：

```text
/data/data/com.jituileet.inputmethod/files/rime-user/
```

上传的 `.dict.yaml` 会保存到应用的 `files/dicts/`，同时生成 Rime schema patch 并执行 deployment。

## 许可证

鸡腿输入法前端代码采用 MIT License；Rime 及其数据/依赖保持各自许可证。详细列表见 [`THIRD_PARTY_LICENSES.md`](THIRD_PARTY_LICENSES.md)。

Rime 官方项目明确列出的核心依赖包括 Boost、glog、LevelDB、marisa-trie、OpenCC 和 yaml-cpp 等。

## Candidate learning

The supplied luna_pinyin dictionary is bundled in `app/src/main/assets/rime/luna_pinyin.dict.yaml`. Candidate selections are recorded locally; candidates selected more often are promoted in subsequent candidate lists, while equal-frequency candidates retain Rime order. Usage can be cleared from Dictionary settings.

## Included functionality
