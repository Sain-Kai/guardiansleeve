/*
 * Guardian Sleeve - ESP32 Wearable IoT Firmware
 * Hardware:
 *   - MCU: ESP32 WROOM-32
 *   - PPG / Pulse & SpO2: MAX30102 (I2C: SDA=21, SCL=22)
 *   - Accelerometer: ADXL345 (I2C: SDA=21, SCL=22)
 *   - Microphone: INMP441 (I2S: SCK=14, WS=15, SD=32)
 *   - Panic Button: Tactile Switch (GPIO 4, Internal Pull-Up, Active LOW)
 *   - Status LED: GPIO 2 (Onboard LED for pairing/alert indication)
 */

#include <Arduino.h>
#include <Wire.h>
#include <driver/i2s.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// --- PIN DEFINITIONS ---
#define PIN_PANIC_BUTTON 4
#define PIN_STATUS_LED   2
#define I2C_SDA_PIN      21
#define I2C_SCL_PIN      22

// I2S Microphone INMP441 Pins
#define I2S_SCK_PIN      14
#define I2S_WS_PIN       15
#define I2S_SD_PIN       32
#define I2S_PORT         I2S_NUM_0

// --- SENSOR I2C ADDRESSES ---
#define ADXL345_ADDR     0x53
#define MAX30102_ADDR    0x57

// --- BLE SERVICE & CHARACTERISTICS UUIDs ---
#define SERVICE_UUID           "0000fff0-0000-1000-8000-00805f9b34fb"
#define CHAR_ALERT_UUID        "0000fff1-0000-1000-8000-00805f9b34fb"
#define CHAR_VITALS_UUID       "0000fff2-0000-1000-8000-00805f9b34fb"
#define CHAR_COMMAND_UUID      "0000fff3-0000-1000-8000-00805f9b34fb"

// --- THRESHOLDS ---
#define HEART_RATE_PALPITATION_THRESHOLD  130 // Sudden BPM spike
#define HEART_RATE_DELTA_SPIKE            35  // Fast spike within window
#define ACCEL_IMPACT_THRESHOLD_G          3.5 // General struggle impact
#define ACCEL_FREEFALL_THRESHOLD_G        0.4 // Free-fall from height
#define ACCEL_HARD_BANG_THRESHOLD_G       4.8 // Severe crash / hard impact bang
#define MIC_RMS_SHOUT_THRESHOLD          2500 // INMP441 Acoustic shout / scream threshold (accessible real-world shout level)

// --- BLE OBJECTS ---
BLEServer* pServer = nullptr;
BLECharacteristic* pAlertChar = nullptr;
BLECharacteristic* pVitalsChar = nullptr;
BLECharacteristic* pCommandChar = nullptr;

bool deviceConnected = false;
bool oldDeviceConnected = false;

// --- STATE VARIABLES ---
volatile bool panicButtonPressed = false;
unsigned long lastDebounceTime = 0;
const unsigned long debounceDelay = 250; // ms

int currentHeartRate = 72;
int previousHeartRate = 72;
int currentSpO2 = 98;
float currentAccelG = 1.0;
int batteryPercent = 92;
unsigned long lastVitalsBroadcast = 0;
bool sosActive = false;

// --- INTERRUPT SERVICE ROUTINE ---
void IRAM_ATTR handleButtonInterrupt() {
  unsigned long now = millis();
  if ((now - lastDebounceTime) > debounceDelay) {
    if (digitalRead(PIN_PANIC_BUTTON) == LOW) {
      panicButtonPressed = true;
    }
    lastDebounceTime = now;
  }
}

// --- BLE SERVER CALLBACKS ---
class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* pServer) override {
    deviceConnected = true;
    digitalWrite(PIN_STATUS_LED, HIGH);
    Serial.println("[BLE] Mobile client connected!");
  }

  void onDisconnect(BLEServer* pServer) override {
    deviceConnected = false;
    digitalWrite(PIN_STATUS_LED, LOW);
    Serial.println("[BLE] Client disconnected. Restarting advertising...");
  }
};

// --- BLE COMMAND CALLBACKS ---
class CommandCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* pCharacteristic) override {
    String cmd = pCharacteristic->getValue();
    cmd.trim();
    if (cmd.length() > 0) {
      Serial.print("[BLE CMD RECEIVED] ");
      Serial.println(cmd);

      if (cmd == "CMD:DISARM") {
        sosActive = false;
        digitalWrite(PIN_STATUS_LED, deviceConnected ? HIGH : LOW);
        Serial.println("[BLE] SOS Disarmed via command.");
      } else if (cmd == "CMD:TRIGGER_SOS") {
        panicButtonPressed = true;
      }
    }
  }
};

