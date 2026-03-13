package com.streamtv.parser
import android.graphics.Color
import com.streamtv.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

class LostFilmParser {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val r = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Android 13) Chrome/120.0")
                .build()
            chain.proceed(r)
        }.build()

    val defaultChannels = listOf(
        Channel(0, "БОЕВИК",         "💥", "action",     Color.parseColor("#1a0500")),
        Channel(1, "ФАНТАСТИКА",     "🚀", "scifi",      Color.parseColor("#00081a")),
        Channel(2, "ДРАМА",          "🎭", "drama",      Color.parseColor("#1a001a")),
        Channel(3, "КОМЕДИЯ",        "😄", "comedy",     Color.parseColor("#001200")),
        Channel(4, "КРИМИНАЛ",       "🕵", "crime",      Color.parseColor("#0d0d00")),
        Channel(5, "МИСТИКА",        "👻", "mystery",    Color.parseColor("#00001a")),
        Channel(6, "АНИМЕ",          "⛩",  "anime",      Color.parseColor("#1a0010")),
        Channel(7, "ДОКУМЕНТАЛЬНОЕ", "📽", "documentary",Color.parseColor("#001010")),
    )

    suspend fun loadShows(baseUrl: String): List<Show> = withContext(Dispatchers.IO) {
        try {
            val html = fetch("$baseUrl/series/")
            val doc = Jsoup.parse(html)
            val items = doc.select(".card-series, .bb3, [class*=series]")
            if (items.isEmpty()) return@withContext fallbackShows()
            items.take(80).mapNotNull { el ->
                try {
                    val title = el.select(".title-en, .title, h2, h3").firstOrNull()?.text()?.trim() ?: return@mapNotNull null
                    val poster = el.select("img").firstOrNull()?.attr("src")?.let { absoluteUrl(baseUrl, it) } ?: ""
                    val year = el.select(".year, .date").firstOrNull()?.text()?.trim() ?: ""
                    val desc = el.select(".description, p").firstOrNull()?.text()?.trim() ?: ""
                    val href = el.select("a").firstOrNull()?.attr("href")?.let { absoluteUrl(baseUrl, it) } ?: ""
                    val genres = el.select(".category, .genre").firstOrNull()?.text()?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                    Show(title, poster, year, desc, listOf(Episode("S01E01 — Пилот", "", 45, pageUrl = href)), genres)
                } catch (e: Exception) { null }
            }
        } catch (e: Exception) { fallbackShows() }
    }

    fun assignShowsToChannels(shows: List<Show>, channels: List<Channel>): List<Channel> {
        val genreMap = mapOf(
            "боевик" to 0, "экшн" to 0, "action" to 0,
            "фантастика" to 1, "scifi" to 1, "фэнтези" to 1,
            "драма" to 2, "drama" to 2, "мелодрама" to 2,
            "комедия" to 3, "comedy" to 3,
            "криминал" to 4, "crime" to 4, "детектив" to 4, "триллер" to 4,
            "мистика" to 5, "mystery" to 5, "ужасы" to 5,
            "аниме" to 6, "anime" to 6,
            "документальный" to 7, "documentary" to 7
        )
        val buckets = Array(channels.size) { mutableListOf<Show>() }
        shows.forEach { show ->
            var assigned = false
            for (genre in show.genres) {
                val key = genre.lowercase()
                val idx = genreMap.entries.firstOrNull { key.contains(it.key) }?.value
                if (idx != null && idx < buckets.size) { buckets[idx].add(show); assigned = true; break }
            }
            if (!assigned) buckets[(show.title.hashCode() and 0x7FFFFFFF) % buckets.size].add(show)
        }
        return channels.mapIndexed { i, ch -> ch.copy(shows = buckets[i].ifEmpty { fallbackShowsForChannel(ch) }) }
    }

    suspend fun resolveStreamUrl(pageUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val html = fetch(pageUrl)
            val doc = Jsoup.parse(html)
            listOf(
                doc.select("source[type*=mp4],source[type*=hls]").map { it.attr("src") },
                doc.select("video").map { it.attr("src") },
                Regex(""""(https?://[^"]+\.m3u8[^"]*)"""").findAll(html).map { it.groupValues[1] }.toList(),
                Regex(""""(https?://[^"]+\.mp4[^"]*)"""").findAll(html).map { it.groupValues[1] }.toList()
            ).flatten().firstOrNull { it.startsWith("http") }
        } catch (e: Exception) { null }
    }

    private fun fetch(url: String): String {
        val req = Request.Builder().url(url).build()
        return client.newCall(req).execute().use { it.body?.string() ?: "" }
    }

    private fun absoluteUrl(base: String, href: String): String {
        if (href.startsWith("http")) return href
        return if (href.startsWith("/")) "${base.trimEnd('/')}$href" else "${base.trimEnd('/')}/$href"
    }

    fun fallbackShows() = listOf(
        Show("Ходячие мертвецы", "", "2010", "Зомби", listOf(Episode("S11E01", "", 45)), listOf("боевик","ужасы")),
        Show("Игра Престолов",   "", "2011", "Трон",  listOf(Episode("S08E01", "", 60)), listOf("фэнтези","драма")),
        Show("Во все тяжкие",    "", "2008", "Хайзенберг", listOf(Episode("S05E01", "", 47)), listOf("драма","криминал")),
        Show("Мандалорец",       "", "2019", "Звёздные войны", listOf(Episode("S03E01", "", 40)), listOf("фантастика")),
        Show("Теория большого взрыва","","2007","Учёные", listOf(Episode("S12E01","",22)), listOf("комедия")),
        Show("Острые козырьки",  "", "2013", "Бирмингем", listOf(Episode("S06E01","",55)), listOf("криминал","драма")),
        Show("Очень странные дела","","2016","Хоукинс", listOf(Episode("S04E01","",75)), listOf("мистика","фантастика")),
        Show("Нарко",            "", "2015", "Эскобар", listOf(Episode("S03E01","",55)), listOf("криминал","драма")),
        Show("Чернобыль",        "", "2019", "1986", listOf(Episode("S01E01","",60)), listOf("драма","документальный")),
        Show("Атака Титанов",    "", "2013", "Война", listOf(Episode("S04E25","",25)), listOf("аниме","боевик")),
        Show("Эйфория",          "", "2019", "Подростки", listOf(Episode("S02E01","",55)), listOf("драма")),
        Show("Westworld",        "", "2016", "Роботы", listOf(Episode("S04E01","",60)), listOf("фантастика","драма")),
        Show("Настоящий детектив","","2014","Расследования",listOf(Episode("S04E01","",55)),listOf("криминал","мистика")),
        Show("Наша планета",     "", "2019", "Природа", listOf(Episode("S02E01","",50)), listOf("документальный")),
    )

    private fun fallbackShowsForChannel(ch: Channel) =
        fallbackShows().filter { s -> s.genres.any { it.contains(ch.sourceCategory, ignoreCase=true) } }
            .ifEmpty { fallbackShows().shuffled().take(3) }
}
