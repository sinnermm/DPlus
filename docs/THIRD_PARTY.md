# 来源、依赖与调研

- Xposed API 82：`https://api.xposed.info/de/robv/android/xposed/api/82/api-82.jar`；仅 compileOnly，不打包入 APK。原 XposedBridge Apache-2.0 NOTICE 保留于 docs/licenses/。
- Gradle Wrapper 8.2.1：从本地 Gradle 官方发行版生成；Apache-2.0。
- AGP 8.2.2：Google 官方构建组件，仅构建依赖。
- JADX 1.5.3：本地分析工具 Apache-2.0；不作为新模块依赖。
- google-java-format 1.22.0：仅用于源码排版；不作为新模块依赖。

构建前检索过以下公开实现：
- `https://github.com/twyora/DouyinEnhancer`，AGPL-3.0；功能有重合但未涵盖全部参考功能。仅用公开信息核对思路，没有复制其代码。
- `https://github.com/1zzzzzlll/douyim`，检索时未发现明确仓库许可证，未复制其代码。
- `https://github.com/LuckyPray/DexKit`，Apache-2.0；本版已有目标版本的实际静态类签名，不额外引入 native 扫描依赖。
- `https://github.com/LSPosed/LSPosed` 和原 XposedBridge：用于接口文档核对，新模块未打包其框架实现。

没有一个经过许可核对的现成项目与用户提供的多个 APK 全功能并集完全一致，所以本版使用平台控件、官方 DownloadManager、Xposed compileOnly API，以独立代码实现可确认的子集。新模块不复用参考 APK 的签名、保护层或授权。

本轮用户指定脚本：`https://scriptcat.org/zh-CN/script-show-page/2534`，下载头版本2026.9.17.17，GPL-3.0-only。脚本内容仅留在work/本地研究，不包含在交付中；仅使用字段和技术思路独立实现。
