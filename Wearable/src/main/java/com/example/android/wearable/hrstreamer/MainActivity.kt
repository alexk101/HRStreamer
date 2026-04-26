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

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.mutualmobile.composesensors.rememberHeartRateSensorState
import com.mutualmobile.composesensors.rememberLightSensorState
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {

    private val dataClient by lazy { Wearable.getDataClient(this) }
    //private val messageClient by lazy { Wearable.getMessageClient(this) }
    private val capabilityClient by lazy { Wearable.getCapabilityClient(this) }

    private val clientDataViewModel by viewModels<ClientDataViewModel>()

    private val isBodySensorsPermissionGranted: Boolean
        get() {
            return checkSelfPermission(Manifest.permission.BODY_SENSORS) ==
                PackageManager.PERMISSION_GRANTED
        }

    private val hr: Pair<Float?, Long>
        @Composable
        get() = getHR(isBodySensorsPermissionGranted = isBodySensorsPermissionGranted)

    fun navigateToAppInfo() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null))
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MainApp(
                events = clientDataViewModel.events,
                isBodySensorsPermissionGranted = isBodySensorsPermissionGranted,
                navigateToAppInfo = ::navigateToAppInfo,
                hr = hr.first!!
            )

            sendHR(hr.first, hr.second) //send to Android device

            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // Set screen always on


        }

    }

    override fun onResume() {
        super.onResume()
        dataClient.addListener(clientDataViewModel)
        //messageClient.addListener(clientDataViewModel)
    }

    override fun onPause() {
        super.onPause()
        dataClient.removeListener(clientDataViewModel)
        //messageClient.removeListener(clientDataViewModel)
        //capabilityClient.removeListener(clientDataViewModel)
    }

    private fun sendHR(hr: Float?, time: Long?) {
        lifecycleScope.launch {
            try {
                val request = PutDataMapRequest.create(HR_PATH).apply {
                    dataMap.putFloat(HR_KEY, hr!!)
                    //dataMap.putLong(HR_TIME_KEY, Instant.now().toEpochMilli()) //System.currentTimeMillis()) , Instant.now().epochSecond
                    dataMap.putLong(
                        HR_TIME_KEY,
                        time!!
                    ) //System.currentTimeMillis()) , Instant.now().epochSecond
                }
                    .asPutDataRequest()
                    .setUrgent()

                val result = dataClient.putDataItem(request).await()

                Log.d(TAG, "HR $hr, $time")
                //Log.d(TAG, "HR DataItem saved: $result")
                //Log.d(TAG, "HR_TIME_KEY: ${Instant.now().atZone(ZoneId.of("Asia/Tokyo"))}")
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (exception: Exception) {
                Log.d(TAG, "HR Saving DataItem failed: $exception")
            }
        }
    }

    /*
    private fun sendLight(light: Float?) {
        lifecycleScope.launch {
            try {
                val request = PutDataMapRequest.create(LIGHT_PATH).apply {
                    dataMap.putFloat(LIGHT_KEY, light!!)
                    dataMap.putLong(LIGHT_TIME_KEY, Instant.now().epochSecond)
                    //Log.d(TAG, "LIGHT_TIME_KEY: ${Instant.now().atZone(ZoneId.of("Asia/Tokyo"))}")
                    //${Instant.ofEpochSecond(hrtime).atZone(ZoneId.of(timezone)).toLocalDateTime()}
                }
                    .asPutDataRequest()
                    .setUrgent()

                val result = dataClient.putDataItem(request).await()

                Log.d(TAG, "LIGHT: $light")
                Log.d(TAG, "LIGHT DataItem saved: $result")

            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (exception: Exception) {
                Log.d(TAG, "LIGHT Saving DataItem failed: $exception")
            }
        }

    }*/

    companion object {
        private const val TAG = "WearMainActivity"
        private const val HR_PATH = "/hr"
        private const val HR_KEY = "hr"
        private const val HR_TIME_KEY = "hr_time"
        //private const val LIGHT_PATH = "/light"
        //private const val LIGHT_KEY = "light"
        //private const val LIGHT_TIME_KEY = "light_time"
    }
}


@Composable
fun getHR(isBodySensorsPermissionGranted: Boolean): Pair<Float?, Long> {
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.observeAsState()
    var isPermissionGranted: Boolean? by remember { mutableStateOf(null) }
    var hasStartedListening by remember { mutableStateOf(false) }
    var hr: Float? by remember { mutableStateOf(null) }
    val hr_time: Long?


    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            isPermissionGranted = isGranted
        }
    )

    val heartRateSensorState = rememberHeartRateSensorState(autoStart = false)

    // Request permission when app returns to foreground.
    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.Event.ON_RESUME) {
            isPermissionGranted = isBodySensorsPermissionGranted
            hasStartedListening = false
            if (isPermissionGranted != true) {
                permissionLauncher.launch(Manifest.permission.BODY_SENSORS)
            }
        }
    }

    // Start sensor stream immediately after permission is granted.
    LaunchedEffect(isPermissionGranted, hasStartedListening) {
        if (isPermissionGranted == true && !hasStartedListening) {
            heartRateSensorState.startListening()
            hasStartedListening = true
        }
    }

    hr = heartRateSensorState.heartRate
    hr_time = Instant.now().toEpochMilli()

    return Pair(hr, hr_time)

}

