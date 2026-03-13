package com.streamtv.data.model

import java.util.Date

/** Канал = одна категория контента (Боевик, Фантастика, ...) */
data class Channel(
    val id: Int,
    val name: String,       // "БОЕВИК"
    val emoji: String,      // "💥"
    val colorHex: String,   // "#1a0800" — фон иконки
    val shows: List<Show>
)

/** Сериал или фильм */
data class Show(
    val title: String,
    val description: String = "",
    val posterUrl: String = "",
    val episodes: List<Episode>,
    val genre: String = "",
    val year: String = "",
    val rating: String = ""
)

/** Один эпизод / фильм */
data class Episode(
    val title: String,          // "S01E01 — Пилот"
    val streamUrl: String = "", // прямая .m3u8 или .mp4 ссылка
    val pageUrl: String = "",   // ссылка на страницу для парсинга
    val durationMin: Int = 45,
    val posterUrl: String = "",
    val subtitlesUrl: String = ""
)

/** Элемент EPG-расписания */
data class EpgItem(
    val show: Show,
    val episode: Episode,
    val startTime: Date,
    val endTime: Date,
    val channelId: Int
) {
    val isNow: Boolean
        get() { val n = Date(); return n.after(startTime) && n.before(endTime) }

    val isPast: Boolean
        get() = Date().after(endTime)

    val progressPercent: Int
        get() {
            if (!isNow) return if (isPast) 100 else 0
            val total = endTime.time - startTime.time
            val elapsed = Date().time - startTime.time
            return (elapsed * 100 / total).toInt().coerceIn(0, 100)
        }

    val minutesLeft: Int
        get() = ((endTime.time - Date().time) / 60000).toInt().coerceAtLeast(0)
}

/** Конфигурация источника */
data class SourceConfig(
    val url: String,
    val name: String,
    val type: SourceType
)

enum class SourceType {
    LOSTFILM, HDREZKA, KINOGO, ANIMEGO, CUSTOM
}
