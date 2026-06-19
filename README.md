# 自动手 (ZiDongShou)

Android 自动化辅助工具，基于 AccessibilityService 实现屏幕点击、滑动、OCR 文字识别等操作，支持自定义任务编排与执行。

## 功能特性

- **无障碍自动化**：基于 AccessibilityService，模拟点击、滑动、长按等手势操作
- **OCR 文字识别**：集成 Google ML Kit 中文 OCR，支持离线本地识别屏幕文字
- **任务编排**：自定义步骤组合，支持顺序执行、条件判断
- **坐标拾取**：可视化坐标选择器，精准定位屏幕元素
- **悬浮窗控制**：悬浮球快捷操作，不影响正常使用
- **应用列表管理**：选择目标应用，自动跳转启动

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Java |
| 最低 SDK | API 22 (Android 5.1) |
| 目标 SDK | API 34 (Android 14) |
| UI 框架 | AndroidX, Material Design |
| 事件总线 | Otto |
| JSON 解析 | FastJSON |
| OCR 引擎 | Google ML Kit (中文离线识别) |
| 构建工具 | Gradle 8.0 |

## 项目结构

```
app/src/main/java/com/cmlanche/
├── activity/          # Activity 页面
│   ├── MainActivity               # 主界面
│   ├── NewOrEditTaskActivity      # 任务编辑
│   ├── TaskTypeListActivity       # 任务类型选择
│   ├── AppListActivity            # 应用列表
│   └── CoordinatePickerActivity   # 坐标拾取
├── adapter/           # RecyclerView 适配器
├── application/       # Application 入口
├── common/            # 工具类（设备信息、包管理、SP 存储）
├── core/              # 核心模块
│   ├── bus/           # 事件总线（BusManager）
│   ├── executor/      # 步骤执行器（点击、滑动）
│   ├── service/       # AccessibilityService
│   └── job/           # 任务调度
├── floatwindow/       # 悬浮窗
├── model/             # 数据模型
├── ocrcore/           # OCR 核心（屏幕截图 + ML Kit 识别）
└── view/              # 自定义 View
```

## 权限说明

应用需要以下权限以正常工作：

- `BIND_ACCESSIBILITY_SERVICE`：无障碍服务（核心功能）
- `SYSTEM_ALERT_WINDOW`：悬浮窗显示
- `FOREGROUND_SERVICE_MEDIA_PROJECTION`：屏幕截图（OCR 所需）

## 构建运行

1. 使用 Android Studio 打开项目根目录
2. 同步 Gradle 依赖
3. 连接设备或启动模拟器（API 22+）
4. 运行 app 模块

```bash
./gradlew assembleDebug
```

## License

仅供学习交流使用。
