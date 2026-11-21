package com.vita.vbot.demo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider

class DemoActivity : ComponentActivity() {
    private lateinit var session: DemoSession
    private var permittedBleAction: (() -> Unit)? = null

    private val configPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) session.importAortaConfig(uri)
    }
    private val blePermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val action = permittedBleAction
        permittedBleAction = null
        if (result.values.all { it }) action?.invoke()
        else session.permissionDenied()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        session = ViewModelProvider(this)[DemoSession::class.java]
        setContent {
            DemoScreen(
                session,
                importConfig = { configPicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                scanBle = { withBlePermissions(session::scanBle) },
                connectBle = { serial, address -> withBlePermissions { session.connectBle(serial, address) } },
            )
        }
    }

    override fun onStop() {
        session.stopJog()
        super.onStop()
    }

    private fun withBlePermissions(action: () -> Unit) {
        val permissions = if (Build.VERSION.SDK_INT >= 31) arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        ) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) action()
        else {
            permittedBleAction = action
            blePermissions.launch(missing.toTypedArray())
        }
    }
}