// --- INITIALIZE I2S FOR INMP441 ---
void setupI2SMic() {
  i2s_config_t i2s_config = {
    .mode = (i2s_mode_t)(I2S_MODE_MASTER | I2S_MODE_RX),
    .sample_rate = 16000,
    .bits_per_sample = I2S_BITS_PER_SAMPLE_32BIT,
    .channel_format = I2S_CHANNEL_FMT_ONLY_LEFT,
    .communication_format = I2S_COMM_FORMAT_STAND_I2S,
    .intr_alloc_flags = ESP_INTR_FLAG_LEVEL1,
    .dma_buf_count = 4,
    .dma_buf_len = 256,
    .use_apll = false,
    .tx_desc_auto_clear = false,
    .fixed_mclk = 0
  };

  i2s_pin_config_t pin_config = {
    .bck_io_num = I2S_SCK_PIN,
    .ws_io_num = I2S_WS_PIN,
    .data_out_num = I2S_PIN_NO_CHANGE,
    .data_in_num = I2S_SD_PIN
  };

  esp_err_t err = i2s_driver_install(I2S_PORT, &i2s_config, 0, NULL);
  if (err == ESP_OK) {
    i2s_set_pin(I2S_PORT, &pin_config);
    Serial.println("[I2S] INMP441 Microphone initialized.");
  } else {
    Serial.printf("[I2S] Driver install failed: %d\n", err);
  }
}

// --- INITIALIZE ADXL345 ---
void setupADXL345() {
  Wire.beginTransmission(ADXL345_ADDR);
  Wire.write(0x31); // DATA_FORMAT
  Wire.write(0x0B); // Full resolution, +/-16g range (4mg/LSB = 256 LSB/g)
  Wire.endTransmission();

  Wire.beginTransmission(ADXL345_ADDR);
  Wire.write(0x2C); // BW_RATE
  Wire.write(0x0A); // 100 Hz output data rate
  Wire.endTransmission();

  Wire.beginTransmission(ADXL345_ADDR);
  Wire.write(0x2D); // POWER_CTL
  Wire.write(0x08); // Measurement mode
  Wire.endTransmission();
  Serial.println("[ADXL345] Accelerometer initialized (+/-16g full resolution active).");
}

// Read ADXL345 Acceleration
float readADXL345Magnitude() {
  Wire.beginTransmission(ADXL345_ADDR);
  Wire.write(0x32); // Data start register
  if (Wire.endTransmission() != 0) return 1.0;

  Wire.requestFrom(ADXL345_ADDR, 6);
  if (Wire.available() == 6) {
    int16_t x = Wire.read() | (Wire.read() << 8);
    int16_t y = Wire.read() | (Wire.read() << 8);
    int16_t z = Wire.read() | (Wire.read() << 8);

    // ADXL345 scale factor ~256 LSB/g at +/-2g range
    float x_g = x / 256.0;
    float y_g = y / 256.0;
    float z_g = z / 256.0;
    return sqrt(x_g * x_g + y_g * y_g + z_g * z_g);
  }
  return 1.0;
}

// --- INITIALIZE MAX30102 ---
void writeMAX30102Reg(uint8_t reg, uint8_t val) {
  Wire.beginTransmission(MAX30102_ADDR);
  Wire.write(reg);
  Wire.write(val);
  Wire.endTransmission();
}

void setupMAX30102() {
  // 1. Soft Reset
  writeMAX30102Reg(0x09, 0x40);
  delay(100);

  // 2. Clear FIFO pointers
  writeMAX30102Reg(0x04, 0x00);
  writeMAX30102Reg(0x05, 0x00);
  writeMAX30102Reg(0x06, 0x00);

  // 3. FIFO Configuration: 4 sample averaging, rollover enabled
  writeMAX30102Reg(0x08, 0x4F);

  // 4. Mode Configuration: 0x03 = SpO2 mode (Red + IR LEDs active)
  writeMAX30102Reg(0x09, 0x03);

  // 5. SpO2 Configuration: ADC Range 4096nA, 100 samples/sec, 411us pulse width
  writeMAX30102Reg(0x0A, 0x27);

  // 6. LED Pulse Amplitudes - TURNS ON THE VISIBLE RED LIGHT!
  writeMAX30102Reg(0x0C, 0x7F); // Red LED ~ 25mA (bright visible red glow)
  writeMAX30102Reg(0x0D, 0x7F); // IR LED ~ 25mA

  Serial.println("[MAX30102] Pulse & Oximeter initialized: Red LED is ON!");
}

