# 极简桌面 (Minimalist Launcher · v2.9.0)

一款追求极致流畅与单手人体工学的 Android 原生桌面启动器（Home App），零第三方臃肿库依赖，支持首页一体流（Unified Stream）、App Pop-up 关联子应用集合、动态未读通知浮现、自适应流体字母波浪导轨、120Hz 紧贴软键盘极速搜索、弹出式文件夹、日出日落护眼壁纸与无障碍手势控制。

## v2.9.0 新特性（全栈架构精简与极简 UI 降噪）
- **后台与渲染架构精简**：移除历史遗留死代码与静态壁纸 Bitmap 内存泄漏；抽出 [`AppPickerSheet`](src/com/niagara/local/AppPickerSheet.java) 与 [`VectorGlyphView`](src/com/niagara/local/VectorGlyphView.java) 统一 5 处多选/排序弹窗与全桌面线性矢量图标渲染；移除 `AuroraBackdropView` 闲置 360ms 自刷新循环，停留在桌面时实现零额外后台轮询唤醒。
- **控制中心视觉折叠降噪**：将 5 项权限与保活体检收拢为顶部单行动态状态胶囊（5/5 就绪时默认折叠，缺失项自动展开提醒）；将状态栏 3 项开关合并为单一三档模式选择器；将低频外观与排版细节归入可展开二级区。
- **全系统零 Emoji 统一排版与空间克制**：移除弹窗、状态卡片、工具条与媒体时间副标题中的冗余 Emoji 与装饰文字；Pop-up 浮层工具条去重并放大头部热区至 `36dp`；所有底部选择器严格限高 82% 并吸顶搜索与吸底保存按钮。

## v2.8.0 特性（Niagara 原生灵魂架构全面重构）
- **首页一体流（Unified Stream）与极简顶部一览**：`NiagaraHeaderView` 将时钟、日期/农历、天气/智能电量、矢量日程/便签、无边框极简媒体条及桌面微件融入 `ListView` 纵向滚动流；支持 3 种时钟排版风格、按需智能电量显示与跨午夜日期实时监听。
- **原生 Niagara 流体波浪导轨与 120Hz 贴键盘搜索**：自适应字母表（支持静默圆点模式与左手镜像防冲突），`WindowInsetsAnimation.Callback` 逐帧紧贴输入法升落。
- **统一锚点 Pop-up 体系与动态未读通知**：`PopupCardController` 统一应用右滑/长按与文件夹 Pop-up，支持 App Pop-up 关联子应用集合、内联快捷回复与非首页未读通知动态浮现。

## 云端与本地构建
- **云端自动打包与发布 Release（公开仓库免费）**：每次推送或合并更新了版本号的代码至 `main` 分支，或推送 `v*` 标签（如 `v2.9.0`），GitHub Actions 将自动编译并同步创建 **GitHub Release**（附带 `极简桌面-v${versionName}.apk` 与 `极简桌面-v${versionName}-direct.apk` 双安装包）；PR 与手动触发也会生成 Actions 产物。
- **AI Agent 协作**：仓库内置 `AGENTS.md` 架构与构建指南，其他 AI Agent 接入仓库后可直接读取完整源码结构并完成修改与验证。
