/*
 * Copyright 2025 The HR Streamer Project
 *
 * Licensed under the MIT License
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.wearable.hrstreamer

//import com.google.android.gms.wearable.MessageClient
//import com.google.android.gms.wearable.MessageEvent
//import com.google.android.gms.wearable.CapabilityClient
//import com.google.android.gms.wearable.CapabilityInfo

import android.annotation.SuppressLint
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import edu.ucsd.sccn.LSL
import java.io.IOException
import java.time.Instant

//import java.time.Instant
//import kotlinx.coroutines.Job

// Function to write heart rate and timestamp to a CSV file


/**
 * A state holder for the client data. Also interface the OnDataChangedListener
 */
class ClientDataViewModel :
    ViewModel(),
    DataClient.OnDataChangedListener {

    // Interfacing; not using DataLayerListenerService
    // commented out MessageClient and CapabilityClient cuz we don't use them in this app
    //MessageClient.OnMessageReceivedListener,
    //CapabilityClient.OnCapabilityChangedListener

    private val _events = mutableStateListOf<Event>()

    /**
     * The list of events from the clients.
     */
    //val events: List<Event> = _events

    /**
     * The currently captured image (if any), available to send to the wearable devices.
     */
    //var image by mutableStateOf<Bitmap?>(null)
    //    private set

    var heartrate by mutableStateOf<Float?>(null)
        private set
    var hrsendtime by mutableStateOf<Long?>(null)
        private set

    //private var loadHRJob: Job = Job().apply { complete() }

    //// LSL Outlet
    val LSL_OUTLET_NAME_HR = "HeartRate"
    val LSL_OUTLET_TYPE_HR = "DataLayer"
    // val LSL_OUTLET_CHANNELS_HR = 3 // One channel for HR data, one channel for sendtime, one channel for relaytime
    val LSL_OUTLET_CHANNELS_HR = 1 // One channel for HR data, one channel for sendtime, one channel for relaytime
    val LSL_OUTLET_NOMINAL_RATE_HR = LSL.IRREGULAR_RATE
    val LSL_OUTLET_CHANNEL_FORMAT_HR = LSL.ChannelFormat.int32
    val DEVICE_ID = "PixelWatch"
    var info_HR: LSL.StreamInfo? = null
    var outlet_HR: LSL.StreamOutlet? = null
    //var samples_HR: Array<Int?> = arrayOfNulls(1)
    var samples_HR = IntArray(1)

    private fun sendDataHR(data: Float?, timestamp: Long?) {
        if (data == null) return
        ensureOutlet()
        val outlet = outlet_HR ?: return
        samples_HR[0] = data.toInt()
        Log.d(TAG2, "Now sending HR:$data")

        //samples_HR[1] = (timestamp!! % 100000).toInt() // not able to send long format, so only truncate the lower 5 digits
        //Log.d(TAG2, "Now send_timestamp:$timestamp")

        //samples_HR[2] = (System.currentTimeMillis() % 100000).toInt()
        //samples_HR[2] = Instant.now().epochSecond.toInt()
        //Log.d(TAG2, "Now relay_timestamp_long:${Instant.now().toEpochMilli()}")

        try {
            /*final String dataString = Integer.toString(data);
            runOnUiThread(new Runnable(){
                @Override
                public void run(){
                    showMessage("Now sending HR: " + dataString);
                }
            });*/
            Log.d(TAG2, "Pushing samples_HR:$samples_HR")
            outlet.push_sample(samples_HR)
            //Thread.sleep(5);
        } catch (ex: java.lang.Exception) {
            //ex.message?.let { showMessage(it) }
            Log.e(TAG2, "Failed to push sample:")
            outlet_HR?.close()
            info_HR?.destroy()
            outlet_HR = null
            info_HR = null
        }


    }

    private fun ensureOutlet() {
        if (outlet_HR != null) return
        info_HR = LSL.StreamInfo(
            LSL_OUTLET_NAME_HR,
            LSL_OUTLET_TYPE_HR,
            LSL_OUTLET_CHANNELS_HR,
            LSL_OUTLET_NOMINAL_RATE_HR,
            LSL_OUTLET_CHANNEL_FORMAT_HR,
            DEVICE_ID
        )
        outlet_HR = try {
            Log.e(TAG2, "LSL outlet opened in Android View Model!!!")
            LSL.StreamOutlet(info_HR)
        } catch (ex: IOException) {
            Log.e(TAG2, "Unable to open LSL outlet. Have you added <uses-permission android:name=\"android.permission.INTERNET\" /> to your manifest file?")
            null
        }
    }

    /*
    private fun writeToCSV(context: Context, heartrate: Float, timestamp: Long) {
        val filename = "heart_rate_data.csv"

        // Get the path to the app's external files directory (or use internal storage for private data)
        val path = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)

        if (path != null) {
            val file = File(path, filename)

            // Open the file for writing
            try {
                val fileOutputStream = FileOutputStream(file, true) // true to append data
                val outputStreamWriter = OutputStreamWriter(fileOutputStream)
                val bufferedWriter = BufferedWriter(outputStreamWriter)

                // If the file is new, write the headers (optional)
                if (file.length() == 0L) {
                    bufferedWriter.write("HeartRate, Timestamp\n") // CSV Header
                }

                // Write the new data line
                bufferedWriter.write("$heartrate, $timestamp\n")
                bufferedWriter.close() // Don't forget to close the file
            } catch (e: Exception) {
                Log.e("CSV", "Error writing to CSV file: ${e.message}")
            }
        }
    }
*/
    @SuppressLint("VisibleForTests")
    override fun onDataChanged(dataEvents: DataEventBuffer) {

        //// LSL Outlet
        println(LSL.local_clock())

        ensureOutlet()


        _events.addAll(
            dataEvents.map { dataEvent ->
                val title = when (dataEvent.type) {
                    DataEvent.TYPE_CHANGED -> R.string.data_item_changed
                    DataEvent.TYPE_DELETED -> R.string.data_item_deleted
                    else -> R.string.data_item_unknown
                }

                Event(
                    title = title,
                    text = dataEvent.dataItem.toString()
                )
            }
        )

        // Do additional work for specific events
        dataEvents.forEach { dataEvent ->
            when (dataEvent.type) {
                DataEvent.TYPE_CHANGED -> {
                    when (dataEvent.dataItem.uri.path) {

                            HR_PATH -> {
                                heartrate = DataMapItem.fromDataItem(dataEvent.dataItem)
                                        .dataMap
                                        .getFloat(HR_KEY)

                                hrsendtime = DataMapItem.fromDataItem(dataEvent.dataItem)
                                    .dataMap
                                    .getLong(HR_TIME_KEY)

                                Log.d(TAG1, "HR $heartrate, $hrsendtime, ${Instant.now().toEpochMilli()}")

                                sendDataHR(heartrate, hrsendtime)

                            }

                    }
                }
            }
        }


    }

    companion object {
        private const val TAG1 = "WSAndroidViewModel"
        private const val TAG2 = "LSL"
        const val HR_PATH = "/hr"
        const val HR_KEY = "hr"
        const val HR_TIME_KEY = "hr_time"
    }

    /*
    override fun onMessageReceived(messageEvent: MessageEvent) {
        _events.add(
            Event(
                title = R.string.message_from_watch,
                text = messageEvent.toString()
            )
        )
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        _events.add(
            Event(
                title = R.string.capability_changed,
                text = capabilityInfo.toString()
            )
        )
    }

    fun onPictureTaken(bitmap: Bitmap?) {
        image = bitmap ?: return
    }*/

}

/**
 * A data holder describing a client event.
 */
data class Event(
    @StringRes val title: Int,
    val text: String
)
