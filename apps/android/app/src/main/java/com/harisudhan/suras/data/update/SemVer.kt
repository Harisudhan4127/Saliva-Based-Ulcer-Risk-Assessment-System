package com.harisudhan.suras.data.update

/**
 * Minimal SemVer 2.0 comparison used to decide whether a GitHub release is
 * newer than the installed app. Build metadata is ignored; pre-release
 * versions sort below the matching release. Unparseable versions fall back to
 * "not newer" so a malformed tag can never trigger an install.
 */
object SemVer {

    /** @return negative, zero or positive like [Comparable.compareTo]. */
    fun compare(left: String, right: String): Int {
        val a = parse(left) ?: return 0
        val b = parse(right) ?: return 0

        val length = maxOf(a.core.size, b.core.size)
        for (index in 0 until length) {
            val x = a.core.getOrElse(index) { 0 }
            val y = b.core.getOrElse(index) { 0 }
            if (x != y) return x.compareTo(y)
        }

        val preA = a.preRelease
        val preB = b.preRelease
        if (preA.isEmpty() && preB.isEmpty()) return 0
        if (preA.isEmpty()) return 1
        if (preB.isEmpty()) return -1

        val preLength = maxOf(preA.size, preB.size)
        for (index in 0 until preLength) {
            val x = preA.getOrNull(index) ?: return -1
            val y = preB.getOrNull(index) ?: return 1
            val xNumber = x.toLongOrNull()
            val yNumber = y.toLongOrNull()
            val result = when {
                xNumber != null && yNumber != null -> xNumber.compareTo(yNumber)
                xNumber != null -> -1
                yNumber != null -> 1
                else -> x.compareTo(y)
            }
            if (result != 0) return result
        }
        return 0
    }

    fun isNewer(candidate: String, installed: String): Boolean = compare(candidate, installed) > 0

    private data class Parsed(val core: List<Long>, val preRelease: List<String>)

    private fun parse(raw: String): Parsed? {
        val withoutPrefix = raw.trim().removePrefix("v").removePrefix("V")
        if (withoutPrefix.isEmpty()) return null
        val withoutMetadata = withoutPrefix.substringBefore('+')
        val corePart = withoutMetadata.substringBefore('-')
        val prePart = withoutMetadata.substringAfter('-', missingDelimiterValue = "")
        val core = corePart.split('.').map { segment ->
            // Leading zeroes are invalid SemVer, but accepting "2.01.0" is
            // harmless for an update check and avoids a false negative.
            segment.toLongOrNull() ?: return null
        }
        if (core.isEmpty()) return null
        val preRelease = if (prePart.isEmpty()) emptyList() else prePart.split('.')
        return Parsed(core, preRelease)
    }
}
