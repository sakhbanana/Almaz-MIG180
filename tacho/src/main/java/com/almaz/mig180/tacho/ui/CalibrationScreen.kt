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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.almaz.mig180.tacho.core.SpeedMath
import com.almaz.mig180.tacho.ui.components.CameraPane
import com.almaz.mig180.tacho.ui.theme.Amber
import com.almaz.mig180.tacho.ui.theme.Danger
import com.almaz.mig180.tacho.ui.theme.Mint
import com.almaz.mig180.tacho.ui.theme.TextSecondary
import com.almaz.mig180.tacho.vm.TachoViewModel
import kotlin.math.abs

@Composable
fun CalibrationScreen(viewModel: TachoViewModel, onBack: () -> Unit) {
    val ui by viewModel.ui.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val calibration by viewModel.calibration.collectAsState()

    var lengthText by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Калибровка",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onBack) { Text("Назад") }
        }

        // Превью нужно и здесь: мастер считает обороты, пока камера видит метку.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .background(Color.Black, RoundedCornerShape(12.dp)),
        ) {
            CameraPane(
                running = true,
                torchOn = false,
                analyzer = viewModel.analyzer,
                geometry = ui.geometry,
                roi = ui.roi,
                highlight = false,
                onRoiPicked = viewModel::setRoi,
                onTorchAvailable = {},
                onCameraStopped = viewModel::onCameraStopped,
                onError = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            text = if (ui.roi == null) {
                "Тапните по метке на ролике — без ROI обороты не считаются."
            } else {
                "Импульсов: ${ui.pulseCount} · период ${fmt(ui.periodMs, 1)} мс"
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (ui.roi == null) Danger else TextSecondary,
        )

        WizardCard(
            running = calibration.running,
            measured = calibration.measured,
            revolutions = calibration.revolutions,
            pulses = calibration.pulses,
            error = calibration.error,
            lengthText = lengthText,
            onLengthChange = { lengthText = it },
            onStart = {
                lengthText = ""
                viewModel.startCalibration()
            },
            onStop = viewModel::stopCalibration,
            onApply = { parseNumber(lengthText)?.let(viewModel::applyCalibration) },
            onRestart = viewModel::cancelCalibration,
        )

        calibration.result?.let { result ->
            ResultCard(result.circumferenceMm, result.effectiveDiameterMm, result.revolutions, result.lengthMm, result.deviationPercent)
        }

        ManualCard(
            circumferenceMm = settings.circumferenceMm,
            geometricDiameterMm = settings.geometricDiameterMm,
            correction = settings.correction,
            calibrated = settings.calibratedAtMs > 0L,
            onCircumference = viewModel::setCircumference,
            onDiameter = viewModel::setDiameter,
            onGeometricDiameter = viewModel::setGeometricDiameter,
            onCorrection = viewModel::setCorrection,
            onResetToGeometry = viewModel::resetToGeometry,
        )
    }
}

