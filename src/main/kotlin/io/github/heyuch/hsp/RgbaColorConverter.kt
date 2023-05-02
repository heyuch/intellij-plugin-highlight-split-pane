package io.github.heyuch.hsp

import com.intellij.util.xmlb.Converter
import java.awt.Color

class RgbaColorConverter : Converter<Color>() {

    override fun fromString(value: String): Color? {
        if (value.isEmpty() || value.isBlank()) {
            return null
        }

        val result = regex.matchEntire(value)
        if (result == null) {
            return null
        }

        val r = extractIntValue(result, "r")
        if (r == null) {
            return null
        }
        val g = extractIntValue(result, "g")
        if (g == null) {
            return null
        }
        val b = extractIntValue(result, "b")
        if (b == null) {
            return null
        }
        val a = extractIntValue(result, "a")
        if (a == null) {
            return null
        }

        @Suppress("UseJBColor")
        return Color(r, g, b, a)
    }

    override fun toString(value: Color): String {
        return "rgba(%d,%d,%d,%d)".format(value.red, value.green, value.blue, value.alpha)
    }

    private companion object {

        val regex = Regex("^rgba\\((?<r>\\d+),(?<g>\\d+),(?<b>\\d+),(?<a>\\d+)\\)$")

        fun extractIntValue(result: MatchResult, group: String): Int? {
            val matched = result.groups[group]
            if (matched == null) {
                return null
            }

            val v = matched.value
            if (v.isEmpty() || v.isBlank()) {
                return null
            }

            return try {
                v.toInt(10)
            } catch (e: NumberFormatException) {
                null
            }
        }

    }

}
