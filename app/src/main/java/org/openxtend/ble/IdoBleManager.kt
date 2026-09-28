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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.openxtend.model.ConnectionStatus
import org.openxtend.model.WatchInfo
import java.util.LinkedList
import java.util.Queue

@SuppressLint("MissingPermission")
class IdoBleManager(private val context: Context) {

    companion object {
        private const val TAG = "IdoBleManager"
        private const val PREFS_NAME = "openxtend_prefs"
        private const val KEY_LAST_DEVICE = "last_device_address"
        private const val KEY_PAIRED_PREFIX = "is_paired_"
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharNormal: BluetoothGattCharacteristic? = null
    private var notifyCharNormal: BluetoothGattCharacteristic? = null
    private var writeCharBulk: BluetoothGattCharacteristic? = null
    private var notifyCharBulk: BluetoothGattCharacteristic? = null

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _watchInfo = MutableStateFlow(WatchInfo())
    val watchInfo: StateFlow<WatchInfo> = _watchInfo.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices.asStateFlow()

    private val writeQueue: Queue<ByteArray> = LinkedList()
    private var isWriting = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var autoReconnectJob: Job? = null
    private var isManualDisconnect = false

    // Event listener for incoming events like Find Phone & Voice Assistant
    var onFindPhoneRequested: (() -> Unit)? = null
    var onVoiceAssistantTriggered: ((key: Int, payload: ByteArray) -> Unit)? = null
    var onStatusMessage: ((String) -> Unit)? = null

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
        autoReconnectJob?.cancel()
        isManualDisconnect = false
        _connectionStatus.value = ConnectionStatus.CONNECTING
        Log.i(TAG, "Connecting to ${device.name ?: "Watch"} [${device.address}]")

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LAST_DEVICE, device.address).apply()

        val isPaired = prefs.getBoolean(KEY_PAIRED_PREFIX + device.address, false)
        _watchInfo.value = _watchInfo.value.copy(
            deviceName = device.name ?: "boAt Xtend",
            deviceAddress = device.address,
            isPaired = isPaired
        )

        bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    fun disconnect() {
        isManualDisconnect = true
        stopKeepAlive()
        autoReconnectJob?.cancel()
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
                    Log.i(TAG, "GATT Connected. Establishing connection parameters and discovering services...")
                    _connectionStatus.value = ConnectionStatus.CONNECTING
                    gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_BALANCED)
                    scope.launch {
                        delay(250)
                        gatt.discoverServices()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.w(TAG, "GATT Disconnected (status: $status)")
                    stopKeepAlive()
                    _connectionStatus.value = ConnectionStatus.DISCONNECTED
                    writeQueue.clear()
                    isWriting = false

                    // Auto-reconnect if not manual disconnect
                    if (!isManualDisconnect) {
                        scheduleAutoReconnect()
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(IdoGattAttributes.SERVICE_UUID)
                if (service != null) {
                    writeCharNormal = service.getCharacteristic(IdoGattAttributes.CHAR_WRITE_NORMAL)
                    notifyCharNormal = service.getCharacteristic(IdoGattAttributes.CHAR_NOTIFY_NORMAL)
                    writeCharBulk = service.getCharacteristic(IdoGattAttributes.CHAR_WRITE_BULK)
                    notifyCharBulk = service.getCharacteristic(IdoGattAttributes.CHAR_NOTIFY_BULK)

                    Log.i(TAG, "IDO Service & Characteristics discovered! Enabling normal notifications...")
                    enableNotifications(gatt, notifyCharNormal)
                } else {
                    Log.e(TAG, "IDO primary service 0x0AF0 not found!")
                }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (descriptor.characteristic.uuid == IdoGattAttributes.CHAR_NOTIFY_NORMAL && notifyCharBulk != null) {
                    Log.i(TAG, "CHAR_NOTIFY_NORMAL enabled, now enabling CHAR_NOTIFY_BULK...")
                    enableNotifications(gatt, notifyCharBulk)
                } else {
                    Log.i(TAG, "CCCD Notifications enabled! Connected & ready.")
                    _connectionStatus.value = ConnectionStatus.CONNECTED

                    // Initial queries & start continuous keepalive sync
                    scope.launch {
                        delay(300)
                        syncWatch()
                        startKeepAlive()
                    }
                }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            isWriting = false
            processQueue()
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncomingPacket(value)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val data = characteristic.value ?: return
            handleIncomingPacket(data)
        }
    }

    private var keepAliveJob: Job? = null

