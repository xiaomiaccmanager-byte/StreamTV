package com.streamtv.data.parser

import com.streamtv.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

/**
 * ContentParser — парсит сайты и формирует список каналов-категорий.
 *
 * Поддерживает:
 *  - LostFilm.tv  (сериалы по жанрам)
 *  - HDRezka.ag   (фильмы и сериалы по жанрам)
 *  - Kinogo        (фильмы по категориям)
 *  - AnimeGo       (аниме по жанрам)
 *  - Custom        (базовый парсинг любого сайта)
 */
class ContentParser {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Android 14; Mobile) AppleWebKit/537.36")
                .header("Accept-Language", "ru-RU,ru;q=0.9")
                .build()
            chain.proceed(req)
        }
        .build()

    suspend fun parse(config: SourceConfig): List<Channel> = withContext(Dispatchers.IO) {
        try {
            when (config.type) {
                SourceType.LOSTFILM  -> parseLostFilm(config.url)
                SourceType.HDREZKA   -> parseHDRezka(config.url)
                SourceType.KINOGO    -> parseKinogo(config.url)
                SourceType.ANIMEGO   -> parseAnimeGo(config.url)
                SourceType.CUSTOM    -> parseCustom(config.url)
            }
        } catch (e: Exception) {
            // При ошибке возвращаем демо-каналы
            getDemoChannels()
        }
    }

    // ─────────────────────────────────────────────────────────
    // LOSTFILM.TV
    // ─────────────────────────────────────────────────────────
    private fun parseLostFilm(baseUrl: String): List<Channel> {
        // LostFilm группирует сериалы по жанрам на странице /serials/
        val html = fetch("$baseUrl/serials/")
        val doc = Jsoup.parse(html)

        // Парсим блоки жанров
        val genreMap = mutableMapOf<String, MutableList<Show>>()

        doc.select(".bb .shortstory, .serial-item, [class*=serial]").forEach { el ->
            val title = el.select("a[title], .title, h2, h3").firstOrNull()?.text() ?: return@forEach
            val genre = el.select(".genre, [class*=genre]").firstOrNull()?.text() ?: "Разное"
            val pageUrl = el.select("a[href]").firstOrNull()?.attr("abs:href") ?: ""
            val posterUrl = el.select("img").firstOrNull()?.attr("abs:src") ?: ""

            val show = Show(
                title = title,
                posterUrl = posterUrl,
                episodes = listOf(
                    Episode(title = "Смотреть онлайн", pageUrl = pageUrl, durationMin = 45)
                ),
                genre = genre
            )
            genreMap.getOrPut(genre) { mutableListOf() }.add(show)
        }

        if (genreMap.isEmpty()) {
            // Fallback: парсим просто список сериалов без группировки
            val shows = doc.select("a[href*=/series/]").map { el ->
                Show(
                    title = el.text().takeIf { it.isNotBlank() } ?: "Сериал",
                    posterUrl = el.select("img").firstOrNull()?.attr("abs:src") ?: "",
                    episodes = listOf(Episode(title = "Смотреть", pageUrl = el.attr("abs:href"), durationMin = 45))
                )
            }.distinctBy { it.title }.take(50)

            return buildChannelsFromShows(shows)
        }

        return buildChannelsFromGenreMap(genreMap)
    }

    // ─────────────────────────────────────────────────────────
    // HDREZKA
    // ─────────────────────────────────────────────────────────
    private fun parseHDRezka(baseUrl: String): List<Channel> {
        val genreMap = mutableMapOf<String, MutableList<Show>>()
        val genres = listOf("films/fiction", "films/action", "films/drama",
                            "films/comedy", "films/thriller", "series/drama")

        genres.forEach { path ->
            try {
                val html = fetch("$baseUrl/$path/")
                val doc = Jsoup.parse(html)
                val genreName = path.split("/").last().let { mapGenre(it) }

                doc.select(".b-content__inline_item").forEach { el ->
                    val title = el.select(".b-content__inline_item-link a").firstOrNull()?.text() ?: return@forEach
                    val pageUrl = el.select("a").firstOrNull()?.attr("abs:href") ?: ""
                    val posterUrl = el.select("img").firstOrNull()?.attr("abs:src") ?: ""
                    val year = el.select(".year, [class*=year]").firstOrNull()?.text() ?: ""

                    genreMap.getOrPut(genreName) { mutableListOf() }.add(
                        Show(title = title, posterUrl = posterUrl, year = year,
                             episodes = listOf(Episode(title = title, pageUrl = pageUrl, durationMin = 100)))
                    )
                }
            } catch (_: Exception) {}
        }

        return if (genreMap.isNotEmpty()) buildChannelsFromGenreMap(genreMap)
               else getDemoChannels()
    }

    // ─────────────────────────────────────────────────────────
    // KINOGO
    // ─────────────────────────────────────────────────────────
    private fun parseKinogo(baseUrl: String): List<Channel> {
        val html = fetch(baseUrl)
        val doc = Jsoup.parse(html)
        val genreMap = mutableMapOf<String, MutableList<Show>>()

        doc.select(".movie-item, .poster, [class*=film]").forEach { el ->
            val title = el.select("a[title], .title, h2").firstOrNull()?.text() ?: return@forEach
            val genre = el.select(".genre, [class*=cat]").firstOrNull()?.text() ?: "Кино"
            val pageUrl = el.select("a").firstOrNull()?.attr("abs:href") ?: ""
            val posterUrl = el.select("img").firstOrNull()?.attr("abs:src") ?: ""

            genreMap.getOrPut(mapGenre(genre)) { mutableListOf() }.add(
                Show(title = title, posterUrl = posterUrl,
                     episodes = listOf(Episode(title = title, pageUrl = pageUrl, durationMin = 100)))
            )
        }
        return if (genreMap.isNotEmpty()) buildChannelsFromGenreMap(genreMap) else getDemoChannels()
    }

    // ─────────────────────────────────────────────────────────
    // ANIMEGO
    // ─────────────────────────────────────────────────────────
    private fun parseAnimeGo(baseUrl: String): List<Channel> {
        val html = fetch("$baseUrl/anime/")
        val doc = Jsoup.parse(html)
        val genreMap = mutableMapOf<String, MutableList<Show>>()

        doc.select(".anime-item, [class*=anime]").forEach { el ->
            val title = el.select("a, .title").firstOrNull()?.text() ?: return@forEach
            val genre = el.select(".genre").firstOrNull()?.text() ?: "Аниме"
            val pageUrl = el.select("a[href]").firstOrNull()?.attr("abs:href") ?: ""
            val posterUrl = el.select("img").firstOrNull()?.attr("abs:src") ?: ""
            val eps = (1..12).map { ep ->
                Episode(title = "Эпизод $ep", pageUrl = "$pageUrl/episode-$ep", durationMin = 24)
            }
            genreMap.getOrPut(mapGenre(genre)) { mutableListOf() }.add(
                Show(title = title, posterUrl = posterUrl, episodes = eps)
            )
        }
        return if (genreMap.isNotEmpty()) buildChannelsFromGenreMap(genreMap)
               else getAnimeChannels()
    }

    // ─────────────────────────────────────────────────────────
    // CUSTOM — универсальный парсер
    // ─────────────────────────────────────────────────────────
    private fun parseCustom(url: String): List<Channel> {
        val html = fetch(url)
        val doc = Jsoup.parse(html)
        val genreMap = mutableMapOf<String, MutableList<Show>>()

        // Ищем ссылки на контент: фильмы/сериалы обычно содержат poster/img + title
        doc.select("a:has(img)").forEach { el ->
            val title = el.attr("title").takeIf { it.isNotBlank() }
                ?: el.select("img").firstOrNull()?.attr("alt")
                ?: el.text().takeIf { it.isNotBlank() }
                ?: return@forEach
            if (title.length < 3) return@forEach

            val pageUrl = el.attr("abs:href")
            val posterUrl = el.select("img").firstOrNull()?.attr("abs:src") ?: ""

            // Пробуем угадать жанр по тексту рядом
            val parent = el.parent()
            val genreText = parent?.select("[class*=genre],[class*=cat],[class*=type]")
                              ?.firstOrNull()?.text() ?: "Видео"

            genreMap.getOrPut(mapGenre(genreText)) { mutableListOf() }.add(
                Show(title = title, posterUrl = posterUrl,
                     episodes = listOf(Episode(title = title, pageUrl = pageUrl, durationMin = 60)))
            )
        }
        return if (genreMap.size >= 2) buildChannelsFromGenreMap(genreMap)
               else getDemoChannels()
    }

    // ─────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────
    private fun fetch(url: String): String {
        val req = Request.Builder().url(url).build()
        return client.newCall(req).execute().use { it.body?.string() ?: "" }
    }

    private fun mapGenre(raw: String): String {
        val lower = raw.lowercase()
        return when {
            lower.contains("боевик") || lower.contains("action") || lower.contains("экшн") -> "БОЕВИК"
            lower.contains("фантаст") || lower.contains("sci-fi") || lower.contains("fiction") -> "ФАНТАСТИКА"
            lower.contains("драм") || lower.contains("drama") -> "ДРАМА"
            lower.contains("комед") || lower.contains("comedy") -> "КОМЕДИЯ"
            lower.contains("ужас") || lower.contains("horror") -> "УЖАСЫ"
            lower.contains("триллер") || lower.contains("thriller") -> "ТРИЛЛЕР"
            lower.contains("мелодрам") || lower.contains("romance") -> "МЕЛОДРАМА"
            lower.contains("мульт") || lower.contains("animation") || lower.contains("anime") -> "АНИМЕ"
            lower.contains("докум") || lower.contains("documentary") -> "ДОКУМЕНТАЛЬНОЕ"
            lower.contains("истор") || lower.contains("history") -> "ИСТОРИЧЕСКИЕ"
            lower.contains("крим") || lower.contains("crime") -> "КРИМИНАЛ"
            lower.contains("приключ") || lower.contains("adventure") -> "ПРИКЛЮЧЕНИЯ"
            else -> raw.uppercase().take(15)
        }
    }

    private fun buildChannelsFromShows(shows: List<Show>): List<Channel> {
        // Если жанры неизвестны — раскидываем по алфавиту на несколько каналов
        val chunkSize = (shows.size / 4).coerceAtLeast(5)
        return shows.chunked(chunkSize).mapIndexed { i, chunk ->
            val names = listOf("КАНАЛ А", "КАНАЛ Б", "КАНАЛ В", "КАНАЛ Г", "КАНАЛ Д")
            Channel(id = i, name = names.getOrElse(i) { "КАНАЛ ${i+1}" },
                    emoji = "📺", colorHex = "#111118", shows = chunk)
        }
    }

    private fun buildChannelsFromGenreMap(genreMap: Map<String, List<Show>>): List<Channel> {
        val colorMap = mapOf(
            "БОЕВИК" to Pair("💥", "#1a0500"), "ФАНТАСТИКА" to Pair("🚀", "#00091a"),
            "ДРАМА" to Pair("🎭", "#0f001a"), "КОМЕДИЯ" to Pair("😄", "#0a1200"),
            "УЖАСЫ" to Pair("👻", "#0d0005"), "ТРИЛЛЕР" to Pair("🔪", "#100a00"),
            "МЕЛОДРАМА" to Pair("💕", "#1a0010"), "АНИМЕ" to Pair("⛩", "#150010"),
            "ДОКУМЕНТАЛЬНОЕ" to Pair("🌍", "#001212"), "ИСТОРИЧЕСКИЕ" to Pair("🏛", "#1a1000"),
            "КРИМИНАЛ" to Pair("🕵️", "#0d0d00"), "ПРИКЛЮЧЕНИЯ" to Pair("⚔", "#001a0a")
        )

        return genreMap.entries
            .filter { it.value.isNotEmpty() }
            .sortedByDescending { it.value.size }
            .take(12)
            .mapIndexed { i, (genre, shows) ->
                val (emoji, color) = colorMap[genre] ?: Pair("📺", "#111118")
                Channel(id = i, name = genre, emoji = emoji, colorHex = color, shows = shows)
            }
    }

    // ─────────────────────────────────────────────────────────
    // DEMO DATA (когда сайт недоступен или парсинг не удался)
    // ─────────────────────────────────────────────────────────
    fun getDemoChannels(): List<Channel> = listOf(
        Channel(0, "БОЕВИК", "💥", "#1a0500", listOf(
            Show("Ходячие мертвецы", episodes = listOf(
                Episode("S11E01 — Конец и начало", durationMin = 45),
                Episode("S11E02 — Подзарядка", durationMin = 45),
                Episode("S11E03 — Очаги", durationMin = 45)
            )),
            Show("Игра Престолов", episodes = listOf(
                Episode("S08E01 — Зима наступила", durationMin = 60),
                Episode("S08E02 — Рыцарь семи королевств", durationMin = 60),
                Episode("S08E03 — Длинная ночь", durationMin = 82)
            )),
            Show("24", episodes = listOf(
                Episode("S08E01 — 1:00–2:00", durationMin = 44),
                Episode("S08E02 — 2:00–3:00", durationMin = 44)
            ))
        )),
        Channel(1, "ФАНТАСТИКА", "🚀", "#00091a", listOf(
            Show("Мандалорец", episodes = listOf(
                Episode("S03E01 — Дорога", durationMin = 40),
                Episode("S03E02 — Мины Мандалора", durationMin = 42),
                Episode("S03E03 — Гавань", durationMin = 38)
            )),
            Show("Очень странные дела", episodes = listOf(
                Episode("S04E01 — Hellfire Club", durationMin = 75),
                Episode("S04E02 — Vecna's Curse", durationMin = 64)
            )),
            Show("Westworld", episodes = listOf(
                Episode("S04E01 — Начало конца", durationMin = 60),
                Episode("S04E02 — Боль", durationMin = 55)
            ))
        )),
        Channel(2, "ДРАМА", "🎭", "#0f001a", listOf(
            Show("Во все тяжкие", episodes = listOf(
                Episode("S05E01 — Вернуться", durationMin = 47),
                Episode("S05E02 — Мадригал", durationMin = 47),
                Episode("S05E03 — Шабаш", durationMin = 47)
            )),
            Show("Чернобыль", episodes = listOf(
                Episode("S01E01 — 1:23:45", durationMin = 60),
                Episode("S01E02 — Пожалуйста, оставайтесь", durationMin = 65)
            ))
        )),
        Channel(3, "КОМЕДИЯ", "😄", "#0a1200", listOf(
            Show("Теория большого взрыва", episodes = listOf(
                Episode("S12E01 — Потеря пространства", durationMin = 22),
                Episode("S12E02 — Возбуждение ведьм", durationMin = 22),
                Episode("S12E03 — Эксперимент партнёрства", durationMin = 22)
            )),
            Show("Офис", episodes = listOf(
                Episode("S09E01 — Новый директор", durationMin = 22),
                Episode("S09E02 — Рой", durationMin = 22)
            ))
        )),
        Channel(4, "КРИМИНАЛ", "🕵️", "#0d0d00", listOf(
            Show("Острые козырьки", episodes = listOf(
                Episode("S06E01 — Золото в руинах", durationMin = 60),
                Episode("S06E02 — Информаторы", durationMin = 55)
            )),
            Show("Нарко", episodes = listOf(
                Episode("S03E01 — Мир рухнул", durationMin = 55),
                Episode("S03E02 — Тихая война", durationMin = 52)
            ))
        )),
        Channel(5, "АНИМЕ", "⛩", "#150010", listOf(
            Show("Атака Титанов", episodes = listOf(
                Episode("S04E25 — Rumbling", durationMin = 25),
                Episode("S04E26 — Двор", durationMin = 25)
            )),
            Show("Клинок, рассекающий демонов", episodes = listOf(
                Episode("S03E01 — Кузнечная деревня", durationMin = 24),
                Episode("S03E02 — Загрязнённый", durationMin = 24)
            ))
        ))
    )

    private fun getAnimeChannels(): List<Channel> = listOf(
        Channel(0, "СЁНЭН", "⚔", "#1a0010", listOf(
            Show("Атака Титанов", episodes = (1..13).map { Episode("Эпизод $it", durationMin = 24) }),
            Show("Наруто", episodes = (1..20).map { Episode("Эпизод $it", durationMin = 23) })
        )),
        Channel(1, "СЭЙНЭН", "🌸", "#001018", listOf(
            Show("Токийский гуль", episodes = (1..12).map { Episode("Эпизод $it", durationMin = 24) }),
            Show("Виолетта Эвергарден", episodes = (1..13).map { Episode("Эпизод $it", durationMin = 24) })
        ))
    )
}
