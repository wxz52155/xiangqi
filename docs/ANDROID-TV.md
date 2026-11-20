# 南坡象棋 · Android TV / 平板原生版

`android/` 是为电视从头编写的原生应用（Kotlin + Jetpack Compose for TV），不是网页套壳。
规则、训练题库、在线 AI 接口来自本仓库的网页版，界面与交互按 Android TV 的习惯重新设计。

## 功能

| 模块 | 内容 |
| --- | --- |
| 首页 | 沉浸式大图 + 卡片行：继续对局、快速开局（入门 / 进阶 / 挑战 / 双人）、训练营、最近棋谱 |
| 人机对弈 | 执红 / 执黑、三档难度；悔棋、提示（箭头标出着法）、换边、新局、认输；每步自动保存，可继续 |
| 双人同屏 | 两人轮流用同一个遥控器走子 |
| 训练营 | 一步杀法 861 题、残棋定式 737 节、开局 209 套；按难度 / 未完成 / 收藏筛选，棋盘缩略图卡片，进度与收藏记在本机；残局与开局对方自动应手，支持“演示”自动播放主线 |
| 我的棋谱 | 战绩统计、棋谱卡片；回放时左右键逐步，OK 自动播放，可从任意一步接着下 |
| 设置 | 默认执子 / 难度、在线 AI 开关、悔棋、可走位置提示、音效、清除数据 |

AI：联网时用在线 AI（与网页版同一服务，象棋为 Pikafish）；断网、超时或关闭在线 AI 时自动用本机 AI（Kotlin 实现的 alpha-beta 搜索引擎），离线也能下。

## 遥控器

| 按键 | 作用 |
| --- | --- |
| 方向键 | 在卡片 / 按钮间移动；棋盘上移动金色光标，走到边缘再按一次即离开棋盘 |
| OK | 确认；棋盘上先选子再选落点 |
| 返回 | 取消选子 → 返回上一页；首页连按两次退出 |
| 左键（页面最左侧） | 展开左侧导航抽屉 |

平板上可以直接点：点棋子、再点落点；所有按钮、卡片都支持触屏。

## 发布与在线更新

- 推送到 `main` → GitHub Actions（`.github/workflows/android-tv.yml`）跑规则单元测试、构建签名 APK，发布到本仓库 Releases（版本号 `2.0.<运行编号>`，附更新记录与 `update.json`）。
- 应用启动后自动读取 `update.json`（经 `https://gh-proxy.org/` 加速），发现更高的 versionCode 就弹出“发现新版本”，显示更新内容，可一键下载（显示进度、校验 SHA-256）并调起系统安装。设置页显示当前版本并可手动检查。Android 8 以上首次安装需在系统里允许“南坡象棋”安装应用。
- 签名证书保存在私有源码仓库 `android/keystore/`，所有版本签名一致可覆盖安装；也可用 Secrets `ANDROID_KEYSTORE_BASE64` 等 4 项覆盖。
- 手动运行 “Android TV dev preview”：编译 + 单测 + 用 Paparazzi 渲染各界面截图，放在 `dev-preview` 预发布里，方便不装机检查界面。

## 本地构建

```bash
pnpm install
pnpm export:tv-content          # 把训练题库导出到 android/app/src/main/assets/content/
cd android
./gradlew :app:assembleRelease  # 需要 JDK 17 与 Android SDK 35
```

题库里出现新汉字时可运行 `python3 tools/subset-tv-font.py` 重新裁剪内置衬线字体（缺字会自动回落系统字体，不重新裁剪也能用）。

## 代码结构

```text
android/app/                         启动 Activity、图标、电视 banner
android/qilian/src/main/java/com/qilian/tv/
  xiangqi/Xiangqi.kt                 象棋规则核（移植自 packages/xiangqi，perft 校验）
  xiangqi/Engine.kt                  本地象棋 AI
  data/                              题库加载、本机存储、在线 AI 客户端
  game/                              对局与训练流程、音效
  ui/                                主题、组件、棋盘绘制、各页面
```

## 兼容性

Android 5.0（API 21）及以上的电视、盒子、投影仪与平板（横屏）；同时出现在 Android TV 桌面和普通安卓桌面。
