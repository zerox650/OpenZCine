<div align="center">

<a href="https://opencapture.org/openzcine/">
  <img src="docs/assets/icon.png" alt="OpenZCine 应用图标" width="96" height="96">
</a>

# OpenZCine 汉化版

**面向 Nikon Z 的开源现场监看器。**<br>
专业级监看示波器、片段回放、完整相机控制，以及带 LUT 烘焙的 Camera-to-Cloud 导出。免费且开源。

[![汉化版](https://img.shields.io/badge/%E7%AE%80%E4%BD%93%E4%B8%AD%E6%96%87-%E6%B1%89%E5%8C%96%E7%89%88-blue)](https://github.com/zerox650/OpenZCine/releases/latest) [![Docs](https://img.shields.io/badge/docs-opencapture.org-blue)](https://opencapture.org/openzcine/docs/) [![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

[下载汉化版 APK](https://github.com/zerox650/OpenZCine/releases/latest) · [上游项目](https://github.com/erik-sutton95/OpenZCine) · [官方文档](https://opencapture.org/openzcine/docs/)

<br>

<a href="https://opencapture.org/openzcine/">
  <img alt="OpenZCine 实时监看画面，含录制状态、示波器、构图参考线与相机参数" src="docs/assets/readme/hero-monitor.webp" width="820">
</a>

</div>

> [!IMPORTANT]
> **这是 OpenZCine 汉化版，由个人维护的 fork，不是官方版本。**
>
> - 基于上游 [erik-sutton95/OpenZCine](https://github.com/erik-sutton95/OpenZCine) 的 **0.2.5** 版本，仅对 **Android** 端做了简体中文本地化，iOS 端未做改动。
> - 改动范围仅限界面文案与字符串资源（`strings.xml`、`values-zh-rCN`），未改动相机协议、图像处理、录制与导出逻辑。
> - 上游尚未合并这些改动（提交信息格式、iOS/Android 双端同步、横屏截图验证等尚未满足其贡献规范），因此本分支与上游存在差异。
> - 发布用的 APK 使用 debug 签名，仅供自用与侧载测试。它与 Google Play 版签名不同，两者无法互相升级，安装前请先卸载 Play 版或旧的自签版。
> - 上游作者与 OpenCapture 项目与本汉化版没有任何关联。汉化相关问题请在本仓库反馈，不要打扰上游维护者。

<details>
<summary><strong>目录</strong></summary>

- [支持的相机](#支持的相机)
- [功能](#功能)
- [画面预览](#画面预览)
- [安装](#安装)
- [路线图](#路线图)
- [免费、开源、属于你](#免费开源属于你)
- [面向开发者](#面向开发者)
  - [技术栈](#技术栈)
  - [架构](#架构)
  - [从源码构建](#从源码构建)
  - [文档](#文档)
- [参与贡献](#参与贡献)
- [支持项目](#支持项目)
- [致谢](#致谢)
- [许可](#许可)

</details>

## 支持的相机

| 相机 | 实时取景 | 相机控制 | 说明 |
| --- | :---: | :---: | --- |
| **Nikon ZR** | ✅ | ✅ | 主要硬件目标，当前的开发与测试都围绕它进行。 |
| **Nikon Z9** | ✅ | ✅ | |
| **Nikon Z5II** | ✅ | ✅ | |
| **Nikon Z6、Z6II、Z6III、Zf、Z8、Z7、Z7II** | ❔ | ❔ | 尚未在实机上测试。 |

✅ 可用 · ❔ 未测试。

> [!WARNING]
> 相机协议依据公开资料实现，在未测试过的机身上可能并不完整。在你完全信任这条链路之前，请先在相机机身上确认录制能正常开始与停止。

## 功能

- **像调色师一样读画面。** 波形图、RGB 分量图（parade）、直方图与矢量示波器，与你正在判断的画面实时并排显示。
- **在开机拍摄前抓住曝光与对焦。** 支持识别编码格式的伪色彩、斑马纹、参考 RED 设计的红绿灯曝光提示，以及行业标准的峰值对焦，全部直接作用在监看画面上。
- **一次构图，适配多种交付。** 可叠加多种画幅参考线、自定义画框、网格、十字准星、水平仪与变形去挤压，同时不丢失对画面的整体观察。
- **不碰相机也能设置相机。** 在装具上的设备里控制 ISO、快门角度、光圈、白平衡、录制格式、帧率、自动对焦行为与录制启停。
- **保持连接。** 具备容错的 Wi-Fi 发现、配对、已保存相机配置与自动重连，并在主用的 Wi-Fi 流程之外提供 USB-C 有线传输。
- **收工前先审片。** 浏览片段、拖动回放、检查示波器与参考线，并在设备上预览选定的色彩风格。
- **带着既有风格直接交付。** 导出时可套用内置、RED 或自定义的 `.cube` LUT，然后通过系统原生分享或直接发送到 [Frame.io](https://www.frame.io/)。
- **保留相机真实时间码。** 当相机上报时，监看与多机位读数会保留相机头部的帧计数器，以 `HH:MM:SS:FF` 显示；应用不会自行生成帧时钟或帧率。
- **把监看器戴在手腕上。** Apple Watch 与 Wear OS 伴侣应用会同步实时画面及其 LUT、相机状态、时间码，并支持远程录制控制。

iPhone、iPad 与 Android 手机共享同一套监看、辅助工具、相机控制、回放与导出能力，并在长时间监看与录制时自适应管理实时取景的发热。目前的主要硬件目标是 Nikon ZR。USB-C 传输、两个可穿戴伴侣、蓝牙快门集成，以及更广的手机与平板适配，仍在通过真实环境测试持续加固。

## 画面预览

<table>
  <tr>
    <td width="50%" valign="top">
      <a href="https://opencapture.org/openzcine/#commander"><img alt="OpenZCine 相机控制界面，含 ISO、快门、光圈、白平衡、编码与自动对焦" src="docs/assets/readme/camera-controls.webp"></a>
      <br><strong>相机控制。</strong>在装具上的设备里调整 ISO、快门角度、光圈、白平衡、编码与自动对焦。
    </td>
    <td width="50%" valign="top">
      <a href="https://opencapture.org/openzcine/#scopes"><img alt="OpenZCine 在实时画面上显示直方图与 RGB 分量图" src="docs/assets/readme/scopes.webp"></a>
      <br><strong>示波器。</strong>在实时相机画面上叠加直方图与 RGB 分量图，现场辅助工具一并显示。
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <a href="https://opencapture.org/openzcine/#playback"><img alt="OpenZCine 片段回放并带监看辅助工具" src="docs/assets/readme/playback-assists.webp"></a>
      <br><strong>回放。</strong>用与实时取景相同的示波器和辅助工具审看片段。
    </td>
    <td width="50%" valign="top">
      <a href="https://opencapture.org/openzcine/#export"><img alt="OpenZCine 将审看过的片段上传到 Frame.io" src="docs/assets/readme/frameio-upload.webp"></a>
      <br><strong>Camera-to-Cloud。</strong>把审看过的片段连同已烘焙的色彩风格，直接发送到 Frame.io。
    </td>
  </tr>
</table>

## 安装

| 平台 | 方式 | 说明 |
| --- | --- | --- |
| **Android（汉化版）** | [下载本仓库的 APK](https://github.com/zerox650/OpenZCine/releases/latest) | 仅本仓库提供。已汉化，debug 签名，需 Android 10 或更高版本、arm64。 |
| **iPhone 与 iPad（官方英文版）** | [加入 TestFlight](https://testflight.apple.com/join/xu4d6UK8) | 一个通用应用，附带 Apple Watch 伴侣。本汉化版未改动 iOS。 |
| **Android（官方版）** | [Google Play 公开测试](https://play.google.com/store/apps/details?id=com.opencapture.openzcine) | 官方构建，界面为英文。 |
| **Android 无 Play 商店（官方版）** | [侧载 APK](https://github.com/erik-sutton95/OpenZCine/releases/tag/sideload-v0.2.5-4)（**0.2.5 (4)**） | 上游提供的英文版，适合现场监视器等无 Play 商店的设备。 |

> [!NOTE]
> 侧载 APK 与 Google Play 安装包的签名不同，彼此无法互相升级。安装本汉化版之前，请先卸载 Google Play 版或其他来源的旧版本。

配对与实时取景需要附近有一台真实的 Nikon Z 相机。两个平台的指南都在[文档](https://opencapture.org/openzcine/docs/)中。

## 路线图

路线图维护在 [GitHub Discussions](https://github.com/erik-sutton95/OpenZCine/discussions/22)，每个提议的功能都有独立讨论帖。可以浏览
[Ideas 分类](https://github.com/erik-sutton95/OpenZCine/discussions/categories/ideas-feature-requests)投票、补充生产环境背景，或提出下一步要做什么。路线图讨论帖表达的是方向，不承诺具体日期。当前的工程状态可在只读的
[OpenZCine Kaneo 看板](https://kaneo.opencapture.org/public-project/x8bqmvbho1am6f7ganbp72uq)查看，工程阶段的细节保存在 [`docs/ROADMAP.md`](docs/ROADMAP.md)。

## 免费、开源、属于你

没有订阅，没有付费墙，没有广告，也没有遥测。
OpenZCine 以 Apache-2.0 许可发布，并且刻意、透明地全程公开构建，使用 Claude Code 与 Codex 开发，让电影创作者与开发者都能检查、改进并改造这个他们依赖的工具。
工程规范见 [`AGENTS.md`](AGENTS.md)（英文）。

## 面向开发者

### 技术栈

[![Swift](https://img.shields.io/badge/Swift-F05138?style=flat-square&logo=swift&logoColor=white)](https://www.swift.org/) [![SwiftUI](https://img.shields.io/badge/SwiftUI-0D96F6?style=flat-square&logo=swift&logoColor=white)](https://developer.apple.com/xcode/swiftui/) [![Metal](https://img.shields.io/badge/Metal-555555?style=flat-square&logo=apple&logoColor=white)](https://developer.apple.com/metal/) [![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org/) [![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose) [![Vulkan](https://img.shields.io/badge/Vulkan-AC162C?style=flat-square&logo=vulkan&logoColor=white)](https://www.vulkan.org/)

### 架构

一套共享的 Swift 业务与协议核心，外加各平台原生外壳：

| 层 | 路径 | 用途 |
| --- | --- | --- |
| **共享核心** | `Sources/OpenZCineCore/` | PTP-IP 协议、相机状态、Nikon 属性码、设备发现与配对 |
| **iOS 应用** | `ios/` | SwiftUI 外壳、Bonjour 发现、实时取景渲染、相机 I/O |
| **Android 应用** | `Apps/Android/app/` | Jetpack Compose 手机外壳与 Android 平台适配层 |
| **Android 门面** | `Sources/OpenZCineAndroidFacade/` | 面向 Android 的 Swift 会话与 JNI 边界 |
| **Wear OS** | `Apps/Android/wear/`、`Apps/Android/wear-relay/` | 手表界面与经手机中转的 relay |
| **测试** | `Tests/OpenZCineCoreTests/` | Swift 包测试：数据包编解码、属性解析、设备发现、布局 |
| **原型** | `reference/flutter-prototype/` | 归档的 Flutter 参考资料，不参与生产 CI |

共享核心负责协议逻辑并保持可移植（不依赖 SwiftUI、UIKit 或 Android）。平台外壳负责套接字、权限、生命周期、渲染与界面。参见
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) 与[架构决策文档](docs/design/specs/2026-06-20-production-native-architecture-design.md)。

### 从源码构建

工具链统一通过 [`just`](https://github.com/casey/just) 调用：

```bash
just setup                 # 安装元检查工具（macOS / Homebrew）
just                       # 列出全部 recipe
just check                 # 运行仓库级质量检查
just format                # 格式化 Swift 源码
just test                  # 运行 Swift 包测试
just native-check          # 运行 Swift 测试并构建原生 iOS 应用
just android-build         # 构建 Android 应用与分阶段 Swift 运行时
just android-check         # 构建、测试、编译设备测试并对 Android 执行 lint
just android-release-check # 校验已签名的手机 / Wear 发布组合
just bug-relay-check       # 测试独立的匿名问题反馈中转服务
```

iOS 的 Xcode 工程已提交入库：

```bash
open ios/Runner.xcodeproj
```

> [!TIP]
> 应用的大部分功能不接相机也能跑：在 `Runner` scheme 的环境变量里加上 `ZC_DEMO_AUTOSTART=1`
> 即可开启演示用的实时取景会话（见 [`CONTRIBUTING.md`](CONTRIBUTING.md)）。配对与实时取景则需要一台真实的 Nikon Z 相机。

发布流程：TestFlight 构建由 Xcode Cloud 产出，GitHub Actions 作为后备方案（[`docs/testflight-ci.md`](docs/testflight-ci.md)）。Play 测试与侧载 APK 见
[`docs/android-distribution.md`](docs/android-distribution.md)。

**本汉化版的 Android 构建**（在 Windows 上，Gradle 根目录是 `Apps/Android`）：

```bash
cd Apps/Android
ANDROID_KEYSTORE_PATH="C:/Users/<你>/.android/debug.keystore" \
ANDROID_KEYSTORE_PASSWORD=android \
ANDROID_KEY_ALIAS=androiddebugkey \
ANDROID_KEY_PASSWORD=android \
./gradlew :app:assembleRelease --offline
```

### 文档

- **[文档站](https://opencapture.org/openzcine/docs/)**：OpenZCine 手册。
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)：共享 Swift 核心与各平台外壳
- [`docs/connect-presentation.md`](docs/connect-presentation.md)：共享进度、连接方式选择与授权
- [`docs/ROADMAP.md`](docs/ROADMAP.md)：工程阶段与状态
- [`docs/nikon-sdk.md`](docs/nikon-sdk.md)：相机协议所依据的公开资料来源
- [`docs/flows/`](docs/flows/)：用户旅程与界面级设计，以纳入 git 的 Excalidraw 图（`.excalidraw`，可在 [excalidraw.com](https://excalidraw.com) 或编辑器插件中打开）配合 markdown 节点卡片呈现。约定见 [`docs/flows/README.md`](docs/flows/README.md)
- [`docs/design/`](docs/design/)：设计规格与实现方案
- [`AGENTS.md`](AGENTS.md)：面向编码助手的常驻索引

OpenZCine 在公开之前经历过一段较长的私有研发期；公开仓库从一次压缩后的初始提交开始，不携带那段实验性历史。

## 参与贡献

欢迎贡献。若你希望把改动合入上游项目，请以[上游仓库](https://github.com/erik-sutton95/OpenZCine)为准：

- 阅读 [`CONTRIBUTING.md`](CONTRIBUTING.md) 了解开发流程、代码规范，以及如何区分报告缺陷与提出功能请求。
- **报告问题**（iOS 与 Android）是与 OpenPocketCine 共用的反馈表单：它会把你的描述、可选的回复邮箱、仅在你主动填写时才包含的技术细节，以及照片通过 Sentry 私密地发送给 OpenCapture。自动错误报告是另一项需主动开启的功能，默认关闭。
- 想法与提问请发到 **GitHub Discussions**（需要账号）：[Ideas](https://github.com/erik-sutton95/OpenZCine/discussions/categories/ideas-feature-requests) 与 [Q&A](https://github.com/erik-sutton95/OpenZCine/discussions/categories/q-a)。
- 统一的标签有助于分拣工作，见 [`.github/labels.yml`](.github/labels.yml)。
- 请阅读 [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md)。安全问题请见 [`SECURITY.md`](SECURITY.md)。

**本汉化版的贡献**：只接受与中文本地化、文案纠错、翻译缺失相关的改动。相机协议、图像处理与录制逻辑的改动请提交给上游项目。

<a href="https://github.com/erik-sutton95/OpenZCine/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=erik-sutton95/OpenZCine" alt="贡献者">
</a>

## 支持项目

非常感谢每一位使用本项目、提交问题或贡献补丁的人。可选的
[Buy Me a Coffee](https://buymeacoffee.com/eriksutton) 赞助有助于让项目持续运转。如果你更愿意捐给慈善机构，尤其是有助于动物的机构，同样非常欢迎。

> 上述支持渠道属于上游作者。本汉化版不设任何赞助入口。

## 致谢

**R3D NE Monitor** 内置风格由 Wang Yuehua 贡献，经许可随应用发布，并在应用 LUT 选择器中与该风格一同署名。Nikon PTP/MTP 的属性与操作码已对照 libgphoto2 项目的公开协议表进行交叉核对，未包含任何 libgphoto2 源码。两者的细节都在 [`THIRD-PARTY-NOTICES.md`](THIRD-PARTY-NOTICES.md)。

本汉化版的简体中文翻译由 [zerox650](https://github.com/zerox650) 完成，基于上游 0.2.5 版本。

## 许可

[Apache 2.0](LICENSE)。第三方许可列在 [`THIRD-PARTY-NOTICES.md`](THIRD-PARTY-NOTICES.md)。应用的隐私政策位于
[opencapture.org/openzcine/privacy](https://opencapture.org/openzcine/privacy/)。

本项目与 Nikon 无隶属关系。项目不包含、不随附、也不依赖任何厂商 SDK 或 Nikon 专有文档。相机协议依据公开资料实现，见 [`docs/nikon-sdk.md`](docs/nikon-sdk.md)。
"Nikon"、"Nikon Z"、"ZR" 与 "Z Cinema" 是 Nikon Corporation 的商标，此处仅用于标识。

---

<sub>英文原版 README 见[上游仓库](https://github.com/erik-sutton95/OpenZCine#readme)。</sub>
