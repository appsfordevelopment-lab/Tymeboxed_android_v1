package dev.ambitionsoftware.tymeboxed.data.export

import dev.ambitionsoftware.tymeboxed.domain.model.Profile
import dev.ambitionsoftware.tymeboxed.domain.model.Session
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class SessionExportSortDirection {
    ASCENDING,
    DESCENDING,
}

enum class SessionExportTimeZone {
    UTC,
    LOCAL,
}

object SessionDataExporter {
    private val isoFormatterUtc: DateTimeFormatter =
        DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("UTC"))

    fun exportSessionsCsv(
        sessions: List<Session>,
        profilesById: Map<String, Profile>,
        sortDirection: SessionExportSortDirection = SessionExportSortDirection.ASCENDING,
        timeZone: SessionExportTimeZone = SessionExportTimeZone.UTC,
    ): String {
        val sorted = when (sortDirection) {
            SessionExportSortDirection.ASCENDING -> sessions.sortedBy { it.startTime }
            SessionExportSortDirection.DESCENDING -> sessions.sortedByDescending { it.startTime }
        }
        val formatter = when (timeZone) {
            SessionExportTimeZone.UTC -> isoFormatterUtc
            SessionExportTimeZone.LOCAL ->
                DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.systemDefault())
        }
        val lines = buildList {
            add("session_id,profile_name,start_time,end_time,break_start_time,break_end_time")
            sorted.forEach { session ->
                val profileName = profilesById[session.profileId]?.name.orEmpty()
                val start = formatter.format(Instant.ofEpochMilli(session.startTime))
                val end = session.endTime?.let { formatter.format(Instant.ofEpochMilli(it)) }.orEmpty()
                val breakStart = session.pauseStartTime
                    ?.let { formatter.format(Instant.ofEpochMilli(it)) }
                    .orEmpty()
                val row = listOf(
                    session.id,
                    profileName,
                    start,
                    end,
                    breakStart,
                    "",
                ).joinToString(",") { escapeCsvField(it) }
                add(row)
            }
        }
        return lines.joinToString("\n")
    }

    private fun escapeCsvField(field: String): String {
        if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            return "\"${field.replace("\"", "\"\"")}\""
        }
        return field
    }
}
