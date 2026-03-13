package com.streamtv.data.scheduler

import com.streamtv.data.model.Channel
import com.streamtv.data.model.EpgItem
import java.util.*

/**
 * EpgScheduler — генерирует расписание вещания из списка шоу канала.
 * Принцип: берём все эпизоды всех шоу, раскидываем их по времени начиная
 * с offsetHours часов назад, создавая непрерывный эфир 24/7.
 */
object EpgScheduler {

    /**
     * Возвращает полное расписание на 24 часа (8 ч назад + 16 вперёд)
     */
    fun buildSchedule(channel: Channel, offsetHours: Int = 8): List<EpgItem> {
        val result = mutableListOf<EpgItem>()
        val allEpisodes = channel.shows.flatMap { show ->
            show.episodes.map { ep -> Pair(show, ep) }
        }
        if (allEpisodes.isEmpty()) return result

        val cal = Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, -offsetHours)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val endCal = Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, 24 - offsetHours)
        }

        var epIndex = 0
        while (cal.before(endCal)) {
            val pair = allEpisodes[epIndex % allEpisodes.size]
            val show = pair.first
            val episode = pair.second
            val start = cal.time
            cal.add(Calendar.MINUTE, episode.durationMin)
            val end = cal.time

            result.add(
                EpgItem(
                    show = show,
                    episode = episode,
                    startTime = start,
                    endTime = end,
                    channelId = channel.id
                )
            )
            epIndex++
        }
        return result
    }

    /**
     * Что идёт прямо сейчас на канале
     */
    fun getCurrentItem(channel: Channel): EpgItem? {
        return buildSchedule(channel).firstOrNull { it.isNow }
    }

    /**
     * Ближайшие N передач (текущая + следующие)
     */
    fun getUpcoming(channel: Channel, count: Int = 8): List<EpgItem> {
        val now = Date()
        return buildSchedule(channel)
            .filter { !it.isPast || it.isNow }
            .take(count)
    }

    /**
     * Форматирует время в строку HH:mm
     */
    fun formatTime(date: Date): String {
        val cal = Calendar.getInstance().apply { time = date }
        return "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }

    /**
     * Форматирует продолжительность: "45м" или "1ч 30м"
     */
    fun formatDuration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) "${h}ч ${m}м" else "${m}м"
    }
}
