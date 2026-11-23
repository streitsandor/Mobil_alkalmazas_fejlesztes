package hu.omegacode.meetingnotes

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

data class MeetingCalendarEvent(
    val title: String,                  // Esemény cím
    val beginTimeMillis: Long,          // Kezdés idő
    val endTimeMillis: Long,            // Befejezés idő
    val description: String,            // Megjegyzés
    val zoneId: ZoneId,                 // Dátum zóna ID
    val assumedDeviceZone: Boolean,     // Dátum zóna
    val hasRecordingDuration: Boolean   // Felvétel hossza
) {
    fun formatStart(): String = format(beginTimeMillis)
    fun formatEnd(): String = format(endTimeMillis)

    private fun format(epochMillis: Long): String =
        DateTimeFormatter.ofPattern("yyyy. MM. dd. HH:mm z", Locale.forLanguageTag("hu-HU"))
            .format(Instant.ofEpochMilli(epochMillis).atZone(zoneId))

    companion object {
        fun fromApi(
            title: String?,
            startedAt: String?,
            totalSeconds: Double?,
            deviceZone: ZoneId = ZoneId.systemDefault()
        ): MeetingCalendarEvent {
            val value = startedAt?.trim().orEmpty()
            require(value.isNotEmpty() && value != "null") {
                "A válaszból hiányzik a started_at mező. Nem használjuk helyette a rekord létrehozási idejét."
            }

            val zoned = runCatching { ZonedDateTime.parse(value) }.getOrNull()
            val offset = if (zoned == null) runCatching { OffsetDateTime.parse(value) }.getOrNull() else null
            val assumed = zoned == null && offset == null
            val instant = when {
                zoned != null -> zoned.toInstant()
                offset != null -> offset.toInstant()
                else -> {
                    val local = runCatching { LocalDateTime.parse(value) }.getOrNull()
                        ?: throw IllegalArgumentException("Hibás started_at: $value. ISO 8601 dátum és idő szükséges.")
                    val validOffsets = deviceZone.rules.getValidOffsets(local)
                    require(validOffsets.size == 1) {
                        "A kezdési időpont óraátállás miatt hiányzó vagy kétértelmű a(z) ${deviceZone.id} zónában. " +
                            "Add meg a kezdést időzóna-eltolással (például +01:00)."
                    }
                    local.atOffset(validOffsets.single()).toInstant()
                }
            }
            val usableSeconds = totalSeconds?.takeIf { it.isFinite() && it > 0.0 }
            // A felvétel hosszából felfelé kerekített perces becslés (hiányzó esetén 1 óra)
            val minutes = usableSeconds?.let { ceil(it / 60.0).toLong().coerceAtLeast(1) } ?: 60L
            val displayTitle = title?.trim().orEmpty().ifBlank { "Megbeszélés" }
            val description = if (usableSeconds != null) {
                "GDE MeetingNotes – várható időtartam: ~00$minutes perc."
            } else {
                "GDE MeetingNotes – várható időtartam: ~60 perc (alapértelmezett becslés)."
            }
            val startMillis = instant.toEpochMilli()
            val endMillis = instant.plusSeconds(minutes * 60).toEpochMilli()
            return MeetingCalendarEvent(
                title = displayTitle,
                beginTimeMillis = startMillis,
                endTimeMillis = endMillis,
                description = description,
                zoneId = deviceZone,
                assumedDeviceZone = assumed,
                hasRecordingDuration = usableSeconds != null
            )
        }
    }
}
