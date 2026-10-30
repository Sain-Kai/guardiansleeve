#!/usr/bin/env python3
"""
Guardian Sleeve - USB Serial Hardware Bridge
Bridges the physical ESP32 connected via USB (/dev/ttyUSB0) to:
1. The Android Emulator via ADB Broadcast
2. The Backend Relay Server via HTTP REST
"""

import sys
import time
import subprocess
import urllib.request
import json
import serial

SERIAL_PORT = "/dev/ttyUSB0"
BAUD_RATE = 115200
ADB_PATH = "/home/neymar-jr/Android/Sdk/platform-tools/adb"
BACKEND_URL = "http://127.0.0.1:5000/api/telemetry"

def get_adb_devices():
    try:
        res = subprocess.run([ADB_PATH, "devices"], capture_output=True, text=True, timeout=2)
        devices = []
        for line in res.stdout.strip().splitlines()[1:]:
            parts = line.strip().split()
            if len(parts) >= 2 and parts[1] == "device":
                devices.append(parts[0])
        return devices
    except Exception:
        return []

def send_adb_broadcast(alert_type):
    devices = get_adb_devices()
    if not devices:
        # Fallback to direct broadcast if devices list empty
        cmd = [
            ADB_PATH, "shell", "am", "broadcast",
            "-a", "com.guardiansleeve.app.HARDWARE_ALERT",
            "-p", "com.guardiansleeve.app",
            "--es", "alert", alert_type
        ]
        try:
            subprocess.run(cmd, capture_output=True, timeout=2)
            print(f"📡 [ADB BROADCAST] Alert: {alert_type} sent to default target")
        except Exception as e:
            print(f"⚠️ ADB broadcast failed: {e}")
        return

    for dev in devices:
        cmd = [
            ADB_PATH, "-s", dev, "shell", "am", "broadcast",
            "-a", "com.guardiansleeve.app.HARDWARE_ALERT",
            "-p", "com.guardiansleeve.app",
            "--es", "alert", alert_type
        ]
        try:
            res = subprocess.run(cmd, capture_output=True, text=True, timeout=2)
            print(f"📡 [ADB BROADCAST] Alert: {alert_type} -> {dev} (Result: {res.stdout.strip()})")
        except Exception as e:
            print(f"⚠️ ADB broadcast to {dev} failed: {e}")

def send_backend_alert(trigger_source, is_accident=False):
    payload = {
        "userId": "sohan",
        "alertState": "STAGE_1_COUNTDOWN",
        "triggerSource": trigger_source,
        "isAccident": is_accident,
        "statusMessage": f"Hardware Trigger: {trigger_source}"
    }
    try:
        req = urllib.request.Request(
            BACKEND_URL,
            data=json.dumps(payload).encode('utf-8'),
            headers={"Content-Type": "application/json"}
        )
        urllib.request.urlopen(req, timeout=1)
    except Exception:
        pass

def main():
    print(f"=== Guardian Sleeve Hardware Bridge Starting ===")
    print(f"Monitoring {SERIAL_PORT} at {BAUD_RATE} baud...")

    while True:
        try:
            ser = serial.Serial()
            ser.port = SERIAL_PORT
            ser.baudrate = BAUD_RATE
            ser.timeout = 1
            ser.dtr = False
            ser.rts = False
            ser.open()
            # Clean boot pulse to ensure ESP32 leaves bootloader/strapping state
            ser.setDTR(False)
            ser.setRTS(True)
            time.sleep(0.05)
            ser.setRTS(False)
            time.sleep(0.2)
            print(f" Connected to ESP32 on {SERIAL_PORT} (clean boot triggered)!")
            while True:
                line = ser.readline().decode('utf-8', errors='replace').strip()
                if not line:
                    continue

                print(f"[ESP32] {line}")

                # 1. Hardware Panic Button
                if "ALERT:BUTTON" in line or "Panic Button Pressed" in line:
                    print("🚨 HARDWARE PANIC BUTTON DETECTED!")
                    send_adb_broadcast("BUTTON")
                    send_backend_alert("SLEEVE_BUTTON")

                # 2. Hard Bang / Accident / Fall
                elif "ALERT:ACCIDENT_FALL" in line or "Hard Bang" in line:
                    print("🚨 ACCIDENT / HARD BANG / FALL DETECTED!")
                    send_adb_broadcast("ACCIDENT_FALL")
                    send_backend_alert("ACCIDENT_FALL_BANG", is_accident=True)

                # 3. Sudden Movement / Struggle / Shake
                elif "ALERT:IMPACT" in line or "Sudden Movement" in line or "Impact Detected" in line:
                    print("🚨 SUDDEN MOVEMENT / IMPACT DETECTED!")
                    send_adb_broadcast("IMPACT")
                    send_backend_alert("IMPACT_FALL")

                # 4. Palpitation Spike
                elif "ALERT:PALPITATION" in line or "Palpitation Spike" in line:
                    print("🚨 PALPITATION SPIKE DETECTED!")
                    send_adb_broadcast("PALPITATION")
                    send_backend_alert("PALPITATION_SPIKE")

                # 5. Acoustic Loud Shout from INMP441 -> Wakes Mobile Mic
                elif "ALERT:LOUD_SHOUT_WAKE_MIC" in line or "Loud shout" in line:
                    print("🎙️ INMP441 ACOUSTIC SHOUT DETECTED! Waking Mobile Mic...")
                    send_adb_broadcast("LOUD_SHOUT_WAKE_MIC")

        except serial.SerialException as e:
            print(f"Waiting for {SERIAL_PORT}... ({e})")
            time.sleep(2)
        except KeyboardInterrupt:
            print("Bridge stopped by user.")
            sys.exit(0)

if __name__ == "__main__":
    main()
