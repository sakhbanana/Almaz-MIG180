package com.almaz.mig180.tacho.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.almaz.mig180.tacho.ui.theme.TextSecondary
import com.almaz.mig180.tacho.vm.TachoViewModel

private const val SCREEN_MEASURE = 0
private const val SCREEN_CALIBRATION = 1
private const val SCREEN_LOG = 2

@Composable
fun TachoApp(viewModel: TachoViewModel = viewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    var denied by rememberSaveable { mutableStateOf(false) }
    var requested by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result ->
        granted = result
        denied = !result
    }

    LaunchedEffect(Unit) {
        if (!granted && !requested) {
            requested = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Разрешение могли выдать в системных настройках, пока мы были в фоне.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val now = context.hasCameraPermission()
                granted = now
                if (now) denied = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!granted) {
        CameraPermissionScreen(
            denied = denied,
            onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
            },
        )
        return
    }

    var screen by rememberSaveable { mutableIntStateOf(SCREEN_MEASURE) }
    BackHandler(enabled = screen != SCREEN_MEASURE) { screen = SCREEN_MEASURE }

    when (screen) {
        SCREEN_CALIBRATION -> CalibrationScreen(viewModel) { screen = SCREEN_MEASURE }
        SCREEN_LOG -> LogScreen(viewModel) { screen = SCREEN_MEASURE }
        else -> MeasureScreen(
            viewModel = viewModel,
            onOpenCalibration = { screen = SCREEN_CALIBRATION },
            onOpenLog = { screen = SCREEN_LOG },
        )
    }
}

@Composable
private fun CameraPermissionScreen(
    denied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = "Нужен доступ к камере",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Приложение измеряет скорость подачи проволоки оптически: камера смотрит " +
                "на метку подающего ролика и считает обороты. Ничего не записывается и никуда " +
                "не отправляется — кадры обрабатываются на телефоне и сразу отбрасываются.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        if (denied) {
            Text(
                text = "Без разрешения измерять нечем. Если запрос больше не появляется, " +
                    "выдайте доступ в настройках приложения.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) { Text("Разрешить доступ") }
        if (denied) {
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Открыть настройки приложения")
            }
        }
    }
}

private fun android.content.Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
