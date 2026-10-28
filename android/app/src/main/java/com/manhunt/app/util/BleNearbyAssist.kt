package com.manhunt.app.util

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.Charset
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Optional BLE nearby assist for MANHNT.
 *
 * Hiders advertise: M|<joinCode>|<playerId>
 * Seekers scan and surface nearby player IDs.
 *
 * Tagging never depends on BLE — code/QR + Firebase remain the authority.
 */
class BleNearbyAssist(private val appContext: Context) {

    data class NearbyPlayer(
        val playerId: String,
        val joinCode: String,
        val lastSeenEpochMs: Long,
        val rssi: Int
    )

    private val tag = "BleNearbyAssist"

    val serviceUuid: UUID = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8")

    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null
    private var isAdvertising = false
    private var isScanning = false
    private var activeJoinCode: String = ""

    private val nearbyMap = ConcurrentHashMap<String, NearbyPlayer>()
    private val _nearbyPlayers = MutableStateFlow<List<NearbyPlayer>>(emptyList())
    val nearbyPlayers: StateFlow<List<NearbyPlayer>> = _nearbyPlayers.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun hasBleHardware(): Boolean =
        appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

    fun hasRuntimePermissions(): Boolean {
        return requiredPermissions().all {
            ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }
    }

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun startAdvertising(joinCode: String, playerId: String) {
        if (!hasBleHardware()) {
            _statusMessage.value = "Bluetooth LE not available"
            return
        }
        if (!hasRuntimePermissions()) {
            _statusMessage.value = "Bluetooth permission needed"
            return
        }
        if (!isBluetoothEnabled()) {
            _statusMessage.value = "Turn on Bluetooth for nearby tags"
            return
        }
        val adv = adapter?.bluetoothLeAdvertiser
        if (adv == null) {
            _statusMessage.value = "BLE advertising not supported"
            return
        }
        if (isAdvertising) return

        val payload = buildPayload(joinCode, playerId)
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(false)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(serviceUuid))
            .addServiceData(ParcelUuid(serviceUuid), payload)
            .build()

        try {
            adv.startAdvertising(settings, data, advertiseCallback)
            advertiser = adv
            isAdvertising = true
            _statusMessage.value = "Nearby broadcast on"
            Log.d(tag, "Advertising started")
        } catch (e: Exception) {
            Log.w(tag, "startAdvertising failed", e)
            _statusMessage.value = "Could not start Bluetooth broadcast"
            isAdvertising = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        if (!isAdvertising) return
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (_: Exception) {
        }
        isAdvertising = false
        advertiser = null
        Log.d(tag, "Advertising stopped")
    }

    @SuppressLint("MissingPermission")
    fun startScanning(expectedJoinCode: String) {
        if (!hasBleHardware()) {
            _statusMessage.value = "Bluetooth LE not available"
            return
        }
        if (!hasRuntimePermissions()) {
            _statusMessage.value = "Bluetooth permission needed"
            return
        }
        if (!isBluetoothEnabled()) {
            _statusMessage.value = "Turn on Bluetooth for nearby detection"
            return
        }
        val sc = adapter?.bluetoothLeScanner
        if (sc == null) {
            _statusMessage.value = "BLE scanning not supported"
            return
        }
        if (isScanning) return

        activeJoinCode = expectedJoinCode.trim().uppercase()
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(serviceUuid))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            sc.startScan(listOf(filter), settings, scanCallback)
            scanner = sc
            isScanning = true
            _statusMessage.value = "Scanning for nearby runners"
            Log.d(tag, "Scanning started")
        } catch (e: Exception) {
            Log.w(tag, "startScanning failed", e)
            _statusMessage.value = "Could not start Bluetooth scan"
            isScanning = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (!isScanning) return
        try {
            scanner?.stopScan(scanCallback)
        } catch (_: Exception) {
        }
        isScanning = false
        scanner = null
        Log.d(tag, "Scanning stopped")
    }

    fun stopAll() {
        stopAdvertising()
        stopScanning()
        nearbyMap.clear()
        _nearbyPlayers.value = emptyList()
        _statusMessage.value = null
    }

    fun pruneStale(maxAgeMs: Long = 12_000L) {
        val now = System.currentTimeMillis()
        var changed = false
        val it = nearbyMap.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (now - e.value.lastSeenEpochMs > maxAgeMs) {
                it.remove()
                changed = true
            }
        }
        if (changed) {
            _nearbyPlayers.value = nearbyMap.values.sortedByDescending { it.rssi }
        }
    }

    private fun buildPayload(joinCode: String, playerId: String): ByteArray {
        val code = joinCode.trim().uppercase().take(8)
        val id = playerId.trim().take(16)
        return "M|$code|$id".toByteArray(Charset.forName("US-ASCII"))
    }

    private fun parsePayload(bytes: ByteArray?): Triple<String, String, String>? {
        if (bytes == null || bytes.isEmpty()) return null
        val text = try {
            String(bytes, Charset.forName("US-ASCII"))
        } catch (_: Exception) {
            return null
        }
        val parts = text.split("|")
        if (parts.size < 3) return null
        if (parts[0] != "M" && parts[0] != "MANHNT") return null
        val join = parts[1].trim().uppercase()
        val player = parts[2].trim()
        if (join.isEmpty() || player.isEmpty()) return null
        return Triple(parts[0], join, player)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            isAdvertising = true
        }

        override fun onStartFailure(errorCode: Int) {
            isAdvertising = false
            _statusMessage.value = "Bluetooth advertise failed ($errorCode)"
            Log.w(tag, "Advertise failed code=$errorCode")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            if (result == null) return
            val record = result.scanRecord ?: return
            val data = record.getServiceData(ParcelUuid(serviceUuid))
            val parsed = parsePayload(data) ?: return
            val join = parsed.second
            val playerId = parsed.third
            if (activeJoinCode.isNotEmpty() && join != activeJoinCode) return
            nearbyMap[playerId] = NearbyPlayer(
                playerId = playerId,
                joinCode = join,
                lastSeenEpochMs = System.currentTimeMillis(),
                rssi = result.rssi
            )
            _nearbyPlayers.value = nearbyMap.values.sortedByDescending { it.rssi }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            _statusMessage.value = "Bluetooth scan failed ($errorCode)"
            Log.w(tag, "Scan failed code=$errorCode")
        }
    }
}
