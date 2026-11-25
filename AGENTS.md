# 极简桌面 (Minimalist Launcher · v2.6.0) — AI Agent 开发与构建规范

## 1. 项目概览
- **仓库名称**: `极简桌面`
- **当前版本**: `v2.6.0` (`versionCode: 260`)
- **技术栈**: 纯原生 Android Java (JDK 17 编译目标 `--release 8`，`minSdkVersion 26`，`targetSdkVersion 34`)，**无 Gradle 依赖**，直接通过 Android Build Tools (`aapt` + `javac` + `d8` + `zipalign` + `apksigner`) 毫秒级打包。
- **核心设计哲学**: 极简零冗余、单手拇指人体工学、流体字母波浪检索、原生弹出式文件夹与通知胶囊。

## 2. 目录与关键文件说明
- `AndroidManifest.xml` — 应用清单、权限声明与四大组件注册（桌面 Home、无障碍手势、通知监听、常驻保活服务）。
- `build.ps1` / `build.sh` — Windows PowerShell 与 Linux/GitHub Actions 跨平台自动编译脚本（自动生成双包名版本：`com.niagara.local` 与 `com.niagara.loca2`）。
- `res/` — 矢量图标 (`drawable/ic_launcher.xml`)、应用名称与文案 (`values/strings.xml`)、主题样式 (`values/styles.xml`)、无障碍配置 (`xml/accessibility.xml`)。
- `src/com/niagara/local/` — 全部核心 Java 源码：
  - `MainActivity.java`: 主桌面容器、流式列表适配器 (`StreamAdapter`)、弹出式文件夹、搜索与快捷计算引擎调用。
  - `NiagaraWaveView.java`: 边缘流体字母波浪导轨与动态系统手势排除区。
  - `NiagaraCoinButton.java`: 右下角悬浮灵动圆钮（单击搜索、四向手势与长按控制中心）。
  - `SettingsSheetController.java`: 分组卡片式控制中心与后台保活/权限体检中心。
  - `AuroraBackdropView.java` / `AuroraTheme.java`: 日出日落护眼壁纸与昼夜主题引擎。
  - `HeroStatusBarView.java` / `LunarStatusEngine.java` / `WeatherSyncEngine.java`: 顶部状态栏、农历节气与本地气象同步。
  - `NotificationHubService.java` / `GestureLockService.java` / `KeepAliveService.java`: 通知监听、无障碍双击锁屏/多任务与保活守护。
  - `PinyinEngine.java` / `QuickCalcEngine.java` / `IconPackManager.java` / `WidgetHostHelper.java`: 拼音/T9 搜索、内联计算器、图标造型重塑与小组件托管。

## 3. Agent 修改与编译规范
1. **Java 类文件命名限制**：`src/com/niagara/local/` 下的 `.java` 文件名必须与文件内顶层类声明保持一致（Java 编译器硬性要求），切勿单独重命名 `.java` 文件而不修改类声明与全项目引用。
2. **签名密钥说明**：仓库已排除生产签名文件 `release.keystore`；本地或 CI 首次运行 `build.ps1` / `build.sh` 时若未检测到 `release.keystore`，会自动通过 `keytool` 生成测试签名完成打包。
3. **版本升级流程**：修改功能后，同步更新 `AndroidManifest.xml` 与 `build.ps1` / `.github/workflows/build-apk.yml` 中的 `versionCode` 与 `versionName`。
