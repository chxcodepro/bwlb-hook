# 保卫萝卜合集 · bwlb-hook

[![Build & Release APK](https://github.com/chxcodepro/bwlb-hook/actions/workflows/build.yml/badge.svg)](https://github.com/chxcodepro/bwlb-hook/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/chxcodepro/bwlb-hook)](https://github.com/chxcodepro/bwlb-hook/releases/latest)
![Android](https://img.shields.io/badge/Android-11%2B-3DDC84)
![ABI](https://img.shields.io/badge/ABI-arm64--v8a-245C49)

将保卫萝卜 1–4 的 LSPosed 处理逻辑整合为一个 APK，通过统一界面和悬浮窗控制各代游戏功能。

**[下载最新 APK](https://github.com/chxcodepro/bwlb-hook/releases/latest)** · [更新日志](CHANGELOG.md) · [反馈问题](https://github.com/chxcodepro/bwlb-hook/issues)

## 功能

- 一个 APK 管理四代游戏，各代设置独立保存。
- 点击“运行”显示悬浮窗，支持拖动、展开、收起及停止。
- 只处理当前选中的游戏；停止或配置失联后关闭处理功能。
- 悬浮窗不抢占输入焦点，窗口之外的触摸交给游戏。

| 游戏 | 适配版本 / versionCode | 功能 |
| --- | --- | --- |
| 保卫萝卜 1 | 2.0.18 / 74992 | 对局金币、跳过广告 |
| 保卫萝卜 2 | 5.2.6 / 821 | 对局金币、钻石补充、道具补充、商城与萝卜解锁、关卡资源解锁 |
| 保卫萝卜 3 | 6.3.1 / 9245 | 对局金币、钻石锁定、商城金币、点券锁定、跳过广告 |
| 保卫萝卜 4 | 1.4.0 / 36773、37304 | 对局金币、金币与钻石锁定、解除限购、无限体力 |

## 安装与使用

需要 Android 11 或更高版本、ARM64 设备，以及已安装并正常工作的 LSPosed 环境。游戏需单独安装。

1. 从 [Releases](https://github.com/chxcodepro/bwlb-hook/releases/latest) 下载并安装 `保卫萝卜合集.apk`。
2. 在 LSPosed 中启用合集，勾选对应游戏作用域；关闭原有单代模块，避免重复 Hook。
3. 重新启动游戏，打开合集并选择游戏。
4. 点击“运行”，首次使用时允许显示悬浮窗。
5. 点击圆钮展开面板；拖动圆钮或标题移动，点击“收起”恢复圆钮。
6. 在面板中切换功能，“打开游戏”进入当前游戏，“停止”关闭悬浮窗及处理功能。

默认暂停；运行时默认开启对局金币及该代的跳过广告，其余功能按需开启。2 代关卡与资源解锁通过按钮手动触发。已经补充或解锁的存档内容不会因停止而回退；可恢复的函数补丁在下一次游戏渲染时解除。

### 支持的游戏包名

| 游戏 | 包名 |
| --- | --- |
| 1 | `com.carrot.carrotfantasy` |
| 2 | `com.carrot.iceworld` |
| 3 | `com.feiyu.carrot3`、`com.feiyu.carrot3.nearme.gamecenter` |
| 4 | `com.feiyu.luobo4`、`com.feiyu.luobo4.nearme.gamecenter` |

同名游戏的其他渠道或版本可能使用不同接口；当前适配范围以表中构建为准。

## 本地构建

支持 Windows、Linux 和 macOS，无需 Gradle。

| 工具 | 版本 |
| --- | --- |
| Python | 3.10+ |
| JDK | 21 |
| Android SDK Platform | 34 |
| Android SDK Build Tools | 35.0.0，兼容 34.0.0 |
| Zig | 0.13.0 |

```sh
git clone https://github.com/chxcodepro/bwlb-hook.git
cd bwlb-hook
python -m pip install ziglang==0.13.0
python build_apk.py
```

配置 `ANDROID_HOME` 和 `JAVA_HOME` 指向已安装的 SDK、JDK。可选 `CARROT_ZIG` 指定 Zig 可执行文件；未指定时从 PATH 或 Python `ziglang` 包查找。

输出文件：

```text
dist/保卫萝卜合集.apk
dist/保卫萝卜合集.apk.sha256
```

脚本会编译 Java 与四套原生库，校验 APK 签名、ZIP 对齐及 ARM64 架构，并生成 SHA-256 校验文件。`app/libs/xposed-api-82.jar` 仅供编译使用，不打包进 APK。

### 签名配置

首次本地构建自动生成 `.signing/suite.keystore`，后续构建复用。保留该文件，以便使用同一证书覆盖更新。自行构建的签名与官方 Release 不同时，不能直接覆盖安装。

| 环境变量 | 用途 |
| --- | --- |
| `ANDROID_KEYSTORE` | 指定已有 keystore 的路径 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 密码，本地默认 `android` |
| `ANDROID_KEY_ALIAS` | 密钥别名，默认 `suite` |
| `ANDROID_KEY_PASSWORD` | 密钥密码，本地默认 `android` |
| `ANDROID_REQUIRE_RELEASE_KEY` | 设置为 `true` 时强制使用已有签名及显式密码 |

## GitHub Actions 与发布

| 触发方式 | 结果 |
| --- | --- |
| 推送到 `main` | 构建签名 APK，保存 Actions artifact |
| Pull Request | 使用测试签名验证构建，保存 artifact |
| Actions → Run workflow | 构建当前选择的分支或标签；标签会发布 Release |
| 推送 `v*` 标签 | 校验版本，构建并将 APK、SHA-256 文件发布到 Release |

正式构建使用仓库 **Settings → Secrets and variables → Actions** 中的以下 Secrets：

| Secret | 内容 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | keystore 文件的 Base64 编码 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 密码 |
| `ANDROID_KEY_ALIAS` | 密钥别名 |
| `ANDROID_KEY_PASSWORD` | 密钥密码 |

Release 使用同一份签名密钥；密钥不会提交进仓库或上传为 artifact。Fork 时需自行配置 Secrets。

发布新版本：同步修改 `VERSION`、Manifest 中的 `versionName` / `versionCode` 和 `CHANGELOG.md`，提交后推送对应标签：

```sh
git push origin main
git tag -a v1.0.1 -m "Release v1.0.1"
git push origin v1.0.1
```

标签必须与 `VERSION` 和 Manifest 版本一致，且更新日志须包含该版本。发布失败时可以重新运行对应 Actions workflow。

## 项目结构

```text
app/
├── AndroidManifest.xml
├── assets/xposed_init       # LSPosed 入口
├── libs/                    # 编译专用 Xposed API
├── native/game1–game4/      # 各代原生处理逻辑
├── res/                     # 图标及作用域
└── src/                     # 统一界面、通信桥及 Hook
ci/release_notes.py          # 版本校验及发布说明
.github/workflows/build.yml # 自动构建与发布
build_apk.py                # 跨平台构建脚本
```

## 问题反馈

提交 [Issue](https://github.com/chxcodepro/bwlb-hook/issues) 时，请附上 Android / LSPosed 版本、游戏包名与版本、复现步骤及相关模块日志。
