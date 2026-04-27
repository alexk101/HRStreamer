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

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Manages Wearable clients to showcase the [DataClient], [MessageClient], [CapabilityClient]
 *
 * This activity allows the user to launch the companion wear activity via the [MessageClient].
 *
 * While resumed, this activity also logs all interactions across the clients, which includes events
 * sent from this activity and from the watch(es).
 */
@SuppressLint("VisibleForTests")
class MainActivity : ComponentActivity() {

    private val dataClient by lazy { Wearable.getDataClient(this) }
    private val messageClient by lazy { Wearable.getMessageClient(this) }
    private val capabilityClient by lazy { Wearable.getCapabilityClient(this) }

    private val clientDataViewModel by viewModels<ClientDataViewModel>()
    private var isStreaming by mutableStateOf(false)
    private var lastForegroundWriteTimestamp: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                    MainApp(
                        hr = clientDataViewModel.heartrate,
                        hrtime = clientDataViewModel.hrsendtime,
                        isStreaming = isStreaming,
                        onStartWearableActivityClick = ::startWearableActivity,
                        onToggleStreamingClick = ::toggleStreaming
                    )
            }

            writeToCSV(clientDataViewModel.heartrate, clientDataViewModel.hrsendtime)
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        /*
        // Observe heart rate data changes in ViewModel
        clientDataViewModel.heartrate?.let { heartrate ->
            clientDataViewModel.hrsendtime?.let { hrsendtime ->
                // Write data to CSV when heart rate or timestamp is updated
                writeToCSV(heartrate, hrsendtime)
            }
        }*/

    }

    private fun writeToCSV(heartrate: Float?, timestamp: Long?) {

        if (heartrate == null || timestamp == null) {
            Log.e("CSV", "Heart rate or timestamp is null. Data will not be written.")
            return
        }
        if (lastForegroundWriteTimestamp == timestamp) {
            // Compose recomposition can call this repeatedly; only write once/sample timestamp.
            return
        }
        lastForegroundWriteTimestamp = timestamp

        val filename = "hr_data.csv"
        val context = application // Context is needed to access app storage

        // Get the path to the app's external files directory (or use internal storage for private data)
        val path = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)

        if (path != null) {
            val file = File(path, filename)
            Log.d("CSV", "File path: ${file.absolutePath}")

            // Open the file for writing
            try {
                // Check if file exists, if not, create a new file with headers
                val fileOutputStream = FileOutputStream(file, true) // true to append data
                val outputStreamWriter = OutputStreamWriter(fileOutputStream)
                val bufferedWriter = BufferedWriter(outputStreamWriter)

                // If the file is new, write the headers (optional)
                if (file.length() == 0L) {
                    bufferedWriter.write("Timestamp, HeartRate\n") // CSV Header
                    //Toast.makeText(this, "Writing headers to file", Toast.LENGTH_SHORT).show()
                    Log.d("CSV", "Writing headers to file")
                }

                // Write the new data line
                bufferedWriter.write("$timestamp, $heartrate\n")
                bufferedWriter.close() // Don't forget to close the file
                Log.d("CSV", "Data written to file successfully")
            } catch (e: Exception) {
                Log.e("CSV", "Error writing to CSV file: ${e.message}")
            }
        } else {
            Log.e("CSV", "Path is null, cannot write to file")
        }
    }

    /*
    private fun writeToCSV(heartrate: Float, timestamp: Long) {
        val filename = "heart_rate_data.csv"

        // Get the path to the app's external files directory (or use internal storage for private data)
        val path = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)

        Log.d("CSV", "File path: $path")

        if (path != null) {
            val file = File(path, filename)

            try {
                // Open the file for writing
                val fileOutputStream = FileOutputStream(file, true) // true to append data
                val outputStreamWriter = OutputStreamWriter(fileOutputStream)
                val bufferedWriter = BufferedWriter(outputStreamWriter)

                // If the file is new, write the headers (optional)
                if (file.length() == 0L) {
                    Log.d("CSV", "Creating new file")
                    bufferedWriter.write("HeartRate, Timestamp\n") // CSV Header
                }

                // Write the new data line
                Log.d("CSV", "Saving to file $heartrate, $timestamp")
                Toast.makeText(this, "Saving HR data", Toast.LENGTH_SHORT).show()
                bufferedWriter.write("$heartrate, $timestamp\n")
                bufferedWriter.close() // Close the file
            } catch (e: Exception) {
                Log.e("CSV", "Error writing to CSV file: ${e.message}")
                Toast.makeText(this, "Error writing to CSV file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
*/
    override fun onResume() {
        super.onResume()
        if (isStreaming) {
            // Foreground mode: activity owns ingestion/writing.
            stopService(HrStreamingService.stopIntent(this))
            dataClient.addListener(clientDataViewModel)
        }
        /*messageClient.addListener(clientDataViewModel)
        capabilityClient.addListener(
            clientDataViewModel,
            Uri.parse("wear://"),
            CapabilityClient.FILTER_REACHABLE
        )

        if (isCameraSupported) {
            lifecycleScope.launch {
                try {
                    capabilityClient.addLocalCapability(CAMERA_CAPABILITY).await()
                } catch (cancellationException: CancellationException) {
                    throw cancellationException
                } catch (exception: Exception) {
                    Log.e(TAG, "Could not add capability: $exception")
                }
            }
        }*/
    }

    override fun onPause() {
        super.onPause()
        if (isStreaming) {
            // Background mode: service owns ingestion/writing.
            dataClient.removeListener(clientDataViewModel)
            val startIntent = HrStreamingService.startIntent(this)
            startForegroundService(startIntent)
        }
        //messageClient.removeListener(clientDataViewModel)
        //capabilityClient.removeListener(clientDataViewModel)
    }

    private fun toggleStreaming() {
        isStreaming = !isStreaming
        if (isStreaming) {
            // User can only toggle from foreground activity, so keep service off.
            stopService(HrStreamingService.stopIntent(this))
            dataClient.addListener(clientDataViewModel)
        } else {
            dataClient.removeListener(clientDataViewModel)
            stopService(HrStreamingService.stopIntent(this))
        }
    }

    private fun startWearableActivity() {
        lifecycleScope.launch {
            try {
                val nodes = capabilityClient
                    .getCapability(WEAR_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                    .await()
                    .nodes

                // Send a message to all nodes in parallel
                nodes.map { node ->
                    async {
                        messageClient.sendMessage(node.id, START_ACTIVITY_PATH, byteArrayOf())
                            .await()
                    }
                }.awaitAll()

                Log.d(TAG, "Starting activity requests sent successfully")
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (exception: Exception) {
                Log.d(TAG, "Starting activity failed: $exception")
            }
        }
    }


    companion object {
        private const val TAG = "AndroidMainActivity"
        private const val START_ACTIVITY_PATH = "/start-activity"
        private const val WEAR_CAPABILITY = "wear"

    }

}
