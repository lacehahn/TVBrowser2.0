# TVBrowser20

在现在这个短视频时代，慢慢坐在电视前看一场球赛越来越觉得奢侈。

`TVBrowser20` 是一个面向电视/大屏使用场景的 Android WebView 小工具：把常见直播/视频网页的播放器区域“提纯”为全屏播放，尽可能隐藏网页其余干扰元素；并针对部分站点提供更强的页面适配（例如频道列表桥接到 Android 侧做遥控器/列表选台）。

## 主要特性

- **按站点注入 JS 的适配框架**：通过 `jsKey` 选择注入策略（默认 / CCTV / yibababa / none）。
- **播放器全屏化**：将 `video` 或播放器容器固定到 `100vw x 100vh`，黑底，隐藏页面其他 DOM。
- **站点增强适配**
  - **CCTV**：隐藏头/尾/推荐/评论等元素，等待页面延迟加载后再做全屏。
  - **yibababa**：读取频道列表并通过 `Android.onChannelList(...)` 回传到 Android；支持 `TVB_select(idx)` 在网页侧切台并自动全屏。
- **Android TV 友好**：Manifest 同时包含 `LAUNCHER` 与 `LEANBACK_LAUNCHER`，支持横屏。

## 快速开始

### 环境要求

- Android Studio（推荐较新版本）
- Android SDK：`compileSdk = 35` / `targetSdk = 35`
- 最低版本：`minSdk = 21`
- JDK：项目使用 `Java 11`（`jvmTarget = 11`）

### 运行

1. 用 Android Studio 打开本工程根目录。
2. 等待 Gradle Sync 完成。
3. 选择 `app` 运行到模拟器或实体设备（电视盒子/TV 更合适）。

## 项目结构（与功能相关的关键文件）

- `app/src/main/java/com/example/tvbrowser20/web/JsInjector.kt`
  - 注入脚本入口：`JsInjector.getJs(jsKey: String)`
  - 默认注入：优先查找 `video` / 常见播放器容器 / iframe，并做全屏与隐藏其它元素
  - 站点注入：`CCTV_JS`、`YIBA_JS`
- `app/src/main/java/com/example/tvbrowser20/data/Source.kt`
  - `Source` 的 `jsKey` 字段与 `JsKey` 常量定义
- `app/src/main/java/com/example/tvbrowser20/data/SourceConfig.kt`
  - 所有源与频道的集中配置：`SOURCES`
  - `allowedDomains`：用于 URL 白名单（并影响 WebView 允许加载的域名集合）

## 如何新增一个站点（JS 规则）

1. 在 `app/src/main/java/com/example/tvbrowser20/web/JsInjector.kt` 增加一个新的注入脚本常量（例如 `private val MY_SITE_JS = """..."""`）。
2. 在 `app/src/main/java/com/example/tvbrowser20/data/Source.kt` 的 `object JsKey` 增加常量（例如 `const val MY_SITE = "mysite"`）。
3. 在 `JsInjector.getJs()` 的 `when (jsKey)` 中把 `JsKey.MY_SITE -> MY_SITE_JS` 映射起来。
4. 在 `SourceConfig` 里对应的 `Source(...)` 设置 `jsKey = JsKey.MY_SITE`，并补齐 `allowedDomains`。

## 免责声明

- 本项目仅做 **网页端展示适配/交互增强** 的技术研究与个人学习用途。
- 直播/视频内容与版权归其权利方所有；请在你所在地法律与相关站点条款允许的前提下使用。
- 对第三方站点的 DOM 结构依赖可能随站点更新而失效，属于正常现象。

## License

如需开源发布，建议你在此处补充具体 License（例如 MIT / Apache-2.0）。当前仓库未内置 License 文件。

