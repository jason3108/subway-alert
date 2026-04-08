# 🚇 地铁警报 (Subway Alert)

北京地铁站点监控应用，当您接近或离开预设地铁站时自动提醒。

## 功能特性

### 🎯 核心功能
- **GPS 位置监控** - 实时追踪用户位置，计算与预设站点的距离
- **多站点管理** - 支持添加多个地铁站为监控点（最多支持北京地铁 500+ 站点）
- **灵活监控模式**
  - 🔄 轮询模式 - 定时检查位置（可调节间隔 10-60 秒）
  - 📍 电子围栏模式 - 使用 Android Geofencing API
- **多种提醒方式**
  - 📳 震动提醒（短震动 / 长震动 / 单次震动）
  - 🔔 声音提醒（可选）
  - 📲 通知栏提醒

### 📱 站点添加方式
1. **预设站点选择** - 按线路浏览，选择预设站点
2. **搜索添加** - 输入站点名称自动搜索并添加
3. **自定义坐标** - 支持添加任意位置的坐标点

### 🔄 OTA 无线更新
- 内置 OTA 更新服务器客户端
- 支持自定义 OTA 服务器地址
- 自动检测、下载、安装更新

### 🌐 局域网 APK 分发
- 内置 HTTP 服务器，可分享 APK 给同一 WiFi 下的其他设备
- 无需数据线，方便测试和分发

## 系统要求

- **Android 版本**: 8.0 (API 26) 及以上
- **必要权限**:
  - 位置权限（精确定位 / 模糊定位）
  - 后台位置权限（用于后台监控）
  - 通知权限

## 项目结构

```
subway-alert/
├── app/
│   └── src/main/
│       ├── java/com/subwayalert/
│       │   ├── data/
│       │   │   ├── local/           # 本地存储 (DataStore)
│       │   │   └── repository/       # 数据仓库
│       │   │       ├── GeocodingRepository.kt
│       │   │       ├── StationRepository.kt
│       │   │       └── UpdateRepository.kt
│       │   ├── di/                   # Hilt 依赖注入
│       │   ├── domain/model/         # 数据模型
│       │   │       ├── Station.kt
│       │   │       ├── Settings.kt
│       │   │       ├── PresetStations.kt  # 北京地铁 500+ 站点数据
│       │   │       └── UpdateInfo.kt
│       │   ├── presentation/
│       │   │   ├── ui/              # UI 层
│       │   │   │   ├── MainActivity.kt
│       │   │   │   └── screens/MainScreen.kt
│       │   │   └── viewmodel/       # ViewModel
│       │   │       ├── MainViewModel.kt
│       │   │       └── UpdateViewModel.kt
│       │   ├── receiver/            # 广播接收器
│       │   └── service/             # 后台服务
│       └── res/                     # 资源文件
├── ota-server/                      # OTA 服务器端
│   ├── ota_server.py               # Python HTTP 服务器
│   ├── version.json                # 版本信息配置
│   └── apk/                        # APK 存储目录
└── build_apk.sh                    # 自动构建脚本
```

## 架构

本应用采用 **MVVM + Clean Architecture** 架构：

```
┌─────────────────────────────────────────────────┐
│                   UI Layer                       │
│  ┌─────────────┐    ┌─────────────────────────┐  │
│  │ MainScreen  │───▶│   MainViewModel        │  │
│  └─────────────┘    └───────────┬───────────┘  │
└──────────────────────────────────┼──────────────┘
                                   │
┌──────────────────────────────────┼──────────────┐
│               Domain Layer       ▼              │
│  ┌─────────────┐    ┌─────────────────────────┐  │
│  │   Models    │◀───│   Repositories          │  │
│  └─────────────┘    └───────────┬───────────┘  │
└──────────────────────────────────┼──────────────┘
                                   │
┌──────────────────────────────────┼──────────────┐
│               Data Layer         ▼              │
│  ┌─────────────┐    ┌─────────────────────────┐  │
│  │ DataStore   │◀───│  PreferencesManager     │  │
│  └─────────────┘    └─────────────────────────┘  │
└──────────────────────────────────────────────────┘
```