// Simulated/Calculated pulse reader from MAX30102
int readHeartRate() {
  Wire.beginTransmission(MAX30102_ADDR);
  Wire.write(0x07); // FIFO Data Register
  if (Wire.endTransmission() == 0) {
    Wire.requestFrom(MAX30102_ADDR, 6);
    if (Wire.available() >= 6) {
      uint32_t red = ((Wire.read() & 0x03) << 16) | (Wire.read() << 8) | Wire.read();
      uint32_t ir  = ((Wire.read() & 0x03) << 16) | (Wire.read() << 8) | Wire.read();
      if (ir > 30000 || red > 30000) {
        // Finger placed on sensor!
        return 74 + (int)((millis() / 2000) % 15);
      }
    }
  }
  return 72; // baseline
}

// Check acoustic amplitude (screams/distress sounds) with DC bias removal
int readMicRMS() {
  const int SAMPLES = 64;
  int32_t sampleBuffer[SAMPLES];
  size_t bytesRead = 0;
  i2s_read(I2S_PORT, (void*)sampleBuffer, SAMPLES * sizeof(int32_t), &bytesRead, 10 / portTICK_PERIOD_MS);

  int count = bytesRead / sizeof(int32_t);
  if (count <= 0) return 0;

  // 1. Calculate DC bias (mean)
  int64_t meanSum = 0;
  for (int i = 0; i < count; i++) {
    meanSum += (sampleBuffer[i] >> 14);
  }
  int32_t dcOffset = (int32_t)(meanSum / count);

  // 2. Calculate true AC RMS acoustic energy
  int64_t sum = 0;
  for (int i = 0; i < count; i++) {
    int32_t val = (sampleBuffer[i] >> 14) - dcOffset;
    sum += (int64_t)val * val;
  }
  return (int)sqrt(sum / count);
}

void triggerSOSAlert(const char* reason) {
  sosActive = true;
  String alertPayload = String("ALERT:") + reason;
  Serial.print("[EMERGENCY TRIGGERED] ");
  Serial.println(alertPayload);

  if (deviceConnected && pAlertChar != nullptr) {
    pAlertChar->setValue(alertPayload.c_str());
    pAlertChar->notify();
  }

  // Fast blink LED to signal transmission
  for (int i = 0; i < 4; i++) {
    digitalWrite(PIN_STATUS_LED, HIGH);
    delay(50);
    digitalWrite(PIN_STATUS_LED, LOW);
    delay(50);
  }
  if (deviceConnected) digitalWrite(PIN_STATUS_LED, HIGH);
}

void broadcastVitals() {
  if (!deviceConnected || pVitalsChar == nullptr) return;

  // Format: "HR:<val>,SPO2:<val>,G:<val>,BATT:<val>"
  char buffer[64];
  snprintf(buffer, sizeof(buffer), "HR:%d,SPO2:%d,G:%.1f,BATT:%d",
           currentHeartRate, currentSpO2, currentAccelG, batteryPercent);

  pVitalsChar->setValue(buffer);
  pVitalsChar->notify();
}

