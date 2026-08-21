package com.almaz.mig180.tacho.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.almaz.mig180.tacho.core.SpeedMath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Точка журнала: скорость + произвольная метка пользователя. */
data class LogPoint(
    val timestampMs: Long,
    val label: String,
    val speedMMin: Double,
    val rpm: Double,
    val circumferenceMm: Double,
    val correction: Double,
)

private val Context.tachoLogStore: DataStore<Preferences> by preferencesDataStore(name = "tacho_log")

class LogRepository(context: Context) {

    private val store = context.applicationContext.tachoLogStore

    val points: Flow<List<LogPoint>> = store.data.map { prefs ->
        decode(prefs[KEY_POINTS].orEmpty())
    }

    suspend fun add(point: LogPoint) {
        store.edit { prefs ->
            val current = decode(prefs[KEY_POINTS].orEmpty()).toMutableList()
            current.add(point)
            prefs[KEY_POINTS] = encode(current)
        }
    }

    suspend fun remove(timestampMs: Long) {
        store.edit { prefs ->
            val current = decode(prefs[KEY_POINTS].orEmpty())
                .filterNot { it.timestampMs == timestampMs }
            prefs[KEY_POINTS] = encode(current)
        }
    }

    suspend fun clear() {
        store.edit { it[KEY_POINTS] = "" }
    }

    private fun encode(points: List<LogPoint>): String = points.joinToString("\n") { p ->
        listOf(
            p.timestampMs.toString(),
            p.label.replace(SEP, " ").replace('\n', ' ').replace('\r', ' '),
            p.speedMMin.toString(),
            p.rpm.toString(),
            p.circumferenceMm.toString(),
            p.correction.toString(),
        ).joinToString(SEP)
    }

    private fun decode(raw: String): List<LogPoint> {
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val parts = line.split(SEP)
            if (parts.size < 6) return@mapNotNull null
            val ts = parts[0].toLongOrNull() ?: return@mapNotNull null
            LogPoint(
                timestampMs = ts,
                label = parts[1],
                speedMMin = parts[2].toDoubleOrNull() ?: 0.0,
                rpm = parts[3].toDoubleOrNull() ?: 0.0,
                circumferenceMm = parts[4].toDoubleOrNull() ?: 0.0,
                correction = parts[5].toDoubleOrNull() ?: 1.0,
            )
        }.toList()
    }

    companion object {
        private val KEY_POINTS = stringPreferencesKey("points")

        /** Разделитель полей во внутреннем хранилище — управляющий символ SOH. */
        private val SEP = Char(1).toString()

        /** BOM, чтобы Excel открыл CSV в UTF-8 без кракозябр. */
        private val BOM = Char(0xFEFF).toString()

        /** CSV для Excel с русской локалью: разделитель «;», десятичная запятая. */
        fun toCsv(points: List<LogPoint>): String {
            val stamp = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale("ru"))
            val sb = StringBuilder()
            sb.append(BOM)
            sb.append("Время;Метка;Скорость, м/мин;Об/мин;См за 20 с;Окружность, мм;Коэффициент\n")
            points.forEach { p ->
                sb.append(stamp.format(Date(p.timestampMs))).append(';')
                sb.append(csvCell(p.label)).append(';')
                sb.append(num(p.speedMMin, 2)).append(';')
                sb.append(num(p.rpm, 1)).append(';')
                sb.append(num(SpeedMath.cmPer20s(p.speedMMin), 1)).append(';')
                sb.append(num(p.circumferenceMm, 2)).append(';')
                sb.append(num(p.correction, 3)).append('\n')
            }
            return sb.toString()
        }

        private fun num(value: Double, digits: Int): String {
            if (!value.isFinite()) return ""
            return String.format(Locale.US, "%.${digits}f", value).replace('.', ',')
        }

        private fun csvCell(text: String): String {
            val cleaned = text.replace('\n', ' ').replace('\r', ' ')
            return if (cleaned.contains(';') || cleaned.contains('"')) {
                "\"" + cleaned.replace("\"", "\"\"") + "\""
            } else {
                cleaned
            }
        }
    }
}
