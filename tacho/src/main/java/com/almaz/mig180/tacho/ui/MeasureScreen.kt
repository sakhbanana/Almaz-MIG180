package com.almaz.mig180.tacho.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.almaz.mig180.tacho.data.TachoSettings
import com.almaz.mig180.tacho.ui.components.BrightnessChart
import com.almaz.mig180.tacho.ui.components.CameraPane
import com.almaz.mig180.tacho.ui.theme.Amber
import com.almaz.mig180.tacho.ui.theme.Danger
import com.almaz.mig180.tacho.ui.theme.Mint
import com.almaz.mig180.tacho.ui.theme.TextSecondary
import com.almaz.mig180.tacho.vm.MeasureUiState
import com.almaz.mig180.tacho.vm.TachoViewModel
import kotlinx.coroutines.delay

private const val MARKER_LOST_MS = 3_000L

@Composable
fun MeasureScreen(
    viewModel: TachoViewModel,
    onOpenCalibration: () -> Unit,
    onOpenLog: () -> Unit,
) {
    val ui by viewModel.ui.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var running by rememberSaveable { mutableStateOf(true) }
    var torchOn by rememberSaveable { mutableStateOf(false) }
    var torchAvailable by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var showRecordDialog by rememberSaveable { mutableStateOf(false) }

    var highlight by remember { mutableStateOf(false) }
    LaunchedEffect(ui.pulseCount) {
        if (ui.pulseCount > 0L) {
            highlight = true
            delay(70)
            highlight = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Тахометр подачи",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onOpenCalibration) { Text("Калибровка") }
            OutlinedButton(onClick = onOpenLog) { Text("Журнал") }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black, RoundedCornerShape(12.dp)),
        ) {
            CameraPane(
                running = running,
                torchOn = torchOn,
                analyzer = viewModel.analyzer,
                geometry = ui.geometry,
                roi = ui.roi,
                highlight = highlight,
                onRoiPicked = viewModel::setRoi,
                onTorchAvailable = { torchAvailable = it },
                onCameraStopped = viewModel::onCameraStopped,
                onError = { cameraError = it },
                modifier = Modifier.fillMaxSize(),
            )
            StatusChip(
                state = ui,
                running = running,
                error = cameraError,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(8.dp),
            )
        }

        SpeedPanel(state = ui, settings = settings)

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = "Яркость ROI и порог · ${fmt(ui.fps, 0)} кадр/с",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
                BrightnessChart(
                    snapshot = ui.chart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(78.dp)
                        .padding(top = 4.dp),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { running = !running },
                modifier = Modifier.weight(1f),
            ) { Text(if (running) "Стоп" else "Старт") }
            OutlinedButton(
                onClick = { torchOn = !torchOn },
                enabled = torchAvailable && running,
                modifier = Modifier.weight(1f),
            ) { Text(if (torchOn) "Фонарь выкл" else "Фонарь") }
            OutlinedButton(
                onClick = viewModel::resetMeasurement,
                modifier = Modifier.weight(1f),
            ) { Text("Сброс") }
        }

        Button(
            onClick = { showRecordDialog = true },
            enabled = ui.speedMMin > 0.0,
            colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Color(0xFF00201A)),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Записать точку") }
    }

    if (showRecordDialog) {
        RecordPointDialog(
            speed = ui.speedMMin,
            onDismiss = { showRecordDialog = false },
            onConfirm = { label ->
                viewModel.recordPoint(label)
                showRecordDialog = false
            },
        )
    }
}

@Composable
private fun SpeedPanel(state: MeasureUiState, settings: TachoSettings) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = fmt(state.speedMMin, 2),
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (state.stable) Mint else Amber,
                )
                Text(
                    text = " м/мин",
                    fontSize = 20.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Metric("об/мин", fmt(state.rpm, 0))
                Metric("см за 20 с", fmt(state.cmPer20s, 1))
                Metric("импульсов", state.pulseCount.toString())
                Metric("C, мм", fmt(settings.circumferenceMm, 2))
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

@Composable
private fun StatusChip(
    state: MeasureUiState,
    running: Boolean,
    error: String?,
    modifier: Modifier = Modifier,
) {
    val (text, color) = statusOf(state, running, error)
    Box(
        modifier = modifier
            .background(Color(0xCC101214), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(text = text, color = color, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

private fun statusOf(state: MeasureUiState, running: Boolean, error: String?): Pair<String, Color> = when {
    error != null -> error to Danger
    !running -> "камера остановлена" to TextSecondary
    !state.geometry.valid -> "запуск камеры…" to TextSecondary
    state.roi == null -> "тапните по метке" to Amber
    state.msSinceLastPulse > MARKER_LOST_MS -> "метка не видна" to Danger
    state.periodsCollected < 3 -> "ловлю обороты ${state.periodsCollected}/3" to Amber
    state.stable -> "стабильно" to Mint
    else -> "нестабильно, разброс ${fmt(state.spread * 100, 0)} %" to Amber
}

@Composable
private fun RecordPointDialog(
    speed: Double,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Записать ${fmt(speed, 2)} м/мин") },
        text = {
            Column {
                Text(
                    text = "Метка — например, уставка регулятора на дисплее аппарата.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    singleLine = true,
                    label = { Text("Метка") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(label) }) { Text("Записать") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
