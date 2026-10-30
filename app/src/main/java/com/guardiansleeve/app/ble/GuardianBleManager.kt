package com.guardiansleeve.app.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import com.guardiansleeve.app.model.VitalsData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

@SuppressLint("MissingPermission")
class GuardianBleManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothGatt: BluetoothGatt? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _vitals = MutableStateFlow(VitalsData())
    val vitals: StateFlow<VitalsData> = _vitals.asStateFlow()

    private val _incomingAlert = MutableStateFlow<String?>(null)
    val incomingAlert: StateFlow<String?> = _incomingAlert.asStateFlow()

    private var alertCharacteristic: BluetoothGattCharacteristic? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isScanning = false

    fun startScanAndConnect() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.w(TAG, "Bluetooth not enabled or not supported.")
            return
        }

        val scanner = bluetoothAdapter.bluetoothLeScanner ?: return
        if (isScanning) return

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        isScanning = true
        Log.d(TAG, "Starting BLE scan for Guardian Sleeve...")
        scanner.startScan(listOf(filter), settings, scanCallback)

        // Stop scan after 15 seconds if not found
        mainHandler.postDelayed({
            if (isScanning) {
                scanner.stopScan(scanCallback)
                isScanning = false
                Log.d(TAG, "BLE scan timeout.")
            }
        }, 15000)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                val name = device.name ?: "Unknown"
                Log.d(TAG, "Found BLE Device: $name [${device.address}]")
                if (name.contains("GuardianSleeve", ignoreCase = true) || name == "GuardianSleeve-BLE") {
                    bluetoothAdapter?.bluetoothLeScanner?.stopScan(this)
                    isScanning = false
                    connectToDevice(device)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error: $errorCode")
            isScanning = false
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        Log.d(TAG, "Connecting to GATT on ${device.address}...")
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to Guardian Sleeve BLE GATT. Discovering services...")
                _isConnected.value = true
                gatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w(TAG, "Disconnected from Guardian Sleeve BLE GATT.")
                _isConnected.value = false
                bluetoothGatt?.close()
                bluetoothGatt = null
                // Attempt auto-reconnect
                mainHandler.postDelayed({ startScanAndConnect() }, 3000)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt?.getService(SERVICE_UUID)
                if (service != null) {
                    alertCharacteristic = service.getCharacteristic(CHAR_ALERT_UUID)
                    commandCharacteristic = service.getCharacteristic(CHAR_COMMAND_UUID)
                    val vitalsChar = service.getCharacteristic(CHAR_VITALS_UUID)

                    // Enable notifications for Alert and Vitals
                    enableNotification(gatt, alertCharacteristic)
                    enableNotification(gatt, vitalsChar)
                    Log.i(TAG, "GATT Services and Characteristics configured!")
                }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            characteristic?.let { char ->
                val uuid = char.uuid
                val value = char.getStringValue(0) ?: ""

                if (uuid == CHAR_ALERT_UUID) {
                    Log.i(TAG, "🚨 BLE Alert Received: $value")
                    _incomingAlert.value = value
                } else if (uuid == CHAR_VITALS_UUID) {
                    parseVitalsPayload(value)
                }
            }
        }
    }

    private fun enableNotification(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
        if (gatt == null || characteristic == null) return
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
        if (descriptor != null) {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }
    }

    private fun parseVitalsPayload(payload: String) {
        try {
            // Format: "HR:135,SPO2:98,G:1.2,BATT:88"
            var hr = _vitals.value.heartRate
            var spo2 = _vitals.value.spO2
            var g = _vitals.value.accelerationG
            var batt = _vitals.value.batteryPercent

            val tokens = payload.split(",")
            for (token in tokens) {
                val kv = token.split(":")
                if (kv.size == 2) {
                    when (kv[0].trim().uppercase()) {
                        "HR" -> hr = kv[1].toIntOrNull() ?: hr
                        "SPO2" -> spo2 = kv[1].toIntOrNull() ?: spo2
                        "G" -> g = kv[1].toFloatOrNull() ?: g
                        "BATT" -> batt = kv[1].toIntOrNull() ?: batt
                    }
                }
            }

            val isPalpitation = hr >= 130
            val isImpact = g >= 3.5f

            _vitals.value = VitalsData(
                heartRate = hr,
                spO2 = spo2,
                accelerationG = g,
                batteryPercent = batt,
                isPalpitationDetected = isPalpitation,
                isImpactDetected = isImpact,
                lastUpdatedTimestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse vitals: ${e.message}")
        }
    }

    fun sendDisarmCommand() {
        val char = commandCharacteristic ?: return
        val gatt = bluetoothGatt ?: return
        char.value = "CMD:DISARM".toByteArray()
        gatt.writeCharacteristic(char)
        Log.d(TAG, "Sent CMD:DISARM to sleeve.")
    }

    fun clearAlert() {
        _incomingAlert.value = null
    }

    companion object {
        private const val TAG = "GuardianBleManager"
        val SERVICE_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        val CHAR_ALERT_UUID: UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
        val CHAR_VITALS_UUID: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")
        val CHAR_COMMAND_UUID: UUID = UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb")
    }
}
