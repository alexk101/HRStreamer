package com.example.android.wearable.hrstreamer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import edu.ucsd.sccn.LSL
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.time.Instant

class HrStreamingService : Service(), DataClient.OnDataChangedListener {
    private val dataClient by lazy { Wearable.getDataClient(this) }

    private var infoHR: LSL.StreamInfo? = null
    private var outletHR: LSL.StreamOutlet? = null
    private val sampleHR = IntArray(1)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START, null -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                dataClient.addListener(this)
                Log.d(TAG, "Foreground HR streaming started")
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        dataClient.removeListener(this)
        outletHR?.close()
        infoHR?.destroy()
        outletHR = null
        infoHR = null
        super.onDestroy()
        Log.d(TAG, "Foreground HR streaming stopped")
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        ensureOutlet()
        dataEvents.forEach { dataEvent ->
            if (dataEvent.type == DataEvent.TYPE_CHANGED && dataEvent.dataItem.uri.path == HR_PATH) {
                val dataMap = DataMapItem.fromDataItem(dataEvent.dataItem).dataMap
                val hr = dataMap.getFloat(HR_KEY)
                val timestamp = dataMap.getLong(HR_TIME_KEY)
                Log.d(TAG, "HR $hr, $timestamp, ${Instant.now().toEpochMilli()}")
                writeToCsv(hr, timestamp)
                pushToLsl(hr)
            }
        }
    }

    private fun ensureOutlet() {
        if (outletHR != null) return
        try {
            infoHR = LSL.StreamInfo(
                LSL_OUTLET_NAME_HR,
                LSL_OUTLET_TYPE_HR,
                LSL_OUTLET_CHANNELS_HR,
                LSL_OUTLET_NOMINAL_RATE_HR,
                LSL_OUTLET_CHANNEL_FORMAT_HR,
                DEVICE_ID
            )
            outletHR = LSL.StreamOutlet(infoHR)
            Log.d(TAG, "LSL outlet opened")
        } catch (exception: IOException) {
            Log.e(TAG, "Unable to open LSL outlet", exception)
        }
    }

    private fun pushToLsl(hr: Float) {
        val outlet = outletHR ?: return
        sampleHR[0] = hr.toInt()
        try {
            outlet.push_sample(sampleHR)
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to push sample", exception)
        }
    }

    private fun writeToCsv(heartrate: Float, timestamp: Long) {
        val path = applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: return
        val file = File(path, CSV_FILENAME)
        try {
            val fileOutputStream = FileOutputStream(file, true)
            val outputStreamWriter = OutputStreamWriter(fileOutputStream)
            val bufferedWriter = BufferedWriter(outputStreamWriter)
            if (file.length() == 0L) {
                bufferedWriter.write("Timestamp, HeartRate\n")
            }
            bufferedWriter.write("$timestamp, $heartrate\n")
            bufferedWriter.close()
        } catch (exception: Exception) {
            Log.e(TAG, "Error writing CSV", exception)
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "HR Streaming",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("HR Streamer running")
            .setContentText("Recording watch heart rate in background")
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "HrStreamingService"
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_CHANNEL_ID = "hr_streaming_channel"
        private const val CSV_FILENAME = "hr_data.csv"
        private const val HR_PATH = "/hr"
        private const val HR_KEY = "hr"
        private const val HR_TIME_KEY = "hr_time"
        private const val LSL_OUTLET_NAME_HR = "HeartRate"
        private const val LSL_OUTLET_TYPE_HR = "DataLayer"
        private const val LSL_OUTLET_CHANNELS_HR = 1
        private const val LSL_OUTLET_NOMINAL_RATE_HR = LSL.IRREGULAR_RATE
        private val LSL_OUTLET_CHANNEL_FORMAT_HR = LSL.ChannelFormat.int32
        private const val DEVICE_ID = "PixelWatch"

        const val ACTION_START = "com.example.android.wearable.hrstreamer.action.START_STREAMING"
        const val ACTION_STOP = "com.example.android.wearable.hrstreamer.action.STOP_STREAMING"

        fun startIntent(context: Context): Intent {
            return Intent(context, HrStreamingService::class.java).setAction(ACTION_START)
        }

        fun stopIntent(context: Context): Intent {
            return Intent(context, HrStreamingService::class.java).setAction(ACTION_STOP)
        }
    }
}
