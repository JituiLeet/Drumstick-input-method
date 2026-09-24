# Third-party components

鸡腿输入法本身的前端代码采用项目根目录中的 MIT License。构建并运行完整 Rime 引擎时，还会包含下列上游组件；这些组件保持各自的许可证。

- **RIME / librime** — BSD 3-Clause License.
- **rime-prelude** — GNU LGPL v3.
- **rime-luna-pinyin** — GNU LGPL v3.
- **OpenCC** — Apache License 2.0.
- **yaml-cpp** — MIT License.
- **LevelDB** — BSD 3-Clause License.
- **marisa-trie** — BSD-style license.
- **google-glog** — BSD 3-Clause License.
- **Boost** — Boost Software License.
- **Snappy** — BSD License.

GitHub Actions 从上游仓库获取 Rime native build dependencies 和 Rime data。发布二进制版本时，应保留这些组件的版权、许可证及相应 NOTICE 文件。

Rime 核心的许可证文本可见上游 `librime/LICENSE`；Rime 方案的数据仓库也有各自的 LICENSE。CI 会把 `rime-prelude` 和 `rime-luna-pinyin` 的 LICENSE 文件复制到 APK assets 的 `rime/licenses/` 目录（若上游仓库提供该文件）。
