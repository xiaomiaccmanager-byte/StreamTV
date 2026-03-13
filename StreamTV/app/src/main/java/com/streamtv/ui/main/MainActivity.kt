package com.streamtv.ui.main

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.streamtv.R
import com.streamtv.data.model.*
import com.streamtv.data.parser.ContentParser
import com.streamtv.data.scheduler.EpgScheduler
import com.streamtv.databinding.ActivityMainBinding
import com.streamtv.ui.player.PlayerActivity
import com.streamtv.utils.Prefs
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var channelAdapter: ChannelAdapter
    private lateinit var epgAdapter: EpgAdapter

    private var channels: List<Channel> = emptyList()
    private var currentChannelIndex = 0
    private val handler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            updatePlayerProgress()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sourceUrl  = intent.getStringExtra("source_url")  ?: "demo"
        val sourceName = intent.getStringExtra("source_name") ?: "StreamTV"
        val sourceType = SourceType.valueOf(intent.getStringExtra("source_type") ?: "LOSTFILM")

        setupUI(sourceName)
        loadChannels(SourceConfig(sourceUrl, sourceName, sourceType))
    }

    private fun setupUI(sourceName: String) {
        // Source label
        binding.tvSourceName.text = sourceName
        binding.tvSourceName.setOnClickListener {
            Prefs.clearSource(this)
            finish()
        }

        // Кнопки управления
        binding.btnPrevCh.setOnClickListener { switchChannel(currentChannelIndex - 1) }
        binding.btnNextCh.setOnClickListener { switchChannel(currentChannelIndex + 1) }
        binding.btnWatch.setOnClickListener  { openPlayer() }
        binding.playerPreview.setOnClickListener { openPlayer() }

        // Channel list
        channelAdapter = ChannelAdapter { idx -> switchChannel(idx) }
        binding.rvChannels.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = channelAdapter
        }

        // EPG list
        epgAdapter = EpgAdapter { item -> openPlayerWithItem(item) }
        binding.rvEpg.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = epgAdapter
        }

        // EPG tabs
        binding.tabToday.setOnClickListener    { selectEpgTab(0) }
        binding.tabTomorrow.setOnClickListener { selectEpgTab(1) }

        // Volume
        binding.volumeSlider.addOnChangeListener { _, value, _ ->
            // Здесь подключим к ExoPlayer в PlayerActivity
        }
    }

    private fun loadChannels(config: SourceConfig) {
        binding.loadingContainer.visibility = View.VISIBLE
        binding.mainContent.visibility = View.GONE

        lifecycleScope.launch {
            val parser = ContentParser()
            channels = if (config.url == "demo") {
                parser.getDemoChannels()
            } else {
                parser.parse(config)
            }

            binding.loadingContainer.visibility = View.GONE
            binding.mainContent.visibility = View.VISIBLE

            channelAdapter.submitList(channels)
            binding.tvChannelCount.text = "${channels.size}"

            if (channels.isNotEmpty()) {
                switchChannel(0)
            }
            handler.post(clockRunnable)
        }
    }

    private fun switchChannel(index: Int) {
        if (channels.isEmpty()) return
        currentChannelIndex = index.coerceIn(0, channels.size - 1)
        val ch = channels[currentChannelIndex]

        // Обновляем sidebar
        channelAdapter.setActive(currentChannelIndex)
        binding.rvChannels.scrollToPosition(currentChannelIndex)

        // Обновляем превью плеера
        val current = EpgScheduler.getCurrentItem(ch)
        current?.let {
            binding.tvNowChannel.text  = "${ch.emoji} ${ch.name}"
            binding.tvNowTitle.text    = it.show.title
            binding.tvNowEpisode.text  = it.episode.title
            binding.tvTimeLeft.text    = "${it.minutesLeft}м до конца"
            updatePlayerProgress()
        }

        // Обновляем EPG
        renderEpg(ch)

        // Обновляем Quick-Nav кнопки в topbar
        updateQuickNav()
    }

    private fun renderEpg(channel: Channel) {
        val items = EpgScheduler.getUpcoming(channel, 12)
        epgAdapter.submitList(items)
        // Скролл к текущей передаче
        val nowIdx = items.indexOfFirst { it.isNow }
        if (nowIdx >= 0) {
            binding.rvEpg.scrollToPosition((nowIdx - 1).coerceAtLeast(0))
        }

        // Заголовок EPG
        val cal = Calendar.getInstance()
        val days = arrayOf("Воскресенье","Понедельник","Вторник","Среда","Четверг","Пятница","Суббота")
        val months = arrayOf("янв","фев","мар","апр","май","июн","июл","авг","сен","окт","ноя","дек")
        binding.tvEpgDate.text = "${days[cal.get(Calendar.DAY_OF_WEEK)-1]}, ${cal.get(Calendar.DAY_OF_MONTH)} ${months[cal.get(Calendar.MONTH)]}"
    }

    private fun openPlayer() {
        val ch = channels.getOrNull(currentChannelIndex) ?: return
        val current = EpgScheduler.getCurrentItem(ch) ?: return
        startPlayerActivity(ch, current)
    }

    private fun openPlayerWithItem(item: EpgItem) {
        val ch = channels.getOrNull(currentChannelIndex) ?: return
        startPlayerActivity(ch, item)
    }

    private fun startPlayerActivity(channel: Channel, item: EpgItem) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("channel_name",  channel.name)
            putExtra("channel_emoji", channel.emoji)
            putExtra("show_title",    item.show.title)
            putExtra("episode_title", item.episode.title)
            putExtra("stream_url",    item.episode.streamUrl)
            putExtra("page_url",      item.episode.pageUrl)
            putExtra("duration_min",  item.episode.durationMin)
            putExtra("progress_pct",  item.progressPercent)
        }
        startActivity(intent)
    }

    private fun updateQuickNav() {
        val quickBtns = listOf(
            binding.quickCh0, binding.quickCh1, binding.quickCh2, binding.quickCh3
        )
        quickBtns.forEachIndexed { i, btn ->
            val ch = channels.getOrNull(i)
            btn.visibility = if (ch != null) View.VISIBLE else View.GONE
            btn.text = ch?.name?.take(8) ?: ""
            btn.isSelected = i == currentChannelIndex
            btn.setOnClickListener { switchChannel(i) }
        }
    }

    private fun updateClock() {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        binding.tvClock.text = sdf.format(Date())
    }

    private fun updatePlayerProgress() {
        val ch = channels.getOrNull(currentChannelIndex) ?: return
        val item = EpgScheduler.getCurrentItem(ch) ?: return
        binding.progressBar.progress = item.progressPercent
        binding.tvTimeLeft.text = "${item.minutesLeft}м до конца"
    }

    private fun selectEpgTab(tab: Int) {
        binding.tabToday.isSelected    = tab == 0
        binding.tabTomorrow.isSelected = tab == 1
        // TODO: переключение расписания на завтра
    }

    override fun onResume() {
        super.onResume()
        handler.post(clockRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(clockRunnable)
    }
}
