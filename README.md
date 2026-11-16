# Cloudy

一个只做网易云音乐的 Android 播放器。界面沿用 [Olbyine](https://github.com/Huliang2597/olbyine-music) 的 Compose 设计语言，液态玻璃（AGSL）原样保留，导航收成四个入口：**首页 / 搜索 / 排行榜 / 我的**，本地音乐仍在。

Android 10+ · arm64-v8a / armeabi-v7a · Kotlin + Jetpack Compose · AGPL-3.0

## 下载

| 渠道 | 地址 |
| --- | --- |
| GitHub Releases | [最新版本](https://github.com/hjphh11/cloudy-music/releases/latest) |
| 加速下载页 | https://cn-nb1.rains3.com/qingwu/cloudy/index.html |

- 包名 `com.olbyine.music`，版本 1.0.3（build 203）
- 签名固定，后续版本可以直接覆盖安装，数据与登录状态不会丢
- 应用内「设置 → 更新 → 检查更新」读的是上面那份下载页同源的版本清单，不依赖 GitHub 可达性

## 功能

**网易云音乐**

- 登录（手机号 / 二维码）、我的音乐、每日推荐、私人 FM、排行榜、歌单广场
- 搜索：热搜双列榜、输入联想、关键词高亮，结果分类到单曲 / 歌手 / 专辑 / 歌单 / MV / 播客
- 歌单、专辑、艺人页，评论、MV、播客
- 私信与一起听（基于网易云信 NIM）
- 音质 128k / 192k / 320k / FLAC / Hi-Res，可下载到本地

**本地音乐**

- 通过系统文件选择器扫描文件夹，按文件夹、专辑、歌单、收藏浏览
- 读取内嵌作曲人标签（ID3 多个 TCOM 帧、FLAC Vorbis Comment），同一位作曲人自动合并
- 歌曲信息编辑，从网易云补全封面、简介与发行日期
- 多选、批量收藏、批量加入歌单、批量补全信息
- 播放记忆：队列、歌单与进度持久化，重启后回到当初播放它的歌单

**播放与歌词**

- 播放页黑胶模式：封面转成唱盘，约 6 秒一圈，暂停停在原地，换歌归零
- 逐字歌词（AMLL）、翻译与音译、扫光前沿柔光
- 桌面歌词、通知栏歌词、蓝牙歌词、车机歌词输出
- 均衡器、左右平衡、单声道、淡入淡出、交叉淡变
- USB 音频独占输出（原生层 ioctl）、可变位深与抖动

**界面**

- 液态玻璃：AGSL `RuntimeShader` 做透镜折射与高光（Android 13+，12 降级为模糊与半透明）
- 四种界面风格、五套图标配色（随主题切换桌面图标）
- 手机 / 平板（侧栏）两套布局

## 与上游 Olbyine 的差异

| 项目 | 上游 Olbyine | 本仓库 Cloudy |
| --- | --- | --- |
| 音源 | 网易云、洛雪、SoundCloud、Navidrome、WebDAV | 只保留网易云 |
| 导航 | 侧栏多入口 | 底部四入口：首页 / 搜索 / 排行榜 / 我的 |
| 设置页 | 纯色分组卡片 | 大标题 + 分组色条，开液态玻璃后卡片是真 AGSL 玻璃 |
| 品牌 | Olbyine | Cloudy + 全新自适应图标 |
| 首页 | 混合信息流 | 台历推荐大卡、私人 FM、播客横滑、排行榜预览 |
| 搜索页 | 单一结果列表 | 搜前 / 联想 / 结果三段式 |
| 播放页封面 | 方形封面 + 3D 倾斜 | 黑胶唱盘旋转（可关） |
| 更新 | 读上游 GitHub Releases | 读自己的版本清单，跳过版本不再打扰 |

## 用到的技术

| 层 | 用了什么 |
| --- | --- |
| 语言 / 构建 | Kotlin 2.0.20、Gradle 8.10.2、AGP 8.6.1、JDK 17、minSdk 29 / targetSdk 36 |
| 界面 | Jetpack Compose（BOM 2024.10.01）、Material 3、material-icons-extended、Compose Animation |
| 状态 | ViewModel + StateFlow + kotlinx-coroutines 1.9.0，设置项用 SharedPreferences 持久化 |
| 液态玻璃 | Android **AGSL**（`RuntimeShader` + `RenderEffect.createRuntimeShaderEffect`）：透镜 SDF、色散、边缘高光；API 33+ 全效果，31+ 部分，以下降级 |
| 播放内核 | AndroidX **Media3** 1.10.1（ExoPlayer、MediaSession、Extractor、HLS） |
| 音源协议 | 网易云 **weapi / eapi** 加密接口（`os=android`）：登录、推荐、搜索、播放地址、评论全走这套 |
| 本地解码 | 自带 `:alac-decoder` 模块解析 ALAC，其余由 Media3 extractor 处理 |
| USB 音频 | CMake 3.22.1 + **NDK 26.3**（`app/src/main/cpp`）编译原生层，直接对 USB 设备做 ioctl 与 UAC 输出 |
| 网络 | OkHttp 4.12.0 |
| 歌词 | 自研 AMLL 逐字歌词渲染（`ui/AmllLyricsView.kt`），支持桌面 / 通知 / 蓝牙 / 车机多路输出 |
| 图形细节 | Compose `graphicsLayer`、`drawBehind`、`BlendMode.Plus` 做柔光，`withFrameNanos` 驱动唱盘旋转 |
| 其他库 | ZXing 3.5.3（二维码登录）、网易云信 NIM 10.9.11（私信 / 一起听）、Guava 33.3.1、DocumentFile |
| 更新分发 | 对象存储上的静态 `version.json` + APK 直链，客户端比对 `versionCode` |

## 从源码构建

需要 JDK 17、Android SDK 36、NDK 26.3.11579264。

```bash
# 调试包
./gradlew :app:assembleDebug

# 发布包（未提供 keystore.properties 时退回 debug 签名，仍可直接安装）
./gradlew :app:assembleRelease
```

产物在 `app/build/outputs/apk/`。

发版时改三处：`app/build.gradle` 的 `versionCode` / `versionName`，以及下载页目录下的 `version.json`。

## 许可

本项目遵循 **AGPL-3.0**，与上游一致：分发二进制时需一并提供源码。完整条款见 [LICENSE](LICENSE)，第三方组件声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 致谢

- [Olbyine](https://github.com/Huliang2597/olbyine-music)：本项目的界面骨架、播放内核与液态玻璃实现都来自这里
- [AndroidX Media3](https://github.com/androidx/media)、[Jetpack Compose](https://developer.android.com/compose)
- [AMLL](https://github.com/amll-dev)：逐字歌词的排版思路
- 网易云音乐接口的加密细节来自社区长期公开的整理；本项目不提供、不托管任何音频内容
