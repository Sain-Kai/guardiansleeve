#!/usr/bin/env bash
# ==============================================================================
# Guardian Sleeve - Automatic Hotspot IP Synchronizer & Launcher
# ==============================================================================
# Usage:
#   ./scripts/update_hotspot_ip.sh               # Auto-detects current Wi-Fi/Hotspot IP
#   ./scripts/update_hotspot_ip.sh 192.168.43.50 # Or specify custom IP
# ==============================================================================

set -e

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"

ADB_BIN="${ADB_BIN:-/home/neymar-jr/Android/Sdk/platform-tools/adb}"

echo "=========================================================="
echo "🛡️  GUARDIAN SLEEVE: HOTSPOT IP SYNCHRONIZER"
echo "=========================================================="

# 1. Determine IP Address
TARGET_IP="$1"
if [ -z "$TARGET_IP" ]; then
    # Try detecting Wi-Fi / Hotspot interface (wlo1, wlan0, etc.)
    TARGET_IP=$(ip -4 addr show dev wlo1 2>/dev/null | grep -oP '(?<=inet\s)\d+(\.\d+){3}' | head -n 1 || true)
    
    if [ -z "$TARGET_IP" ]; then
        # Fallback to default route interface
        TARGET_IP=$(ip route get 1.1.1.1 2>/dev/null | grep -oP '(?<=src\s)\d+(\.\d+){3}' | head -n 1 || true)
    fi

    if [ -z "$TARGET_IP" ]; then
        # Fallback to hostname -I
        TARGET_IP=$(hostname -I | awk '{print $1}')
    fi
fi

if [ -z "$TARGET_IP" ]; then
    echo "❌ Error: Could not automatically detect IP address. Please pass it as an argument:"
    echo "   ./scripts/update_hotspot_ip.sh <YOUR_HOTSPOT_IP>"
    exit 1
fi

SERVER_PORT=5000
SERVER_URL="http://${TARGET_IP}:${SERVER_PORT}"
echo "📍 Detected Host / Hotspot IP: $TARGET_IP"
echo "🔗 New Backend Server URL:    $SERVER_URL"
echo ""

# 2. Update Android Source Code Files
echo "📝 Updating project source code configurations..."

# Update AppSettings.kt
sed -i "s|const val DEFAULT_SERVER_URL = \".*\"|const val DEFAULT_SERVER_URL = \"${SERVER_URL}\"|g" \
    app/src/main/java/com/guardiansleeve/app/data/AppSettings.kt

# Update BackendSyncManager.kt
sed -i "s|private var backendBaseUrl: String = \".*\"|private var backendBaseUrl: String = \"${SERVER_URL}\"|g" \
    app/src/main/java/com/guardiansleeve/app/data/BackendSyncManager.kt

# Update UserDashboardScreen.kt preset
sed -i "s|serverUrlText = \"http://[0-9\.]*:5000\" }|serverUrlText = \"${SERVER_URL}\" }|g" \
    app/src/main/java/com/guardiansleeve/app/ui/screens/UserDashboardScreen.kt

echo "   AppSettings.kt updated."
echo "   BackendSyncManager.kt updated."
echo "   UserDashboardScreen.kt updated."

# 3. Notify Running Android Device / Emulator via ADB Broadcast
echo ""
echo "📱 Checking connected Android devices via ADB..."
if [ -f "$ADB_BIN" ]; then
    DEVICES=$("$ADB_BIN" devices | grep -w "device" | awk '{print $1}')
    if [ -n "$DEVICES" ]; then
        for DEV in $DEVICES; do
            if [[ "$DEV" =~ "emulator" ]]; then
                DEV_URL="http://10.0.2.2:${SERVER_PORT}"
                echo "   Emulator ($DEV) detected: Sending host loopback sync ($DEV_URL)..."
            else
                DEV_URL="${SERVER_URL}"
                echo "   Physical mobile phone ($DEV) detected: Sending Hotspot IP sync ($DEV_URL)..."
            fi
            "$ADB_BIN" -s "$DEV" shell am broadcast \
                -a com.guardiansleeve.app.UPDATE_SETTINGS \
                --es serverUrl "${DEV_URL}" >/dev/null 2>&1 || true
            echo "   Live update broadcast delivered to $DEV!"
        done
    else
        echo "   ℹ️ No active ADB device found. When app launches, it will use: $SERVER_URL"
    fi
else
    echo "   ℹ️ ADB tool not found at $ADB_BIN; skipping live broadcast."
fi

# 4. Check & Start Backend Server if not running
echo ""
echo "🌐 Checking Node.js Backend Server..."
if pgrep -f "backend/server.js" > /dev/null; then
    echo "   Backend server is already running on port ${SERVER_PORT}."
else
    echo "   Starting Node.js Backend Server in background..."
    nohup node backend/server.js > /tmp/guardian_backend.log 2>&1 &
    sleep 2
    if pgrep -f "backend/server.js" > /dev/null; then
        echo "   Backend server started successfully (logs: /tmp/guardian_backend.log)."
    else
        echo "   ⚠️ Failed to start backend server. Check /tmp/guardian_backend.log."
    fi
fi

# 5. Check & Start Hardware USB Bridge if ESP32 is plugged in
echo ""
echo "🔌 Checking Hardware ESP32 USB Connection..."
if [ -e "/dev/ttyUSB0" ]; then
    echo "   ESP32 hardware detected at /dev/ttyUSB0."
    if pgrep -f "hardware_bridge.py" > /dev/null; then
        echo "   Hardware bridge is already active."
    else
        echo "   Starting Hardware Serial Bridge in background..."
        nohup python3 -u scripts/hardware_bridge.py > /tmp/guardian_bridge.log 2>&1 &
        sleep 2
        if pgrep -f "hardware_bridge.py" > /dev/null; then
            echo "   Hardware bridge active (logs: /tmp/guardian_bridge.log)."
        else
            echo "   ⚠️ Failed to start hardware bridge. Check /tmp/guardian_bridge.log."
        fi
    fi
else
    echo "   ℹ️ No ESP32 detected at /dev/ttyUSB0 (running in simulation / pure phone mode)."
fi

echo ""
echo "=========================================================="
echo "🎉 ALL SYSTEMS SYNCHRONIZED TO: $SERVER_URL"
echo "   Owner Web Portal: http://localhost:${SERVER_PORT} or ${SERVER_URL}"
echo "=========================================================="
