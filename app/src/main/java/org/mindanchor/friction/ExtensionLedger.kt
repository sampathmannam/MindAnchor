package org.mindanchor.friction

/**
 * Tiny pure ledger of session extensions, serialized as lines of
 * "package\tdate\tcount". Only today's entries are kept, so the honest
 * "this is your 2nd extension today" counter resets each morning.
 */
object ExtensionLedger {

    fun count(serialized: String, packageName: String, today: String): Int =
        parse(serialized)[packageName to today] ?: 0

    fun increment(serialized: String, packageName: String, today: String): String {
        val entries = parse(serialized)
            .filterKeys { (_, date) -> date == today }
            .toMutableMap()
        val key = packageName to today
        entries[key] = (entries[key] ?: 0) + 1
        return entries.entries.joinToString("\n") { (k, v) -> "${k.first}\t${k.second}\t$v" }
    }

    private fun parse(serialized: String): Map<Pair<String, String>, Int> =
        serialized.lineSequence()
            .mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size != 3) return@mapNotNull null
                val count = parts[2].toIntOrNull() ?: return@mapNotNull null
                (parts[0] to parts[1]) to count
            }
            .toMap()
}
