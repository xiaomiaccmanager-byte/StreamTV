package com.streamtv.epg
import com.streamtv.model.*
import java.util.*

object EpgEngine {
    private const val HOURS_BACK = 6L
    private const val HOURS_FORWARD = 18L

    fun buildSchedule(channel: Channel): List<EpgItem> {
        val shows = channel.shows
        if (shows.isEmpty()) return emptyList()
        val cal = Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, -HOURS_BACK.toInt())
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val startTime = cal.time
        val endTime = Date(startTime.time + (HOURS_BACK + HOURS_FORWARD) * 3600_000L)
        val result = mutableListOf<EpgItem>()
        var cursor = startTime.time
        val flat = shows.flatMap { s -> s.episodes.map { e -> s to e } }
        if (flat.isEmpty()) return emptyList()
        var idx = 0
        while (cursor < endTime.time) {
            val (show, ep) = flat[idx % flat.size]
            val dur = (ep.duration.takeIf { it > 0 } ?: 45).toLong() * 60_000L
            result.add(EpgItem(show, ep, Date(cursor), Date(cursor + dur)))
            cursor += dur; idx++
        }
        return result
    }

    fun currentItem(schedule: List<EpgItem>): EpgItem? {
        val now = Date()
        return schedule.firstOrNull { it.startTime <= now && it.endTime > now }
    }
    fun nextItem(schedule: List<EpgItem>): EpgItem? {
        val cur = currentItem(schedule) ?: return schedule.firstOrNull()
        val idx = schedule.indexOf(cur)
        return if (idx + 1 < schedule.size) schedule[idx + 1] else null
    }
    fun pastItems(schedule: List<EpgItem>, count: Int = 3) =
        schedule.filter { it.endTime <= Date() }.takeLast(count)
    fun futureItems(schedule: List<EpgItem>, count: Int = 10) =
        schedule.filter { it.startTime > Date() }.take(count)
}
