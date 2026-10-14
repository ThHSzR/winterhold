package br.eng.thsouza.winterhold

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        requestInitialBatterySetup()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        CentralService.start(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestInitialBatterySetup()
        }
        setContent { WinterholdApp(DemoCentral.store) }
    }

    private fun requestInitialBatterySetup() {
        val preferences = getSharedPreferences("central", MODE_PRIVATE)
        if (!CentralService.batteryExempt(this) && !preferences.getBoolean("batteryPromptShown", false)) {
            preferences.edit().putBoolean("batteryPromptShown", true).apply()
            CentralService.requestBatterySetup(this)
        }
    }
}
