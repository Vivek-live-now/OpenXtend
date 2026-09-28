package org.openxtend.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openxtend.model.ConnectionStatus
import org.openxtend.model.WatchInfo
import java.util.LinkedList
import java.util.Queue

@SuppressLint("MissingPermission")
class IdoBleManager(private val context: Context) {

    companion object {
        private const val TAG = "IdoBleManager"
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharNormal: BluetoothGattCharacteristic? = null
    private var notifyCharNormal: BluetoothGattCharacteristic? = null
    private var writeCharBulk: BluetoothGattCharacteristic? = null

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _watchInfo = MutableStateFlow(WatchInfo())
    val watchInfo: StateFlow<WatchInfo> = _watchInfo.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices.asStateFlow()

    private val writeQueue: Queue<ByteArray> = LinkedList()
    private var isWriting = false
    private val scope = CoroutineScope(Dispatchers.IO)

    // Event listener for incoming events like Find Phone
    var onFindPhoneRequested: (() -> Unit)? = null

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: ""
            if (name.contains("Xtend", ignoreCase = true) ||
                name.contains("ID206", ignoreCase = true) ||
                name.contains("boAt", ignoreCase = true) ||
                name.contains("VeryFit", ignoreCase = true)) {
                
                val current = _discoveredDevices.value.toMutableList()
                if (current.none { it.address == device.address }) {
                    current.add(device)
                    _discoveredDevices.value = current
                    Log.d(TAG, "Found target watch: $name (${device.address})")
                }
            }
        }
    }

    fun startScan() {
        if (bluetoothAdapter?.isEnabled != true) {
            Log.w(TAG, "Bluetooth is disabled")
            return
        }

        _discoveredDevices.value = emptyList()
        _connectionStatus.value = ConnectionStatus.SCANNING

        val scanner = bluetoothAdapter.bluetoothLeScanner ?: return
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(IdoGattAttributes.SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            // Scan with and without filter in case device advertisement hides service UUID
            scanner.startScan(listOf(filter), settings, scanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start BLE scan", e)
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
        }
    }

    fun stopScan() {
        if (_connectionStatus.value == ConnectionStatus.SCANNING) {
            try {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping scan", e)
            }
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
        }
    }

    fun connect(device: BluetoothDevice) {
        stopScan()
        _connectionStatus.value = ConnectionStatus.CONNECTING
        Log.i(TAG, "Connecting to ${device.name ?: "Watch"} [${device.address}]")

        bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        writeQueue.clear()
        isWriting = false
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "GATT Connected. Requesting MTU and discovering services...")
                    _connectionStatus.value = ConnectionStatus.CONNECTING
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        gatt.requestMtu(512)
                    } else {
                        gatt.discoverServices()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.w(TAG, "GATT Disconnected")
                    _connectionStatus.value = ConnectionStatus.DISCONNECTED
                    writeQueue.clear()
                    isWriting = false
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "MTU changed to $mtu, discovering services...")
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(IdoGattAttributes.SERVICE_UUID)
                if (service != null) {
                    writeCharNormal = service.getCharacteristic(IdoGattAttributes.CHAR_WRITE_NORMAL)
                    notifyCharNormal = service.getCharacteristic(IdoGattAttributes.CHAR_NOTIFY_NORMAL)
                    writeCharBulk = service.getCharacteristic(IdoGattAttributes.CHAR_WRITE_BULK)

                    Log.i(TAG, "IDO Service & Characteristics discovered! Enabling notifications...")
                    enableNotifications(gatt, notifyCharNormal)
                } else {
                    Log.e(TAG, "IDO primary service 0x0AF0 not found!")
                }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "CCCD Notifications enabled! Connected & ready.")
                _connectionStatus.value = ConnectionStatus.CONNECTED

                // Trigger initial sync pipeline
                scope.launch {
                    syncWatch()
                }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            isWriting = false
            processQueue()
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val data = characteristic.value ?: return
            handleIncomingPacket(data)
        }
    }

    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic?) {
        if (characteristic == null) return
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(IdoGattAttributes.CLIENT_CHARACTERISTIC_CONFIG)
        if (descriptor != null) {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }
    }

    private fun handleIncomingPacket(data: ByteArray) {
        val result = IdoPacketDecoder.decodePacket(data, _watchInfo.value)
        val current = _watchInfo.value

        when (result) {
            is IdoPacketDecoder.DecodeResult.DeviceInfoUpdate -> {
                _watchInfo.value = current.copy(
                    deviceId = result.deviceId,
                    firmwareVersion = result.firmwareVer,
                    batteryPercent = result.batteryPercent,
                    isCharging = result.isCharging,
                    isLowPower = result.isLowPower
                )
            }
            is IdoPacketDecoder.DecodeResult.BatteryUpdate -> {
                _watchInfo.value = current.copy(
                    batteryMv = result.voltageMv,
                    batteryPercent = result.batteryPercent,
                    isCharging = result.isCharging,
                    isLowPower = result.isLowPower
                )
            }
            is IdoPacketDecoder.DecodeResult.LiveDataUpdate -> {
                _watchInfo.value = current.copy(
                    liveSteps = result.steps,
                    liveHeartRate = result.heartRate
                )
            }
            is IdoPacketDecoder.DecodeResult.FindPhoneTriggered -> {
                onFindPhoneRequested?.invoke()
            }
            else -> {}
        }
    }

    fun enqueueCommand(packet: ByteArray) {
        synchronized(writeQueue) {
            writeQueue.add(packet)
            if (!isWriting) {
                processQueue()
            }
        }
    }

    private fun processQueue() {
        synchronized(writeQueue) {
            if (writeQueue.isEmpty() || isWriting) return
            val nextPacket = writeQueue.poll() ?: return
            val gatt = bluetoothGatt ?: return
            val char = writeCharNormal ?: return

            char.value = nextPacket
            isWriting = true
            gatt.writeCharacteristic(char)
        }
    }

    /**
     * Initial sync routine: sync time, query info, query battery, query steps
     */
    fun syncWatch() {
        enqueueCommand(IdoPacketEncoder.buildSetTime())
        enqueueCommand(IdoPacketEncoder.buildGetDeviceInfo())
        enqueueCommand(IdoPacketEncoder.buildGetBatteryInfo())
        enqueueCommand(IdoPacketEncoder.buildGetLiveData())
        _watchInfo.value = _watchInfo.value.copy(lastSyncEpoch = System.currentTimeMillis())
    }

    fun setRaiseToWake(enabled: Boolean) {
        enqueueCommand(IdoPacketEncoder.buildSetRaiseToWake(enabled))
    }

    fun setMusicControl(enabled: Boolean) {
        enqueueCommand(IdoPacketEncoder.buildSetMusicControl(enabled))
    }

    fun findPhone() {
        enqueueCommand(IdoPacketEncoder.buildFindPhone(30))
    }

    fun rebootWatch() {
        enqueueCommand(IdoPacketEncoder.buildReboot())
    }

    fun pushCallAlert(callerName: String) {
        enqueueCommand(IdoPacketEncoder.buildCallAlert(callerName))
    }

    fun dismissCall() {
        enqueueCommand(IdoPacketEncoder.buildCallEnd())
    }

    fun pushNotification(typeId: Int, sender: String, message: String) {
        val chunks = IdoPacketEncoder.buildNotificationPackets(typeId, sender, message)
        chunks.forEach { enqueueCommand(it) }
    }
}
