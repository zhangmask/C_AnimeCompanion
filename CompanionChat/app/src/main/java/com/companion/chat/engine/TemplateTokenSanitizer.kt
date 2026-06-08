package com.companion.chat.engine

internal data class SanitizedToken(
    val text: String,
    val shouldStop: Boolean
)

internal class TemplateTokenSanitizer(
    private val stopMarkers: List<String>,
    private val removableMarkers: List<String> = emptyList()
) {
    private var pending = ""
    private var stopped = false
    private val allMarkers = (stopMarkers + removableMarkers).distinct().filter { it.isNotEmpty() }
    private val maxMarkerLength = allMarkers.maxOfOrNull { it.length } ?: 0

    fun append(token: String): SanitizedToken {
        if (stopped || token.isEmpty()) return SanitizedToken("", stopped)
        if (allMarkers.isEmpty()) return SanitizedToken(token, shouldStop = false)

        var working = pending + token
        val output = StringBuilder()

        while (working.isNotEmpty()) {
            val match = findFirstMarker(working)
            if (match == null) {
                val holdLength = longestPossibleMarkerPrefixSuffix(working)
                val emitLength = (working.length - holdLength).coerceAtLeast(0)
                output.append(working.substring(0, emitLength))
                pending = working.substring(emitLength).takeLast((maxMarkerLength - 1).coerceAtLeast(0))
                return SanitizedToken(output.toString(), shouldStop = false)
            }

            output.append(working.substring(0, match.index))
            if (match.marker in stopMarkers) {
                stopped = true
                pending = ""
                return SanitizedToken(output.toString(), shouldStop = true)
            }
            working = working.substring(match.index + match.marker.length)
        }

        pending = ""
        return SanitizedToken(output.toString(), shouldStop = false)
    }

    fun flush(): String {
        if (stopped) {
            pending = ""
            return ""
        }
        return pending.also { pending = "" }
    }

    private fun findFirstMarker(value: String): MarkerMatch? {
        return allMarkers
            .mapNotNull { marker ->
                val index = value.indexOf(marker)
                if (index >= 0) MarkerMatch(index, marker) else null
            }
            .minWithOrNull(compareBy<MarkerMatch> { it.index }.thenByDescending { it.marker.length })
    }

    private fun longestPossibleMarkerPrefixSuffix(value: String): Int {
        val maxLength = minOf(value.length, (maxMarkerLength - 1).coerceAtLeast(0))
        for (length in maxLength downTo 1) {
            val suffix = value.takeLast(length)
            if (allMarkers.any { marker -> marker.startsWith(suffix) }) {
                return length
            }
        }
        return 0
    }

    private data class MarkerMatch(
        val index: Int,
        val marker: String
    )
}
