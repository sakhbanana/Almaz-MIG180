package com.almaz.mig180.tacho.ui

import java.util.Locale

private val RU = Locale("ru")

/** Число с запятой в качестве десятичного разделителя; для не-чисел — прочерк. */
fun fmt(value: Double, digits: Int): String {
    if (!value.isFinite()) return "—"
    return String.format(RU, "%.${digits}f", value)
}

/** Разбор ввода: принимаем и точку, и запятую. */
fun parseNumber(text: String): Double? {
    val cleaned = text.trim().replace(',', '.').replace(" ", "")
    if (cleaned.isEmpty()) return null
    val value = cleaned.toDoubleOrNull() ?: return null
    return if (value.isFinite()) value else null
}
