package com.kaiharimoto.mastertool.core.update

/**
 * A released version, as it appears in a git tag or in `versionName`.
 *
 * Deliberately lenient: release tags get typed by hand and turn up as `v1.2.3`,
 * `1.2.3`, `1.2` or `1.2.3-beta.1`. Anything unparseable compares as "older than
 * everything" so a malformed tag can never masquerade as an update.
 */
data class AppVersion(
    val numbers: List<Int>,
    val preRelease: String?,
    val raw: String,
) : Comparable<AppVersion> {

    val isPreRelease: Boolean get() = preRelease != null

    override fun compareTo(other: AppVersion): Int {
        val size = maxOf(numbers.size, other.numbers.size)
        for (i in 0 until size) {
            // Missing components are zero, so 1.2 and 1.2.0 are the same version.
            val mine = numbers.getOrElse(i) { 0 }
            val theirs = other.numbers.getOrElse(i) { 0 }
            if (mine != theirs) return mine.compareTo(theirs)
        }

        // 1.0.0-beta precedes 1.0.0; two pre-releases fall back to text order.
        return when {
            preRelease == null && other.preRelease == null -> 0
            preRelease == null -> 1
            other.preRelease == null -> -1
            else -> preRelease.compareTo(other.preRelease)
        }
    }

    override fun toString(): String = raw

    companion object {
        val UNKNOWN = AppVersion(emptyList(), null, "")

        fun parse(raw: String): AppVersion {
            val trimmed = raw.trim().removePrefix("v").removePrefix("V")
            if (trimmed.isEmpty()) return UNKNOWN

            val preReleaseStart = trimmed.indexOfFirst { it == '-' || it == '+' }
            val numericPart = if (preReleaseStart >= 0) trimmed.take(preReleaseStart) else trimmed
            val preRelease = if (preReleaseStart >= 0) {
                trimmed.substring(preReleaseStart + 1).takeIf { it.isNotEmpty() }
            } else {
                null
            }

            val numbers = numericPart.split('.')
                .map { part -> part.takeWhile(Char::isDigit).toIntOrNull() }

            // A version is only usable if it starts with at least one number.
            if (numbers.isEmpty() || numbers.first() == null) return UNKNOWN

            return AppVersion(
                numbers = numbers.takeWhile { it != null }.filterNotNull(),
                preRelease = preRelease,
                raw = raw.trim(),
            )
        }

        /**
         * True when [candidate] is a strictly newer release than [current].
         *
         * Returns false for anything unparseable on either side: refusing to
         * offer an update is always safer than offering a bogus one.
         */
        fun isNewer(current: String, candidate: String): Boolean {
            val currentVersion = parse(current)
            val candidateVersion = parse(candidate)
            if (currentVersion == UNKNOWN || candidateVersion == UNKNOWN) return false
            return candidateVersion > currentVersion
        }
    }
}
