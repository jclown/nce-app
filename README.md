# 新概念英语逐句学习 App

Android 原生应用（Kotlin + Jetpack Compose），基于《新概念英语》1-4 册音频与逐句文本，提供逐句听读、跟读录音复习与每日打卡功能。纯本地存储，无账号、无后端、完全离线可用。

## 功能

- **学习**：四册书 276 个音频单元，双列课文卡片；点击课文进入句子列表
- **逐句听读**：点击句子播放该句原音（精确裁剪区间），再点暂停，播完自动连播下一句；当前句高亮并自动滚动
- **语言模式**：EN / EN+CN / CN 三态切换，全局偏好持久化
- **复习**：逐句录音（30 秒上限）、回放、删除重录，并排对照原文；点击句子文本可随时播放原音；全部录完标记复习通过
- **打卡**：当天完成一课即在日历打点，顶部显示连胜天数；支持翻月查看历史

## 技术栈

| 项 | 选型 |
|---|---|
| 语言 | Kotlin 2.1 |
| UI | Jetpack Compose + Material 3 |
| 音频播放 | Media3 ExoPlayer（`ClippingConfiguration` 逐句裁剪） |
| 录音 | MediaRecorder（AAC 16kHz / 64kbps） |
| 数据库 | Room（进度、打卡、录音记录） |
| 偏好 | DataStore Preferences |
| 依赖注入 | Hilt |
| 导航 | Navigation Compose |

`minSdk 26`，`compileSdk 36`，JDK 17+。

## 项目结构

```
app/src/main/java/com/example/nce/
├── data/
│   ├── db/          # Room 实体与 DAO（book/lesson/sentence/progress/check_in/recording）
│   ├── lrc/         # LrcParser：解析 "English | Chinese" 带时间戳歌词
│   ├── asset/       # BookImporter：首次启动解压 assets 中的 zip 并导入数据库
│   └── repo/        # ProgressRepository / CheckInRepository / SettingsRepository
├── domain/          # StreakCalculator：连胜天数算法
├── player/          # SentencePlayer：逐句裁剪播放（专用工作线程）
├── recorder/        # SentenceRecorder：逐句录音
└── ui/              # study / lesson / review / checkin 四个页面 + 组件
```

## 内容数据源

音频与歌词来自仓库上级目录 `us/NCE1` ~ `us/NCE4`，每课一对文件：

- `*.mp3`：整课音频
- `*.lrc`：逐句文本，格式 `[mm:ss.xx]English | Chinese`

命名差异由解析器兼容：NCE1 为 `001&002.Excuse Me.*`（两课合并），NCE2-4 为 `01.A Private Conversation.*`。

## 构建与运行

### 1. 生成音频资源（首次）

`app/src/main/assets/books/*.zip` 体积约 616MB，不入库（见 `.gitignore`），构建前需从源目录打包：

```powershell
$src = ".."   # us 目录，含 NCE1..NCE4
$dst = "app/src/main/assets/books"
New-Item -ItemType Directory -Force -Path $dst | Out-Null
foreach ($b in "NCE1","NCE2","NCE3","NCE4") {
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  [System.IO.Compression.ZipFile]::CreateFromDirectory(
    (Join-Path (Resolve-Path $src) $b),
    (Join-Path $dst "$($b.ToLower()).zip"),
    [System.IO.Compression.CompressionLevel]::NoCompression, $false)
}
```

### 2. 构建 APK

```powershell
# 配置 local.properties: sdk.dir=<Android SDK 路径>
.\gradlew.bat assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk（约 637MB，含全部音频）
```

### 3. 安装

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

首次启动会在后台解压导入四册内容（进度显示在学习页顶部），约需数十秒。

### 4. 运行单元测试

```powershell
.\gradlew.bat testDebugUnitTest
```

覆盖 LRC 解析、文件名解析、连胜算法。

## 关键设计说明

- **逐句播放**：每句构建带 `[startMs, endMs]` 裁剪区间的 `MediaItem`，区间边界由播放器精确控制，播完进入 `STATE_ENDED` 驱动连播，无异步 seek 竞态
- **播放器线程**：`SentencePlayer` 的全部操作在专用 `HandlerThread` 执行，页面转场不被播放器初始化阻塞；课文页与复习页各持独立实例，互不干扰
- **完成判定**：全部句子完整播放过，或手动点"完成本课"，即写入当日打卡（`UNIQUE(date, lessonId)` 防重复）
- **连胜计算**：基于 `epochDay` 整数回推，今天或昨天有打卡才延续
- **录音存储**：`filesDir/recordings/<lessonId>/s<index>.m4a`，一句约 10-20KB

## 权限

- `RECORD_AUDIO`：复习页逐句录音（运行时申请）
- `WAKE_LOCK`：播放保活

## 版权说明

新概念英语教材音频与文本为受版权保护内容，本项目仅供个人学习使用，请勿公开分发或上架应用商店。

## 音频来源
https://github.com/wychl/nce/