### 核心技术栈

| 组件 | 技术 |
|-----|-----|
| UI | Jetpack Compose + Material Design 3 |
| 架构 | MVVM + Clean Architecture |
| 依赖注入 | Hilt |
| 异步处理 | Kotlin Coroutines + Flow |
| 本地存储 | DataStore Preferences |
| 位置服务 | Google Play Services Location |
| 后台监控 | Foreground Service |

## 构建

### 开发构建

```bash
./gradlew assembleDebug
```

APK 输出位置: `app/build/outputs/apk/debug/app-debug.apk`

### 发布构建

```bash
./build_apk.sh "版本更新说明"
```

脚本会自动：
1. 递增版本号
2. 构建 APK
3. 更新 OTA 服务器版本信息
4. 复制 APK 到 `ota-server/apk/`

## OTA 服务器部署

### 快速启动

```bash
cd ota-server
python3 ota_server.py
```

### systemd 服务

```bash
sudo nano /etc/systemd/system/subway-alert-ota.service
```

```ini
[Unit]
Description=Subway Alert OTA Server
After=network.target

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/path/to/ota-server
ExecStart=/usr/bin/python3 ota_server.py --host 0.0.0.0 --port 8080
Restart=always

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable subway-alert-ota
sudo systemctl start subway-alert-ota
```

## API 端点

### 检查更新

```
GET /api/check-update
```

响应示例:
```json
{
  "version": "1.0.28",
  "versionCode": 29,
  "downloadUrl": "/apk/app.apk",
  "releaseNotes": "提醒改为闹钟式持续振动，直到用户点击关闭按钮",
  "apkSize": 15788633
}
```

> **注意**: `downloadUrl` 返回的是相对路径 `/apk/app.apk`，客户端会根据检查更新时输入的服务器地址自动拼接完整的下载URL。

### 下载 APK

```
GET /apk/app.apk
```

## 版本历史

| 版本 | 说明 |
|-----|-----|
| 1.0.32 | 提醒改为完整闹钟模式：持续响铃+振动，全屏通知直到用户关闭 |
| 1.0.31 | 修复进入监控范围后站点高亮显示不正确的问题 |
| 1.0.30 | 优化地铁内定位：改用网络定位替代GPS，信号差时仍能更新位置 |
| 1.0.29 | 修复调试信息窗口使用设置中的监控半径 |
| 1.0.28 | 提醒改为闹钟式持续振动，直到用户点击关闭按钮 |
| 1.0.27 | 服务器URL改为相对路径，应用自动拼接完整下载URL |
| 1.0.26 | 修复测试提醒严格按设置振动模式执行 |
| 1.0.25 | 修复测试提醒中振动模式不正确的问题 |
| 1.0.24 | 修复APK版本号与显示版本不一致的问题 |
| 1.0.23 | 修复调试信息中测试提醒使用错误振动模式的问题 |
| 1.0.22 | 修复APK版本号与显示版本不一致的问题 |
| 1.0.21 | 修复更新后版本号显示不正确的问题，修复服务器地址记忆问题 |
| 1.0.20 | 设置中切换振动模式时实时播放振动效果 |
| 1.0.19 | 修复检查更新时服务器地址不记忆的问题 |
| 1.0.18 | 新增位置轨迹跟踪功能 |
| 1.0.17 | 删除站点时增加确认对话框 |
| 1.0.16 | 修复APK安装问题 |
| 1.0.15 | 修复点击通知后监控状态丢失问题 |
| 1.0.14 | 持久化监控状态 |
| 1.0.11 | 优化 APK 安装流程 |
| 1.0.8 | 预设站点添加线路信息 |
| 1.0.7 | 记住 OTA 服务器地址 |
| 1.0.1 | 初始版本 |

## 许可证

MIT License
