# Guardian Sleeve - Hardware Wiring & Schematics

## 1. Components List

1. **Microcontroller**: ESP32 WROOM-32 (30-pin or 38-pin DevKit)
2. **PPG & Pulse Oximeter**: MAX30102 (I2C)
3. **Digital Accelerometer**: ADXL345 (I2C)
4. **Digital I2S Microphone**: INMP441 (MEMS High Sensitivity Omnidirectional)
5. **Panic Button**: Momentary Push-button (Tactile Switch)
6. **Status LED**: 3mm Green LED (or onboard GPIO 2) + 220Ω resistor
7. **Power Source**: 3.7V 600mAh - 1200mAh LiPo Battery + TP4056 Micro-USB/Type-C Charger + ME6211 3.3V Low-Dropout Regulator (LDO)

---

## 2. Complete Pinout Table

| Sensor / Module | Sensor Pin | ESP32 WROOM-32 Pin | Function / Protocol |
| :--- | :--- | :--- | :--- |
| **MAX30102** | VCC | 3.3V | Power (3.3V) |
| | GND | GND | Ground |
| | SDA | GPIO 21 | I2C Data Line (Shared) |
| | SCL | GPIO 22 | I2C Clock Line (Shared) |
| | INT | GPIO 19 (Optional) | PPG Interrupt |
| **ADXL345** | VCC / VDD | 3.3V | Power (3.3V) |
| | GND | GND | Ground |
| | SDA | GPIO 21 | I2C Data Line (Shared) |
| | SCL | GPIO 22 | I2C Clock Line (Shared) |
| | CS | 3.3V | Pull HIGH for I2C mode |
| | SDO | GND | Sets I2C Address to `0x53` |
| | INT1 | GPIO 18 (Optional) | Impact Interrupt |
| **INMP441** | VDD | 3.3V | Power (3.3V) |
| | GND | GND | Ground |
| | SD | GPIO 32 | I2S Serial Data Out |
| | WS | GPIO 15 | I2S Word Select (Left/Right Clock) |
| | SCK | GPIO 14 | I2S Continuous Serial Clock |
| | L/R | GND | Set Left Channel |
| **Panic Button** | Terminal 1 | GPIO 4 | Interrupt Pin (Internal Pull-Up) |
| | Terminal 2 | GND | Connects to GND on press |
| **Status LED** | Anode (+) | GPIO 2 | Via 220Ω Resistor |
| | Cathode (-) | GND | Ground |

---

## 3. Power Consumption & Battery Optimization

- **Ultra-low power deep/light sleep**:
  - The ESP32 utilizes light-sleep between advertising intervals (300ms window).
  - Average current draw in standby: ~18-25 mA.
  - On a 600mAh LiPo battery, the sleeve operates continuously for over 24-30 hours on a single charge.
- **Hardware Button Wakeup**:
  - GPIO 4 can be configured as an external wakeup source from deep sleep (`esp_sleep_enable_ext0_wakeup(GPIO_NUM_4, 0)`).
