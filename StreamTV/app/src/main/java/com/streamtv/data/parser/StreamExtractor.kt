package com.streamtv.data.parser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * StreamExtractor — извлекает прямую ссылку на видеопоток со страницы.
 *
 * Ищет:
 *  1. Прямые .m3u8 / .mp4 ссылки в HTML/JS
 *  2. Параметры iframe плееров
 *  3. JSON-конфигурации видеоплееров
 */
object StreamExtractor {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .header("Referer", chain.request().url.toString())
                .build()
            chain.proceed(req)
        }
        .build()

    suspend fun extract(pageUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val html = fetch(pageUrl)

            // 1. Ищем прямые HLS ссылки
            findPattern(html, """["'](https?://[^"']+\.m3u8[^"']*)["']""")?.let { return@withContext it }

            // 2. Ищем прямые MP4 ссылки
            findPattern(html, """["'](https?://[^"']+\.mp4[^"']*)["']""")?.let { return@withContext it }

            // 3. Ищем iframe с плеером
            val doc = Jsoup.parse(html)
            val iframeSrc = doc.select("iframe[src*=player], iframe[src*=video], iframe[src*=embed]")
                .firstOrNull()?.attr("abs:src")
            if (iframeSrc != null) {
                val iframeHtml = fetch(iframeSrc)
                findPattern(iframeHtml, """["'](https?://[^"']+\.m3u8[^"']*)["']""")?.let { return@withContext it }
                findPattern(iframeHtml, """["'](https?://[^"']+\.mp4[^"']*)["']""")?.let { return@withContext it }
            }

            // 4. Ищем конфигурацию плееров (jwplayer, videojs, etc.)
            findPattern(html, """"file"\s*:\s*"(https?://[^"]+)"""")?.let { return@withContext it }
            findPattern(html, """'file'\s*:\s*'(https?://[^']+)'""")?.let { return@withContext it }
            findPattern(html, """"src"\s*:\s*"(https?://[^"]+\.(?:m3u8|mp4)[^"]*)"""")?.let { return@withContext it }

            // 5. video src
            doc.select("video source[src], video[src]")
                .firstOrNull()?.let { el ->
                    (el.attr("abs:src").takeIf { it.isNotBlank() })?.let { return@withContext it }
                }

            null
        } catch (e: Exception) {
            null
        }
    }

    private fun fetch(url: String): String {
        val req = Request.Builder().url(url).build()
        return client.newCall(req).execute().use { it.body?.string() ?: "" }
    }

    private fun findPattern(html: String, regex: String): String? {
        val matcher = Pattern.compile(regex).matcher(html)
        return if (matcher.find()) matcher.group(1) else null
    }
}
