package io.github.doubletwist13.orbit

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val vm get() = (application as OrbitApplication).controller

    override fun onStart() {
        super.onStart()
        // opening the app is a request for fresh status; the client's short cache keeps repeat opens cheap
        if (vm.state.value.loggedIn) vm.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 13+ hides the service's notification without this; the bridge still works if it is declined.
        val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        startForegroundService(Intent(this, BridgeService::class.java))
        enableEdgeToEdge(SystemBarStyle.dark(TRANSPARENT), SystemBarStyle.dark(TRANSPARENT))
        setContent {
            OrbitTheme {
                val s by vm.state.collectAsStateWithLifecycle()
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.safeDrawingPadding().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("> Orbit for Lucid", style = MaterialTheme.typography.headlineMedium)
                        if (!s.loggedIn) Login(s.busy != null, vm::login) else Status(s, vm)
                        s.busy?.let { Text("> $it...") }
                        s.message?.let { Text("! $it", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Login(busy: Boolean, onLogin: (String, String) -> Unit) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    OutlinedTextField(user, { user = it }, label = { Text("Lucid email") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
    OutlinedTextField(pass, { pass = it }, label = { Text("Password") }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
    Button({ onLogin(user, pass); pass = "" }, enabled = !busy && user.isNotBlank() && pass.isNotBlank()) {
        Text("Log in")
    }
}

@Composable
private fun Status(s: UiState, vm: Controller) {
    val v = s.status
    val idle = s.busy == null
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    if (v != null) {
        Text("${v.nickname}  ${v.socPercent}%", style = MaterialTheme.typography.titleLarge)
        Text("${when (v.locked) { true -> "Locked"; false -> "Unlocked"; null -> "Lock unknown" }} · ${v.chargeState} · ${if (v.awake) "awake" else "asleep"}",
            color = dim)
        Text("Climate ${if (v.hvacOn) "on" else "off"}", color = dim)
        // ticks so the ages keep counting between state changes
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(30_000)
                now = System.currentTimeMillis()
            }
        }
        val clock = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
        fun at(ms: Long) = clock.format(ms) + " (${agoText(ms, now)})"
        Text("Car data ${at(v.updatedMs)}", color = dim)
        Text("Lucid asked ${at(v.fetchedMs)}", color = dim)
        s.pushAtMs?.let { Text("Watch ${if (s.pushOk) "received" else "send failed"} ${at(it)}", color = dim) }
        Text("Orbit ${vm.version} · watch app ${s.watchVersion ?: "--"}", color = dim)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(vm::refresh, enabled = idle) { Text("Refresh") }
        OutlinedButton(vm::logout, enabled = idle) { Text("Log out") }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Switch(s.pushOn, vm::setPush)
        Text("Keep the watch up to date", color = dim)
    }
}
