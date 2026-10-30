# Guardian Sleeve: Wearable IoT & Android Native Safety Architecture

## 1. System Overview

Guardian Sleeve provides multi-modal distress detection for women safety, child tracking, and accident deterrence. It bridges a low-power ESP32 wearable sleeve with a native Android smartphone app and a real-time Owner web/mobile monitoring portal.

```mermaid
flowchart TD
    subgraph Wearable ["Guardian Sleeve Hardware (ESP32 WROOM)"]
        BTN[Panic Button GPIO 4] --> ESP[ESP32 Low Power MCU]
        MAX[MAX30102 Pulse & SpO2] -->|I2C| ESP
        ADXL[ADXL345 Accelerometer] -->|I2C Freefall & Crash| ESP
        MIC[INMP441 I2S Mic] -->|I2S Acoustics| ESP
        ESP -->|BLE GATT Server| BLE_ADV[Encrypted BLE Broadcast]
    end

    subgraph Phone ["Android Native Application (Guardian Sleeve App)"]
        BLE_ADV -.->|Auto-Connect| BLE_MGR[GuardianBleManager]
        VOICE[On-Device Voice Engine\n'help help help' 3x] --> TRIGGER[Multi-Modal Trigger Engine]
        ACCEL[Phone Internal Accelerometer\nCrash & Freefall Fallback] --> TRIGGER
        BLE_MGR -->|Pulse Spike / Button / Fall| TRIGGER
        
        TRIGGER -->|Stage 1| COUNTDOWN[2-Stage Escalation\n'Are You Okay?' 5s Countdown]
        COUNTDOWN -->|Dismissed in 5s with PIN| SAFE[Safe Status Restored]
        COUNTDOWN -->|5s Timeout or Immediate SOS| STAGE2[Full SOS Escalation]
        
        STAGE2 --> OVERLAY[Stealth Takeover Engine\n'Fake Shutdown' Blackout Screen]
        STAGE2 --> GEO[Haversine Geo-Selector\n2 Closest Dynamic + 3 Fixed Contacts]
        STAGE2 --> TELEGRAM[Telegram Bot API\nBroadcasts Alert to Top 5 Contacts]
        STAGE2 --> CALL[Direct Emergency Call Engine\nPolice 112 / Helpline]
    end

    subgraph Cloud ["Owner Remote Portal & Alarm System"]
        STAGE2 -.->|REST / WebSocket| RELAY[Cloud Relay Backend]
        RELAY --> OWNER_WEB[Owner Web Portal]
        RELAY --> OWNER_APP[Owner Mobile Role]
        
        STAGE2 -.->|Accident / Hard Bang| OWNER_BEEP[🚨 CONTINUOUS STRONG ALARM BEEP\nRings until Owner manually silences]
    end
```

---

## 2. Hard Bang, Collision Accident & Fall from Height Detection

### A. Detection Algorithm (Dual-Layer: ESP32 ADXL345 + Phone Sensor)
1. **Free-Fall Phase**: Weightlessness detection where acceleration vector $|a| = \sqrt{x^2+y^2+z^2} < 0.4\text{ g}$ ($<3.5\text{ m/s}^2$) for $> 120\text{ ms}$.
2. **Impact / Crash Bang Phase**: Sudden violent impact where vector magnitude $|a| > 4.8\text{ g}$ ($> 45\text{ m/s}^2$), or preceded by free-fall with $|a| > 3.5\text{ g}$.
3. **Trigger**: Emits `TriggerSource.ACCIDENT_FALL_BANG`.

### B. Two-Stage Escalation Workflow
- **Stage 1 (Carrier Prompt)**:
  - Phone vibrates and displays: *"Are you okay? Hard Bang / Crash / Fall from Height detected!"*
  - 5-second countdown timer.
  - If user taps **"I AM SAFE (CANCEL)"** within 5 seconds $\rightarrow$ safe status restored, no emergency dispatched.
- **Stage 2 (Owner Continuous Alarm)**:
  - If 5 seconds elapse without dismissal:
    1. **Continuous Strong Alarm Beep**:
       - **Owner Web Portal**: Web Audio API synthesizer loops alternating $900\text{ Hz}$ and $1350\text{ Hz}$ high-decibel alarm tones.
       - **Owner Mobile App**: Android `ToneGenerator` / `OwnerAlarmBuzzer` sounds repeating CDMA emergency alert ringbacks at maximum volume.
       - **Persistent Modal**: A full-screen flashing alert appears with: *"CRITICAL ACCIDENT ALARM! Hard Bang / Fall from Height detected for Sohan. 5s check was unanswered!"*
       - **Manual Acknowledgment Requirement**: The alarm keeps beeping in an infinite loop until the Owner physically presses **"🔕 MANUALLY SILENCE ALARM & ACKNOWLEDGE"**.
    2. **Telegram SOS Alert**: Dispatched to 2 geographically closest contacts + 3 fixed contacts with GPS coordinates.
    3. **Stealth Screen Takeover**: Phone displays realistic shutdown animation and turns pitch-black while continuing background tracking.
    4. **Direct Call**: Emergency call dispatched to 112 / police.
