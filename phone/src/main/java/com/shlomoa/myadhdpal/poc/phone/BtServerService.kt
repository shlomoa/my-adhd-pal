package com.shlomoa.myadhdpal.poc.phone

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import com.shlomoa.myadhdpal.poc.core.BtService
import com.shlomoa.myadhdpal.poc.core.Frame
import com.shlomoa.myadhdpal.poc.core.Frames
import com.shlomoa.myadhdpal.poc.core.LineBuffer
import java.io.IOException
import java.util.UUID

/**
 * Direct Bluetooth (RFCOMM) path: listens for the watch, logs every reading that arrives (path `bt`) and answers its
 * clock pings. Runs as a foreground service so it stays up while the phone screen is off.
 */
class BtServerService : Service() {
    @Volatile private var running = false

    @Volatile private var serverSocket: android.bluetooth.BluetoothServerSocket? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "POC", NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("Pulse POC: listening for the watch")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
        try {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } catch (e: SecurityException) {
            PhoneState.event(this, "startForeground refused: ${e.message}")
        }
        if (!running) {
            running = true
            Thread({ acceptLoop() }, "bt-server").start()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        runCatching { serverSocket?.close() }
        super.onDestroy()
    }

    @SuppressLint("MissingPermission") // BLUETOOTH_CONNECT is granted by the test script (pm grant) and logged
    private fun acceptLoop() {
        val adapter: BluetoothAdapter? = getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null) {
            PhoneState.event(this, "no Bluetooth adapter")
            return
        }
        while (running) {
            try {
                val server = adapter.listenUsingRfcommWithServiceRecord(
                    BtService.NAME,
                    UUID.fromString(BtService.UUID_STRING)
                )
                serverSocket = server
                PhoneState.event(this, "listening")
                val socket = server.accept() // blocks until the watch connects
                server.close()
                PhoneState.event(this, "watch connected: ${socket.remoteDevice?.address}")
                serve(socket)
                PhoneState.event(this, "watch disconnected")
            } catch (e: IOException) {
                if (running) PhoneState.event(this, "accept failed: ${e.message}")
            } catch (e: SecurityException) {
                PhoneState.event(this, "no Bluetooth permission: ${e.message}")
                Thread.sleep(RETRY_MS)
            }
        }
    }

    private fun serve(socket: BluetoothSocket) {
        val buffer = LineBuffer()
        val bytes = ByteArray(READ_BUFFER)
        try {
            while (running) {
                val n = socket.inputStream.read(bytes)
                if (n < 0) break
                val arrival = SystemClock.elapsedRealtimeNanos()
                for (frame in buffer.feed(bytes, n)) handle(frame, arrival, socket)
            }
        } catch (e: IOException) {
            PhoneState.event(this, "read ended: ${e.message}")
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun handle(frame: Frame, arrivalNanos: Long, socket: BluetoothSocket) {
        when (frame) {
            is Frame.SampleFrame -> PhoneState.record(this, "bt", frame.sample, arrivalNanos)

            is Frame.PingFrame -> {
                socket.outputStream.write(Frames.pong(frame.watchNanos, SystemClock.elapsedRealtimeNanos()))
                socket.outputStream.flush()
            }

            is Frame.PongFrame -> Unit

            is Frame.BadFrame -> PhoneState.event(this, "bad frame: ${frame.line}")
        }
    }

    private companion object {
        const val CHANNEL = "poc"
        const val NOTIFICATION_ID = 6001
        const val RETRY_MS = 2000L
        const val READ_BUFFER = 512
    }
}
