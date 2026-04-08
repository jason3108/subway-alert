# OTA 更新服务器部署指南

## 目录结构

```
ota-server/
├── ota_server.py      # Python HTTP 服务器
├── version.json       # 版本信息配置
├── apk/
│   └── app.apk        # 最新版APK (需手动上传)
└── logs/              # 日志目录
```

## 快速启动

```bash
cd /path/to/ota-server
python3 ota_server.py
```

服务启动后访问：
- API: http://localhost:8080/api/check-update
- 首页: http://localhost:8080/

## 使用 systemd 管理服务

创建服务文件：
```bash
sudo nano /etc/systemd/system/subway-alert-ota.service
```

内容：
```ini
[Unit]
Description=Subway Alert OTA Server
After=network.target

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/home/ubuntu/.openclaw/workspace/subway-alert/ota-server
ExecStart=/usr/bin/python3 ota_server.py --host 0.0.0.0 --port 8080
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

启用服务：
```bash
sudo systemctl daemon-reload
sudo systemctl enable subway-alert-ota
sudo systemctl start subway-alert-ota
```

## 更新 APK 步骤

1. 上传新 APK 到 `apk/app.apk`
2. 更新 `version.json` 中的版本信息

```bash
# 上传 APK
scp app-new.apk ubuntu@your-server:/home/ubuntu/.openclaw/workspace/subway-alert/ota-server/apk/app.apk

# 编辑版本信息
nano /home/ubuntu/.openclaw/workspace/subway-alert/ota-server/version.json
```

## version.json 配置说明

```json
{
  "version": "1.0.1",           // 显示版本号
  "versionCode": 2,             // 必须大于当前App版本号
  "downloadUrl": "http://...",  // APK下载地址
  "releaseNotes": "• 修复xxx\n• 新增xxx",
  "apkSize": 15638742           // APK文件大小(字节)
}
```

## App 端配置

Android App 默认连接 `http://10.0.2.2:8080`（模拟器访问宿主机）

**真机测试需要修改地址**：
编辑 `UpdateRepository.kt`：
```kotlin
private const val OTA_SERVER_URL = "http://你的服务器IP:8080"
```

然后重新编译 APK。

## Nginx 反向代理（可选）

如果需要 HTTPS 或域名访问：

```nginx
server {
    listen 80;
    server_name ota.yourdomain.com;

    location / {
        proxy_pass http://127.0.0.1:8080;
    }
}
```
