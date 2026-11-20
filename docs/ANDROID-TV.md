# 棋练 · Android TV 版

电视版和网页版共用同一套代码与全部功能（人机对弈、双人同屏、一步杀法、残棋定式、开局、自定义残局、棋谱回放、五子棋 / 斗兽棋 / 跳棋 / 四子棋、设置），只在电视上切换为遥控器操作和大屏布局。

## 下载安装

- 每次推送到 `main`，GitHub Actions（`.github/workflows/android-tv.yml`）都会自动构建签名 APK，并发布到 **Releases**，发布说明里附带本次的更新记录。
- 在 Releases 页面下载 `QilianTV-v1.0.N.apk`，用 U 盘、局域网传输或「当贝助手」等工具装到电视 / 盒子上。
- 所有版本使用同一签名，新版本可直接覆盖安装，棋谱与训练进度保留。
- 也可以在 Actions 页面手动运行 “Android TV APK”，取消勾选 “发布到 Releases” 只构建不发布（APK 在运行记录的 Artifacts 里）。

## 遥控器操作

| 按键 | 作用 |
| --- | --- |
| 方向键 | 在按钮、卡片、棋盘格之间移动焦点；棋盘上走到边缘再按一次即可移到旁边的操作区 |
| OK / 确认 | 点击；在棋盘上先选子、再选落点 |
| 返回 | 关闭弹窗 → 退出输入框 → 返回上一页 → 回首页；首页连按两次退出 |
| 菜单 | 打开设置 |

## 为电视做的改动

| 原来的做法 | 电视上的做法 |
| --- | --- |
| 鼠标 / 触摸点击 | 全局方向键空间导航（`apps/web/src/tv/spatialNav.ts`），醒目的金色焦点框、棋盘圆环光标；AI 思考时棋盘暂不可点，结束后焦点自动回到原来的格子 |
| 摆残局：拖拽棋子 | OK 选中棋子 → 再选空位移动；对选中的棋子再按 OK 移除；右侧面板显示当前选择并提供“移除”按钮 |
| 复制 FEN / 分享链接 | 弹出二维码，用手机扫码获取 |
| 外部链接（GitHub、引擎文档等） | 弹出二维码；设备有浏览器时也可以直接在电视浏览器里打开 |
| `window.confirm` 确认框 | 遥控器友好的确认弹窗，默认焦点在“取消” |
| 输入框 | 按 OK 才唤起电视软键盘，左右键到达两端时离开输入框 |
| 震动反馈 | 电视没有震动马达，隐藏该开关（音效保留） |
| PWA 安装 / 分享 / Service Worker / 统计 | 不需要：网页包和本地象棋引擎打包在 APK 里，天然离线可用；电视版不接入任何统计 |
| 手机竖屏 / 桌面布局 | 固定 1280×720 逻辑分辨率按电视整屏缩放，对局和训练页一屏放下棋盘与操作区，去掉电视 GPU 吃力的毛玻璃和噪点层 |

在线 AI 请求由原生层代发（`NativeBridge.java`），不受 WebView 跨域限制，并支持流式（SSE）思考进度；联网失败时象棋自动切换到内置的 ElephantEye 本地引擎。

## 本地构建

```bash
pnpm install
pnpm build:tv            # 构建 TV 网页包并复制到 android/app/src/main/assets/web
cd android
./gradlew assembleRelease # 需要 JDK 17 与 Android SDK（compileSdk 35）
```

APK 输出在 `android/app/build/outputs/apk/release/`。

浏览器里调试电视界面：`pnpm dev` 后访问 `http://127.0.0.1:5173/?tv=1`，键盘方向键 / 回车 / Esc 模拟遥控器。

## 目录

```text
android/                     原生壳（Java，无第三方依赖）
  app/src/main/java/com/qilian/tv/
    MainActivity.java        全屏 WebView、遥控器按键、返回键、退出确认、旧 WebView 提示
    AssetServer.java         把 assets/web 以 https://appassets.androidplatform.net 提供给 WebView
    NativeBridge.java        window.QilianNative：原生 HTTP、软键盘、外部浏览器
  keystore/                  本地构建放签名证书的位置（不提交）
  version.properties         版本号前缀（发布版本 = 前缀.运行编号）
apps/web/src/tv/             电视适配：环境检测、空间导航、原生请求、二维码 / 确认弹窗
apps/web/src/styles/tv.css   电视样式（html.tv）
tools/build-tv.mjs           构建 TV 网页包
```

## 签名证书（一次性配置）

签名证书**不放进代码库**，由 GitHub Actions 从仓库 Secrets 读取。在仓库 Settings → Secrets and variables → Actions → New repository secret 添加：

| Secret | 内容 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | 证书文件的 base64（`base64 -w0 qilian-tv.jks`） |
| `ANDROID_KEYSTORE_PASSWORD` | 证书密码 |
| `ANDROID_KEY_ALIAS` | 密钥别名（`qilian-tv`） |
| `ANDROID_KEY_PASSWORD` | 密钥密码（与证书密码相同） |

配置后每个版本签名一致，可以直接覆盖安装。没有配置时工作流会用临时证书兜底继续发布，但发布说明里会提示“需先卸载旧版本”。

本地构建时，把 `qilian-tv.jks` 和 `keystore.properties` 放进 `android/keystore/`（已被 `.gitignore` 排除）。**证书丢失后就无法再发布可覆盖安装的更新，请自行备份。**

## 兼容性

- Android 5.0（API 21）及以上的电视、盒子、投影仪；同时出现在 Android TV 桌面（Leanback）和普通安卓桌面。
- 需要系统 WebView（Chrome 内核）80 及以上；更旧的设备启动时会提示升级「Android System WebView」。