@Composable
private fun WizardCard(
    running: Boolean,
    measured: Boolean,
    revolutions: Double,
    pulses: Int,
    error: String?,
    lengthText: String,
    onLengthChange: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onApply: () -> Unit,
    onRestart: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Мастер калибровки", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = "1. Откусите проволоку у наконечника.\n" +
                    "2. Запустите подачу и одновременно нажмите «Старт».\n" +
                    "3. Через 10–20 секунд остановите подачу и нажмите «Стоп».\n" +
                    "4. Измерьте линейкой вышедшую проволоку и введите длину.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = fmt(revolutions, 2),
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (running) Amber else Mint,
                )
                Text(
                    text = "  оборотов · $pulses имп.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }

            if (!running) {
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Text(if (measured) "Начать заново" else "Старт")
                }
            } else {
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(containerColor = Danger, contentColor = Color(0xFF3A0000)),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Стоп") }
            }

            if (measured && !running) {
                OutlinedTextField(
                    value = lengthText,
                    onValueChange = onLengthChange,
                    singleLine = true,
                    label = { Text("Длина вышедшей проволоки, см") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onApply,
                        enabled = parseNumber(lengthText)?.let { it > 0.0 } == true && revolutions > 0.0,
                        modifier = Modifier.weight(1f),
                    ) { Text("Рассчитать и сохранить") }
                    OutlinedButton(onClick = onRestart) { Text("Сброс") }
                }
            }

            if (error != null) {
                Text(text = error, color = Danger, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ResultCard(
    circumferenceMm: Double,
    effectiveDiameterMm: Double,
    revolutions: Double,
    lengthMm: Double,
    deviationPercent: Double,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Результат калибровки", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("Эффективная окружность: ${fmt(circumferenceMm, 2)} мм", color = Mint)
            Text("Эквивалентный диаметр: ${fmt(effectiveDiameterMm, 2)} мм")
            Text(
                text = if (deviationPercent.isFinite()) {
                    "Отклонение от геометрии: ${fmt(deviationPercent, 1)} %"
                } else {
                    "Геометрический диаметр не задан — сравнить не с чем"
                },
                color = if (deviationPercent.isFinite() && abs(deviationPercent) > 15.0) Danger else TextSecondary,
            )
            Text(
                text = "${fmt(lengthMm / 10.0, 1)} см проволоки за ${fmt(revolutions, 2)} оборотов",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun ManualCard(
    circumferenceMm: Double,
    geometricDiameterMm: Double,
    correction: Double,
    calibrated: Boolean,
    onCircumference: (Double) -> Unit,
    onDiameter: (Double) -> Unit,
    onGeometricDiameter: (Double) -> Unit,
    onCorrection: (Double) -> Unit,
    onResetToGeometry: () -> Unit,
) {
    var circText by remember { mutableStateOf(fmt(circumferenceMm, 2)) }
    var diaText by remember { mutableStateOf(fmt(SpeedMath.diameterOf(circumferenceMm), 2)) }
    var geoText by remember { mutableStateOf(fmt(geometricDiameterMm, 2)) }
    var corrText by remember { mutableStateOf(fmt(correction, 3)) }

    // Значение могло приехать из мастера или из «сбросить к геометрии» — подтягиваем поля.
    LaunchedEffect(circumferenceMm) {
        val typed = parseNumber(circText)
        if (typed == null || abs(typed - circumferenceMm) > 0.005) {
            circText = fmt(circumferenceMm, 2)
            diaText = fmt(SpeedMath.diameterOf(circumferenceMm), 2)
        }
    }
    LaunchedEffect(correction) {
        val typed = parseNumber(corrText)
        if (typed == null || abs(typed - correction) > 0.0005) corrText = fmt(correction, 3)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Параметры вручную", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = if (calibrated) "Значение получено калибровкой." else "Значение по умолчанию, калибровки ещё не было.",
                style = MaterialTheme.typography.labelSmall,
                color = if (calibrated) Mint else TextSecondary,
            )

            NumberField(
                value = circText,
                label = "Эффективная окружность C, мм",
                onValueChange = { text ->
                    circText = text
                    val parsed = parseNumber(text)
                    if (parsed != null && parsed > 0.0) {
                        diaText = fmt(SpeedMath.diameterOf(parsed), 2)
                        onCircumference(parsed)
                    }
                },
            )
            NumberField(
                value = diaText,
                label = "Эквивалентный диаметр (C / π), мм",
                onValueChange = { text ->
                    diaText = text
                    val parsed = parseNumber(text)
                    if (parsed != null && parsed > 0.0) {
                        circText = fmt(SpeedMath.circumferenceOf(parsed), 2)
                        onDiameter(parsed)
                    }
                },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            NumberField(
                value = geoText,
                label = "Геометрический диаметр ролика, мм",
                onValueChange = { text ->
                    geoText = text
                    parseNumber(text)?.takeIf { it > 0.0 }?.let(onGeometricDiameter)
                },
            )
            NumberField(
                value = corrText,
                label = "Поправочный коэффициент",
                onValueChange = { text ->
                    corrText = text
                    parseNumber(text)?.takeIf { it > 0.0 }?.let(onCorrection)
                },
            )

            OutlinedButton(onClick = onResetToGeometry, modifier = Modifier.fillMaxWidth()) {
                Text("Сбросить к геометрии (C = π · D)")
            }
        }
    }
}

@Composable
private fun NumberField(value: String, label: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(label) },
        isError = parseNumber(value)?.let { it <= 0.0 } ?: value.isNotBlank(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}
