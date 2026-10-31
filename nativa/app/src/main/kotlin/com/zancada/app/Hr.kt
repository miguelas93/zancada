package com.zancada.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import java.util.UUID

/**
 * Pulsómetro por Bluetooth (banda de pecho o reloj que emita el pulso): el servicio estándar
 * «Heart Rate» (0x180D). Solo se lee el pulso; no se guarda nada en el dispositivo.
 */
object HrBle {
    val SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    val MEASURE: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** Permisos que hacen falta (Android 12+: buscar y conectar; antes, la ubicación que ya se pide para correr). */
    fun perms(): Array<String> = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    fun allowed(ctx: Context) = perms().all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }

    fun adapterOn(ctx: Context) = runCatching { ctx.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true }.getOrDefault(false)

    /** Pulso de una lectura «Heart Rate Measurement» (8 o 16 bits según el primer byte). */
    fun parse(v: ByteArray?): Int? {
        if (v == null || v.size < 2) return null
        val bpm = if (v[0].toInt() and 1 == 0) v[1].toInt() and 0xFF
            else if (v.size >= 3) (v[1].toInt() and 0xFF) or ((v[2].toInt() and 0xFF) shl 8) else return null
        return bpm.takeIf { it in 30..240 }
    }
}

data class HrFound(val addr: String, val name: String)

/** Busca pulsómetros cerca durante unos segundos. */
@SuppressLint("MissingPermission")   // se comprueba con HrBle.allowed antes de usar el Bluetooth
class HrScan(private val ctx: Context, private val onFound: (HrFound) -> Unit, private val onEnd: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val seen = mutableSetOf<String>()
    private var running = false
    private val cb = object : ScanCallback() {
        override fun onScanResult(type: Int, r: ScanResult) {
            val a = r.device.address ?: return
            if (!seen.add(a)) return
            val n = runCatching { r.device.name }.getOrNull() ?: r.scanRecord?.deviceName ?: "Pulsómetro"
            main.post { onFound(HrFound(a, n.take(30))) }
        }
        override fun onScanFailed(errorCode: Int) { main.post { stop() } }
    }

    fun start(seconds: Int = 12): Boolean {
        if (!HrBle.allowed(ctx)) return false
        val sc = ctx.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner ?: return false
        val f = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(HrBle.SERVICE)).build())
        val s = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        if (runCatching { sc.startScan(f, s, cb) }.isFailure) return false
        running = true
        main.postDelayed({ stop() }, seconds * 1000L)
        return true
    }

    fun stop() {
        if (!running) return
        running = false
        main.removeCallbacksAndMessages(null)
        if (HrBle.allowed(ctx)) runCatching { ctx.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner?.stopScan(cb) }
        onEnd()
    }
}

/**
 * Conexión al pulsómetro durante la carrera. Se reconecta solo si se pierde la señal.
 * Pulso medio y máximo: solo con la carrera en marcha (no en pausa).
 */
@SuppressLint("MissingPermission")   // se comprueba con HrBle.allowed antes de usar el Bluetooth
class HrMonitor(private val ctx: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var addr: String? = null
    private var on = false
    @Volatile private var last = 0
    @Volatile private var lastAt = 0L
    var paused = false
    private var sum = 0L
    private var n = 0
    var max = 0; private set

    /** Pulso actual (null si no llega nada desde hace 5 s). */
    fun now(t: Long): Int? = if (on && last > 0 && t - lastAt < 5000) last else null
    fun avg(): Int? = if (n >= 30) (sum / n).toInt() else null

    fun start(address: String?) {
        if (on || address.isNullOrBlank() || !HrBle.allowed(ctx)) return
        addr = address; on = true; sum = 0; n = 0; max = 0; last = 0
        connect()
    }

    private fun connect() {
        if (!on) return
        val ad = ctx.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        val dev: BluetoothDevice = runCatching { ad.getRemoteDevice(addr) }.getOrNull() ?: return
        gatt = runCatching { dev.connectGatt(ctx, false, cb, BluetoothDevice.TRANSPORT_LE) }.getOrNull()
    }

    private fun retry() {
        runCatching { gatt?.close() }; gatt = null
        if (on) main.postDelayed({ connect() }, 4000)
    }

    /** Cada lectura (una por segundo, más o menos). */
    private fun reading(bpm: Int?) {
        bpm ?: return
        last = bpm; lastAt = System.currentTimeMillis()
        if (!paused) { sum += bpm; n++; if (bpm > max) max = bpm }
    }

    private val cb = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, state: Int) {
            if (state == BluetoothProfile.STATE_CONNECTED) runCatching { g.discoverServices() }
            else if (state == BluetoothProfile.STATE_DISCONNECTED) main.post { retry() }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val ch = g.getService(HrBle.SERVICE)?.getCharacteristic(HrBle.MEASURE) ?: return
            runCatching {
                g.setCharacteristicNotification(ch, true)
                val d = ch.getDescriptor(HrBle.CCCD) ?: return@runCatching
                if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                else {
                    @Suppress("DEPRECATION") d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION") g.writeDescriptor(d)
                }
            }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) {
            if (ch.uuid == HrBle.MEASURE) reading(HrBle.parse(value))
        }

        @Deprecated("Android 12 y anteriores")
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            if (ch.uuid == HrBle.MEASURE) reading(HrBle.parse(ch.value))
        }
    }

    fun stop() {
        on = false
        main.removeCallbacksAndMessages(null)
        if (HrBle.allowed(ctx)) runCatching { gatt?.disconnect(); gatt?.close() }
        gatt = null; last = 0
    }
}
