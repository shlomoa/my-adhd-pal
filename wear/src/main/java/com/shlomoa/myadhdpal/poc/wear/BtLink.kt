package com.shlomoa.myadhdpal.poc.wear

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.SystemClock
import com.shlomoa.myadhdpal.poc.core.BtService
import com.shlomoa.myadhdpal.poc.core.Exchange
import com.shlomoa.myadhdpal.poc.core.Frame
import com.shlomoa.myadhdpal.poc.core.LineBuffer
import com.shlomoa.myadhdpal.poc.core.OffsetTracker
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors

/**
 * The direct Bluetooth (RFCOMM) path to the phone, which proves "via Bluetooth" (the Data Layer may use Wi-Fi).
 *
 * A worker thread connects to the paired phone (phones first among the bonded devices), reads the phone's pongs,
 * and reconnects when the link drops. [send] queues a write on a single-thread executor so the sensor callback never
 * blocks on Bluetooth flow control.
 */
object BtLink {
    private val uuid: UUID = UUID.fromString(BtService.UUID_STRING)
    private val tracker = OffsetTracker()
    private val writer = Executors.newSingleThreadExecutor()

    @Volatile private var socket: BluetoothSocket? = null

    @Volatile private var running = false

    @Volatile var connectedTo: String = ""
        private set

    @Volatile var lastError: String = ""
        private set

    val offsetNanos: Long get() = tracker.offset()
    val connected: Boolean get() = socket?.isConnected == true

    @Synchronized
    fun start(context: Context, log: (String) -> Unit) {
        if (running) return
        running = true
        tracker.clear()
        Thread({ loop(context.applicationContext, log) }, "bt-link").start()
    }

    @Synchronized
    fun stop() {
        running = false
        runCatching { socket?.close() }
        socket = null
        connectedTo = ""
    }

    fun send(bytes: ByteArray) {
        val s = socket ?: return
        writer.execute {
            try {
                s.outputStream.write(bytes)
                s.outputStream.flush()
            } catch (e: IOException) {
                lastError = "write: ${e.message}"
                runCatching { s.close() }
            }
        }
    }

    @SuppressLint("MissingPermission") // BLUETOOTH_CONNECT is granted by the test script (pm grant) and logged
    private fun loop(context: Context, log: (String) -> Unit) {
        val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null) {
            lastError = "no Bluetooth adapter"
            log("bt: $lastError")
            running = false
            return
        }
        while (running) {
            val candidates = try {
                adapter.bondedDevices.sortedBy { if (isPhone(it)) 0 else 1 }
            } catch (e: SecurityException) {
                lastError = "bondedDevices: ${e.message}"
                log("bt: $lastError")
                emptyList()
            }
            for (device in candidates) {
                if (!running) break
                if (connect(device, log)) readLoop(context, log)
            }
            if (running) Thread.sleep(RETRY_MS)
        }
    }

    @SuppressLint("MissingPermission")
    // No cancelDiscovery(): this app never starts discovery, and the call would need BLUETOOTH_SCAN.
    private fun connect(device: BluetoothDevice, log: (String) -> Unit): Boolean {
        val name = runCatching { device.name }.getOrNull() ?: device.address
        return try {
            val s = device.createRfcommSocketToServiceRecord(uuid)
            s.connect()
            socket = s
            connectedTo = name
            lastError = ""
            log("bt: connected to $name")
            true
        } catch (e: IOException) {
            lastError = "connect $name: ${e.message}"
            log("bt: $lastError")
            false
        } catch (e: SecurityException) {
            lastError = "connect $name: ${e.message}"
            log("bt: $lastError")
            false
        }
    }

    private fun readLoop(context: Context, log: (String) -> Unit) {
        val s = socket ?: return
        val buffer = LineBuffer()
        val bytes = ByteArray(READ_BUFFER)
        try {
            while (running) {
                val n = s.inputStream.read(bytes)
                if (n < 0) break
                val arrival = SystemClock.elapsedRealtimeNanos()
                for (frame in buffer.feed(bytes, n)) {
                    if (frame is Frame.PongFrame) {
                        tracker.add(Exchange(frame.pingWatchNanos, frame.phoneNanos, arrival))
                        Exchanges.log(context, "bt", frame.pingWatchNanos, frame.phoneNanos, arrival)
                    }
                }
            }
        } catch (e: IOException) {
            lastError = "read: ${e.message}"
        }
        log("bt: link closed ($lastError)")
        runCatching { s.close() }
        socket = null
        connectedTo = ""
    }

    @SuppressLint("MissingPermission")
    private fun isPhone(device: BluetoothDevice): Boolean =
        runCatching { device.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.PHONE }.getOrDefault(false)

    private const val RETRY_MS = 2000L
    private const val READ_BUFFER = 512
}