    private fun startKeepAlive() {
        stopKeepAlive()
        keepAliveJob = scope.launch {
            while (isActive && _connectionStatus.value == ConnectionStatus.CONNECTED) {
                delay(12000)
                if (_connectionStatus.value == ConnectionStatus.CONNECTED) {
                    enqueueCommand(IdoPacketEncoder.buildGetLiveData())
                    enqueueCommand(IdoPacketEncoder.buildGetLiveActivity())
                    enqueueCommand(IdoPacketEncoder.buildGetHeartRate())
                }
            }
        }
    }

    private fun stopKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = null
    }

    private fun scheduleAutoReconnect() {
        autoReconnectJob?.cancel()
        autoReconnectJob = scope.launch {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastAddr = prefs.getString(KEY_LAST_DEVICE, null) ?: return@launch

            while (isActive && _connectionStatus.value == ConnectionStatus.DISCONNECTED && !isManualDisconnect) {
                delay(4000)
                if (_connectionStatus.value == ConnectionStatus.DISCONNECTED && !isManualDisconnect) {
                    val device = bluetoothAdapter?.getRemoteDevice(lastAddr)
                    if (device != null) {
                        Log.i(TAG, "Auto-reconnecting to $lastAddr...")
                        connect(device)
                        break
                    }
                }
            }
        }
    }

    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic?) {
        if (characteristic == null) return
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(IdoGattAttributes.CLIENT_CHARACTERISTIC_CONFIG)
        if (descriptor != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            }
        }
    }

    private fun handleIncomingPacket(data: ByteArray) {
        Log.d(TAG, "Incoming packet (${data.size} B): ${data.joinToString(" ") { "%02X".format(it) }}")
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
            is IdoPacketDecoder.DecodeResult.BindResult -> {
                if (result.success) {
                    _watchInfo.value = current.copy(
                        isPaired = true,
                        lastSyncStatus = "Paired & Bonded!"
                    )
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().putBoolean(KEY_PAIRED_PREFIX + current.deviceAddress, true).apply()
                    onStatusMessage?.invoke("Watch Paired Successfully!")
                    syncWatch()
                }
            }
            is IdoPacketDecoder.DecodeResult.TimeSyncAck -> {
                _watchInfo.value = current.copy(lastSyncStatus = "Time Synchronized!")
                onStatusMessage?.invoke("Time Synchronized!")
            }
            is IdoPacketDecoder.DecodeResult.FindPhoneTriggered -> {
                onFindPhoneRequested?.invoke()
            }
            is IdoPacketDecoder.DecodeResult.DataUpdateNotify -> {
                Log.d(TAG, "Watch reported DataUpdateNotify: sending ACK and refreshing live telemetry")
                enqueueCommand(byteArrayOf(0x07, 0x40, 0x00))
                enqueueCommand(IdoPacketEncoder.buildGetLiveData())
                enqueueCommand(IdoPacketEncoder.buildGetLiveActivity())
                enqueueCommand(IdoPacketEncoder.buildGetHeartRate())
            }
            is IdoPacketDecoder.DecodeResult.VoiceAssistantTriggered -> {
                Log.i(TAG, "Voice Assistant triggered on watch! Key: 0x${"%02X".format(result.key)}")
                enqueueCommand(IdoPacketEncoder.buildAlexaVoiceAck(result.key))
                onVoiceAssistantTriggered?.invoke(result.key, result.payload)
                _watchInfo.value = current.copy(lastSyncStatus = "Watch Mic / Alexa Active")
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

            isWriting = true
            val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(char, nextPacket, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
            } else {
                char.value = nextPacket
                gatt.writeCharacteristic(char)
            }

            if (!success) {
                Log.w(TAG, "GATT writeCharacteristic returned false, clearing write lock")
                isWriting = false
                scope.launch {
                    delay(60)
                    processQueue()
                }
            }
        }
    }

    /**
     * Pair/Bind Watch: Prompts checkmark on the boAt Xtend screen.
     */
    fun pairWatch() {
        Log.i(TAG, "Sending BIND_START (04 01 F1...)")
        enqueueCommand(IdoPacketEncoder.buildBindStart())
        _watchInfo.value = _watchInfo.value.copy(lastSyncStatus = "Pairing prompt sent to watch screen...")
    }

    /**
     * Unbind watch
     */
    fun unbindWatch() {
        enqueueCommand(IdoPacketEncoder.buildUnbind())
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PAIRED_PREFIX + _watchInfo.value.deviceAddress, false).apply()
        _watchInfo.value = _watchInfo.value.copy(isPaired = false)
    }

    /**
     * Sync routine: Time, continuous HR, weather switch, Alexa ready handshake, device info, battery, steps, live activity, heart rate
     */
    fun syncWatch() {
        _watchInfo.value = _watchInfo.value.copy(
            lastSyncEpoch = System.currentTimeMillis(),
            lastSyncStatus = "Syncing..."
        )
        enqueueCommand(IdoPacketEncoder.buildSetTime())
        enqueueCommand(IdoPacketEncoder.buildSetContinuousHeartRate(true))
        enqueueCommand(IdoPacketEncoder.buildSetWeatherSwitch(true))
        enqueueCommand(IdoPacketEncoder.buildSetAlexaVoiceState(true))
        enqueueCommand(IdoPacketEncoder.buildSetAlexaReady())
        enqueueCommand(IdoPacketEncoder.buildGetDeviceInfo())
        enqueueCommand(IdoPacketEncoder.buildGetBatteryInfo())
        enqueueCommand(IdoPacketEncoder.buildGetLiveActivity())
        enqueueCommand(IdoPacketEncoder.buildGetLiveData())
        enqueueCommand(IdoPacketEncoder.buildGetHeartRate())
    }

    fun setRaiseToWake(enabled: Boolean) {
        enqueueCommand(IdoPacketEncoder.buildSetRaiseToWake(enabled))
    }

    fun setMusicControl(enabled: Boolean) {
        enqueueCommand(IdoPacketEncoder.buildSetMusicControl(enabled))
    }

    fun setWeatherSwitch(enabled: Boolean) {
        enqueueCommand(IdoPacketEncoder.buildSetWeatherSwitch(enabled))
    }

    fun pushWeather(
        tempC: Int,
        maxC: Int,
        minC: Int,
        weatherType: Int = 1,
        humidity: Int = 50,
        cityName: String = "Delhi",
        day1Type: Int = weatherType,
        day1Max: Int = maxC,
        day1Min: Int = minC,
        day2Type: Int = weatherType,
        day2Max: Int = maxC,
        day2Min: Int = minC,
        day3Type: Int = weatherType,
        day3Max: Int = maxC,
        day3Min: Int = minC
    ) {
        // Ensure watch weather switch is active
        enqueueCommand(IdoPacketEncoder.buildSetWeatherSwitch(true))
        // Send 18-byte weather data packet (today + 3 days forecast)
        enqueueCommand(
            IdoPacketEncoder.buildWeatherDataPacket(
                currentTempC = tempC,
                maxTempC = maxC,
                minTempC = minC,
                weatherType = weatherType,
                humidity = humidity,
                day1Type = day1Type,
                day1Max = day1Max,
                day1Min = day1Min,
                day2Type = day2Type,
                day2Max = day2Max,
                day2Min = day2Min,
                day3Type = day3Type,
                day3Max = day3Max,
                day3Min = day3Min
            )
        )
        // Send 20-byte city packet required by IDO firmware to display weather
        enqueueCommand(IdoPacketEncoder.buildWeatherCityPacket(cityName))
        _watchInfo.value = _watchInfo.value.copy(
            lastWeatherSummary = "$cityName: $tempC°C (H:$maxC° L:$minC°)"
        )
    }

    /**
     * Push AI / Voice assistant response to the watch.
     * Uses IDO Alexa protocol frames (0x13 0x01) to beam text to the watch voice assistant screen.
     * Also pushes a generic system notification (typeId = 1, NEVER WhatsApp typeId = 8!)
     */
    fun pushVoiceResponse(replyText: String) {
        // 1. Send IDO Alexa voice reply frames (0x13 0x01)
        val voiceFrames = IdoPacketEncoder.buildVoiceAssistantResponse(replyText)
        voiceFrames.forEach { enqueueCommand(it) }

        // 2. Push as generic system notification (typeId = 1) - NEVER WhatsApp (typeId = 8)!
        pushNotification(
            typeId = 1,
            sender = "Gemini AI",
            message = replyText
        )
    }

    fun setContinuousHeartRate(enabled: Boolean) {
        enqueueCommand(IdoPacketEncoder.buildSetContinuousHeartRate(enabled))
    }

    fun findPhone() {
        enqueueCommand(IdoPacketEncoder.buildFindPhone(30))
    }

    /**
     * Find Watch: Sends vibration command (03 21) AND triggers alert screen
     */
    fun findWatch() {
        enqueueCommand(IdoPacketEncoder.buildFindWatch())
        enqueueCommand(IdoPacketEncoder.buildCallAlert("FIND WATCH"))
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
