package com.almaz.mig180.tacho.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.almaz.mig180.tacho.core.SpeedMath
import com.almaz.mig180.tacho.data.LogPoint
import com.almaz.mig180.tacho.ui.theme.Amber
import com.almaz.mig180.tacho.ui.theme.TextSecondary
import com.almaz.mig180.tacho.vm.TachoViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogScreen(viewModel: TachoViewModel, onBack: () -> Unit) {
    val points by viewModel.logPoints.collectAsState()
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val message = try {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(viewModel.buildCsv().toByteArray(Charsets.UTF_8))
            }
            "CSV сохранён"
        } catch (t: Throwable) {
            "Не удалось сохранить: ${t.message}"
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Журнал замеров",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onBack) { Text("Назад") }
        }

        Text(
            text = "Таблица «уставка регулятора → м/мин». Точек: ${points.size}",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { exportLauncher.launch(defaultFileName()) },
                enabled = points.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) { Text("Экспорт CSV") }
            OutlinedButton(
                onClick = { confirmClear = true },
                enabled = points.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) { Text("Очистить") }
        }

        if (points.isEmpty()) {
            Text(
                text = "Пусто. Запишите точку с экрана измерения — скорость сохранится вместе с вашей меткой.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(points.sortedByDescending { it.timestampMs }, key = { it.timestampMs }) { point ->
                    PointRow(point = point, onDelete = { viewModel.removePoint(point.timestampMs) })
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Очистить журнал?") },
            text = { Text("Все ${points.size} точек будут удалены без возможности восстановления.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearLog()
                    confirmClear = false
                }) { Text("Очистить") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun PointRow(point: LogPoint, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${fmt(point.speedMMin, 2)} м/мин",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Amber,
                )
                Text(
                    text = point.label.ifBlank { "без метки" },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${fmt(point.rpm, 0)} об/мин · ${fmt(SpeedMath.cmPer20s(point.speedMMin), 1)} см/20 с · " +
                        "C ${fmt(point.circumferenceMm, 2)} мм · ${stamp(point.timestampMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
            TextButton(onClick = onDelete) { Text("Удалить") }
        }
    }
}

private fun stamp(timestampMs: Long): String =
    SimpleDateFormat("dd.MM HH:mm", Locale("ru")).format(Date(timestampMs))

private fun defaultFileName(): String =
    "wire-speed-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".csv"
