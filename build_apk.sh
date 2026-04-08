#!/bin/bash
# 构建脚本 - 自动版本递增 + 发布OTA服务器
# 用法: ./build_apk.sh [版本说明]

set -e

PROJECT_DIR="/home/ubuntu/.openclaw/workspace/subway-alert"
OTA_SERVER_DIR="$PROJECT_DIR/ota-server"
VERSION_FILE="$OTA_SERVER_DIR/version.json"
BUILD_FILE="$PROJECT_DIR/app/build.gradle.kts"

# 默认版本说明
if [ -z "$1" ]; then
    CHANGE_LOG="• 常规更新"
else
    CHANGE_LOG="$1"
fi

echo "========================================"
echo "  地铁警报 OTA 构建脚本"
echo "========================================"

# 1. 读取当前版本 (从 build.gradle.kts)
CURRENT_VERSION=$(grep 'versionName' "$BUILD_FILE" | grep -v 'versionName property' | sed 's/.*versionName = "\(.*\)".*/\1/' | head -1)
CURRENT_CODE=$(grep 'versionCode' "$BUILD_FILE" | grep -v 'versionCode property' | sed 's/.*versionCode = \(.*\)/\1/' | head -1)

echo "当前版本: $CURRENT_VERSION (versionCode: $CURRENT_CODE)"
echo "变更日志: $CHANGE_LOG"

# 2. 计算新版本
NEW_CODE=$((CURRENT_CODE + 1))
# 语义化版本: 1.0.0 -> 1.0.1
IFS='.' read -ra VER <<< "$CURRENT_VERSION"
MAJOR="${VER[0]}"
MINOR="${VER[1]:-0}"
PATCH="${VER[2]:-0}"
NEW_PATCH=$((PATCH + 1))
NEW_VERSION="$MAJOR.$MINOR.$NEW_PATCH"

echo "新版本: $NEW_VERSION (versionCode: $NEW_CODE)"

# 3. 更新 build.gradle.kts
sed -i "s/versionName = \"$CURRENT_VERSION\"/versionName = \"$NEW_VERSION\"/" "$BUILD_FILE"
sed -i "s/versionCode = $CURRENT_CODE/versionCode = $NEW_CODE/" "$BUILD_FILE"

echo "✅ build.gradle.kts 已更新"

# 3.5 更新 UpdateInfo.kt (运行时版本常量)
UPDATE_INFO="$PROJECT_DIR/app/src/main/java/com/subwayalert/domain/model/UpdateInfo.kt"
sed -i "s/const val VERSION = \"$CURRENT_VERSION\"/const val VERSION = \"$NEW_VERSION\"/" "$UPDATE_INFO"
sed -i "s/const val VERSION_CODE = $CURRENT_CODE/const val VERSION_CODE = $NEW_CODE/" "$UPDATE_INFO"

echo "✅ UpdateInfo.kt 已更新"

# 4. 构建 APK
echo ""
echo "📦 正在构建 APK..."
cd "$PROJECT_DIR"
./gradlew assembleDebug --quiet

APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK_PATH" ]; then
    echo "❌ 构建失败!"
    exit 1
fi

APK_SIZE=$(stat -c%s "$APK_PATH")
APK_SIZE_MB=$(echo "scale=2; $APK_SIZE / 1024 / 1024" | bc)

echo "✅ APK 构建成功: ${APK_SIZE_MB}MB"

# 5. 复制到 OTA 服务器
cp "$APK_PATH" "$OTA_SERVER_DIR/apk/app.apk"
echo "✅ APK 已复制到 OTA 服务器"

# 6. 更新 version.json
cat > "$VERSION_FILE" << EOF
{
  "version": "$NEW_VERSION",
  "versionCode": $NEW_CODE,
  "downloadUrl": "/apk/app.apk",
  "releaseNotes": "$CHANGE_LOG",
  "apkSize": $APK_SIZE
}
EOF

echo "✅ version.json 已更新"

# 7. 输出变更记录历史 (追加到CHANGELOG.md)
CHANGELOG="$OTA_SERVER_DIR/CHANGELOG.md"
echo "" >> "$CHANGELOG"
echo "### v$NEW_VERSION (versionCode $NEW_CODE) - $(date '+%Y-%m-%d %H:%M')" >> "$CHANGELOG"
echo "$CHANGE_LOG" >> "$CHANGELOG"
echo "" >> "$CHANGELOG"

echo ""
echo "========================================"
echo "  ✅ 构建完成!"
echo "========================================"
echo ""
echo "新版本信息:"
echo "  版本: $NEW_VERSION"
echo "  VersionCode: $NEW_CODE"
echo "  APK大小: ${APK_SIZE_MB}MB"
echo ""
echo "变更说明:"
echo "$CHANGE_LOG"
echo ""
echo "OTA 服务器运行中: http://localhost:8080"