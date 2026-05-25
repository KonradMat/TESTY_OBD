package com.obdreader.app.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.core.app.ActivityCompat
import com.obdreader.app.viewmodel.ObdViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ObdViewModel by viewModels {
        androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory(application)
    }

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    private val enableBtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            Toast.makeText(this, "Bluetooth jest wymagany!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestBluetoothPermissions()
        setContent {
            ObdAppTheme {
                AppRoot(viewModel = viewModel)
            }
        }
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
            if (permissions.any {
                    ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
                })
                ActivityCompat.requestPermissions(this, permissions, 1001)
        } else {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            if (permissions.any {
                    ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
                })
                ActivityCompat.requestPermissions(this, permissions, 1001)
        }
        if (bluetoothAdapter?.isEnabled == false)
            enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }
}

// ─────────────────────────────────────────────────────────────
// THEME
// ─────────────────────────────────────────────────────────────

val DarkBackground = Color(0xFF0A0E1A)
val CardBackground = Color(0xFF111827)
val AccentGreen    = Color(0xFF00FF88)
val AccentBlue     = Color(0xFF00B4FF)
val AccentOrange   = Color(0xFFFF6B35)
val AccentRed      = Color(0xFFFF3B5C)
val TextPrimary    = Color(0xFFE8F0FE)
val TextSecondary  = Color(0xFF8899AA)

@Composable
fun ObdAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background   = DarkBackground,
            surface      = CardBackground,
            primary      = AccentGreen,
            secondary    = AccentBlue,
            error        = AccentRed,
            onBackground = TextPrimary,
            onSurface    = TextPrimary
        ),
        content = content
    )
}

// ─────────────────────────────────────────────────────────────
// APP ROOT
// ─────────────────────────────────────────────────────────────

@Composable
fun AppRoot(viewModel: ObdViewModel) {
    val authPassed    by viewModel.authPassed.collectAsState()
    val isAuthLoading by viewModel.isAuthLoading.collectAsState()
    val authError     by viewModel.authError.collectAsState()
    val isGuest       by viewModel.isGuest.collectAsState()

    AnimatedContent(
        targetState = authPassed,
        transitionSpec = {
            if (targetState)
                (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut())
            else
                (slideInHorizontally { -it } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
        },
        label = "auth_transition"
    ) { passed ->
        if (!passed) {
            AuthScreen(
                isLoading       = isAuthLoading,
                errorMessage    = authError,
                onLogin         = { email, password ->
                    viewModel.clearAuthError()
                    viewModel.login(email, password)
                },
                onRegister      = { email, firstName, lastName, password ->
                    viewModel.clearAuthError()
                    viewModel.register(email, firstName, lastName, password)
                },
                onGuestContinue = { viewModel.continueAsGuest() }
            )
        } else {
            ObdMainScreen(
                viewModel        = viewModel,
                isGuest          = isGuest,
                onConnectRequest = { device -> viewModel.connect(device) }
            )
        }
    }
}