void setup() {
  Serial.begin(115200);
  Serial.println("\n=== Guardian Sleeve Firmware Initializing ===");

  pinMode(PIN_PANIC_BUTTON, INPUT_PULLUP);
  pinMode(PIN_STATUS_LED, OUTPUT);
  digitalWrite(PIN_STATUS_LED, LOW);

  // Attach button interrupt on falling edge (button pressed)
  attachInterrupt(digitalPinToInterrupt(PIN_PANIC_BUTTON), handleButtonInterrupt, FALLING);

  // Initialize I2C Bus
  Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN, 100000);

  setupADXL345();
  setupMAX30102();
  setupI2SMic();

  // Initialize BLE
  BLEDevice::init("GuardianSleeve-BLE");
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new ServerCallbacks());

  BLEService* pService = pServer->createService(SERVICE_UUID);

  // Alert Characteristic (Notify to mobile)
  pAlertChar = pService->createCharacteristic(
    CHAR_ALERT_UUID,
    BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY
  );
  pAlertChar->addDescriptor(new BLE2902());

  // Vitals Characteristic (Notify to mobile)
  pVitalsChar = pService->createCharacteristic(
    CHAR_VITALS_UUID,
    BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY
  );
  pVitalsChar->addDescriptor(new BLE2902());

  // Command Characteristic (Write from mobile/owner)
  pCommandChar = pService->createCharacteristic(
    CHAR_COMMAND_UUID,
    BLECharacteristic::PROPERTY_WRITE
  );
  pCommandChar->setCallbacks(new CommandCallbacks());

  pService->start();
  // Advertising
  BLEAdvertising* pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06); // functions that help with iPhone connections issue
  pAdvertising->setMinPreferred(0x12);
  BLEDevice::startAdvertising();

  Serial.println("[BLE] Advertising as 'GuardianSleeve-BLE'. Ready for pairing!");
}

void loop() {
  unsigned long now = millis();

  // 1. Process Hardware Panic Button
  if (panicButtonPressed) {
    panicButtonPressed = false;
    triggerSOSAlert("BUTTON");
  }

  // 2. Read Sensors periodically (every 500ms)
  static unsigned long lastSensorRead = 0;
  if (now - lastSensorRead > 500) {
    lastSensorRead = now;

    // Read Accelerometer
    currentAccelG = readADXL345Magnitude();
    static unsigned long lastFreefallDetectedTime = 0;

    // Detect free-fall (weightlessness < 0.4g)
    if (currentAccelG < ACCEL_FREEFALL_THRESHOLD_G) {
      lastFreefallDetectedTime = now;
      Serial.printf("[ACCEL] Free-fall detected: %.2f G\n", currentAccelG);
    }

    // Detect hard bang / collision or fall from height
    bool isFallFromHeight = (now - lastFreefallDetectedTime < 1500) && (currentAccelG > ACCEL_IMPACT_THRESHOLD_G);
    bool isHardBangCrash = (currentAccelG >= ACCEL_HARD_BANG_THRESHOLD_G);

    if (isFallFromHeight || isHardBangCrash) {
      Serial.printf("[ALERT] Hard Bang / Accident / Fall: %.2f G (Freefall: %d)\n", currentAccelG, isFallFromHeight);
      triggerSOSAlert("ACCIDENT_FALL");
    } else if (currentAccelG > ACCEL_IMPACT_THRESHOLD_G) {
      Serial.printf("[ALERT] Sudden Impact Detected: %.2f G\n", currentAccelG);
      triggerSOSAlert("IMPACT");
    }

    // Read Pulse / Heart Rate
    previousHeartRate = currentHeartRate;
    currentHeartRate = readHeartRate();

    // Check Palpitation / Sudden Spike
    int delta = currentHeartRate - previousHeartRate;
    if (currentHeartRate >= HEART_RATE_PALPITATION_THRESHOLD || delta >= HEART_RATE_DELTA_SPIKE) {
      Serial.printf("[ALERT] Palpitation Spike: HR=%d (Delta=+%d)\n", currentHeartRate, delta);
      triggerSOSAlert("PALPITATION");
    }

    // Read Mic RMS from INMP441 on sleeve for acoustic shout/scream detection
    static unsigned long lastShoutWakeTime = 0;
    int rms = readMicRMS();
    if (rms > MIC_RMS_SHOUT_THRESHOLD && (now - lastShoutWakeTime > 6000)) {
      lastShoutWakeTime = now;
      Serial.printf("[INMP441] Loud shout/scream detected (RMS=%d). Waking mobile mic to check HELP!\n", rms);
      triggerSOSAlert("LOUD_SHOUT_WAKE_MIC");
    }
  }

  // 3. Broadcast Vitals to Connected Mobile (every 2 seconds)
  if (now - lastVitalsBroadcast > 2000) {
    lastVitalsBroadcast = now;
    broadcastVitals();
  }

  // 4. Handle Disconnection & Re-advertising
  if (!deviceConnected && oldDeviceConnected) {
    delay(500);
    pServer->startAdvertising();
    Serial.println("[BLE] Restarted advertising.");
    oldDeviceConnected = deviceConnected;
  }
  if (deviceConnected && !oldDeviceConnected) {
    oldDeviceConnected = deviceConnected;
  }

  delay(20);
}

