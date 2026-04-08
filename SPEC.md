# Subway Alert - 地铁站提醒应用

## 1. 项目概述

- **项目名称**: SubwayAlert
- **类型**: Android 原生应用
- **核心功能**: 用户输入地铁站名称，当 GPS 检测到接近该站时自动振动提醒
- **目标用户**: 地铁通勤者，防止坐过站

## 2. 技术方案

### 技术栈
- **语言**: Kotlin
- **最低 SDK**: 26 (Android 8.0)
- **目标 SDK**: 34 (Android 14)
- **UI**: Jetpack Compose + Material 3
- **架构**: MVVM + Clean Architecture
- **DI**: Hilt
- **位置服务**: Google Play Services Location (FusedLocationProviderClient)
- **地理围栏**: Geofencing API
- **后台服务**: Foreground Service (保持位置监控)
- **数据存储**: DataStore Preferences

### 核心逻辑
1. 用户输入地铁站名称
2. 通过 Geocoding API 将站名转换为经纬度坐标
3. 在该坐标创建 200-300 米半径的地理围栏
4. 当用户进入围栏时，触发振动提醒
5. 持续后台监控

## 3. 功能列表

### 核心功能
- [ ] 地铁站名称输入 (带自动补全/历史记录)
- [ ] GPS 位置监控服务 (前台服务，状态栏通知)
- [ ] 地理围栏创建与管理
- [ ] 接近提醒 + 振动
- [ ] 已监控站点列表管理
- [ ] 手动测试提醒功能

### 设置
- [ ] 提醒半径调整 (100-500米)
- [ ] 振动模式选择 (短振动/长振动/重复)
- [ ] 提醒音效开关

## 4. 应用界面

### 主界面
```
┌─────────────────────────┐
│      Subway Alert        │
│                         │
│  ┌─────────────────┐   │
│  │ 输入地铁站名称    │   │
│  └─────────────────┘   │
│                         │
│  [添加监控]            │
│                         │
│  已监控站点:            │
│  ┌─────────────────┐   │
│  │ 🚇 人民广场站    │   │
│  │ 🚇 陆家嘴站      │   │
│  └─────────────────┘   │
│                         │
└─────────────────────────┘
```

### 监控中状态
```
┌─────────────────────────┐
│      Subway Alert        │
│  ● 正在监控             │
│                         │
│  🚇 人民广场站           │
│  距离: 约 1.2 公里       │
│  [测试提醒] [停止监控]   │
│                         │
└─────────────────────────┘
```

## 5. 权限需求

- `ACCESS_FINE_LOCATION` - 精确定位
- `ACCESS_COARSE_LOCATION` - 粗略定位
- `ACCESS_BACKGROUND_LOCATION` - 后台位置访问
- `FOREGROUND_SERVICE` - 前台服务
- `FOREGROUND_SERVICE_LOCATION` - 位置前台服务
- `POST_NOTIFICATIONS` - 发送通知
- `VIBRATE` - 振动

## 6. 组件架构

```
app/
├── data/
│   ├── repository/
│   └── local/          # DataStore
├── domain/
│   ├── model/          # Station, Geofence
│   └── usecase/
├── presentation/
│   ├── ui/
│   │   ├── screens/    # MainScreen, AddStationDialog
│   │   ├── components/
│   │   └── theme/
│   └── viewmodel/
├── service/
│   └── LocationMonitoringService  # Foreground Service
├── receiver/
│   └── GeofenceBroadcastReceiver
└── di/
```
