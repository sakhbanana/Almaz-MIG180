package com.almaz.mig180.tacho.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.almaz.mig180.tacho.core.SpeedMath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Параметры пересчёта оборотов в скорость. */
data class TachoSettings(
    /** Эффективная окружность ролика, мм — то, что реально протягивает проволоку. */
    val circumferenceMm: Double = SpeedMath.DEFAULT_CIRCUMFERENCE_MM,
    /** Геометрический диаметр ролика, мм — справочное значение для сравнения. */
    val geometricDiameterMm: Double = SpeedMath.DEFAULT_GEOMETRIC_DIAMETER_MM,
    /** Поправочный коэффициент, по умолчанию 1.000. */
    val correction: Double = 1.0,
    /** Время последней калибровки, мс epoch; 0 — калибровки не было. */
    val calibratedAtMs: Long = 0L,
) {
    val effectiveDiameterMm: Double get() = SpeedMath.diameterOf(circumferenceMm)
}

private val Context.tachoDataStore: DataStore<Preferences> by preferencesDataStore(name = "tacho_settings")

class SettingsRepository(context: Context) {

    private val store = context.applicationContext.tachoDataStore

    val settings: Flow<TachoSettings> = store.data.map { prefs ->
        TachoSettings(
            circumferenceMm = prefs[KEY_CIRCUMFERENCE]?.takeIf { it.isFinite() && it > 0.0 }
                ?: SpeedMath.DEFAULT_CIRCUMFERENCE_MM,
            geometricDiameterMm = prefs[KEY_GEOMETRIC_DIAMETER]?.takeIf { it.isFinite() && it > 0.0 }
                ?: SpeedMath.DEFAULT_GEOMETRIC_DIAMETER_MM,
            correction = prefs[KEY_CORRECTION]?.takeIf { it.isFinite() && it > 0.0 } ?: 1.0,
            calibratedAtMs = prefs[KEY_CALIBRATED_AT] ?: 0L,
        )
    }

    suspend fun setCircumference(mm: Double, calibratedAtMs: Long? = null) {
        if (!mm.isFinite() || mm <= 0.0) return
        store.edit { prefs ->
            prefs[KEY_CIRCUMFERENCE] = mm
            if (calibratedAtMs != null) prefs[KEY_CALIBRATED_AT] = calibratedAtMs
        }
    }

    suspend fun setGeometricDiameter(mm: Double) {
        if (!mm.isFinite() || mm <= 0.0) return
        store.edit { it[KEY_GEOMETRIC_DIAMETER] = mm }
    }

    suspend fun setCorrection(factor: Double) {
        if (!factor.isFinite() || factor <= 0.0) return
        store.edit { it[KEY_CORRECTION] = factor }
    }

    /** Сброс к геометрии: C = pi * D, коэффициент 1.000, отметка калибровки снята. */
    suspend fun resetToGeometry() {
        store.edit { prefs ->
            val diameter = prefs[KEY_GEOMETRIC_DIAMETER]?.takeIf { it.isFinite() && it > 0.0 }
                ?: SpeedMath.DEFAULT_GEOMETRIC_DIAMETER_MM
            prefs[KEY_CIRCUMFERENCE] = SpeedMath.circumferenceOf(diameter)
            prefs[KEY_CORRECTION] = 1.0
            prefs[KEY_CALIBRATED_AT] = 0L
        }
    }

    private companion object {
        val KEY_CIRCUMFERENCE = doublePreferencesKey("circumference_mm")
        val KEY_GEOMETRIC_DIAMETER = doublePreferencesKey("geometric_diameter_mm")
        val KEY_CORRECTION = doublePreferencesKey("correction_factor")
        val KEY_CALIBRATED_AT = longPreferencesKey("calibrated_at_ms")
    }
}
