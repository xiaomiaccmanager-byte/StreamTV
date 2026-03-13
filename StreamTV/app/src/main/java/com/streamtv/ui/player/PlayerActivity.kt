package com.streamtv.ui.player

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.streamtv.databinding.ActivityPlayerBinding
import com.streamtv.data.parser.StreamExtractor
import kotlinx.coroutines.launch

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null

    private val handler = Handler(Looper.getMainLooper())
    private var isControlsVisible = true
    private val hideControlsRunnable = Runnable { hideControls() }

    // Параметры из Intent
    private var channelName  = ""
    private var channelEmoji = ""
    private var showTitle    = ""
    private var episodeTitle = ""
    private var streamUrl    = ""
    private var pageUrl      = ""
    private var durationMin  = 45
    private var progressPct  = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Читаем параметры
        channelName  = intent.getStringExtra("channel_name")  ?: ""
        channelEmoji = intent.getStringExtra("channel_emoji") ?: "📺"
        showTitle    = intent.getStringExtra("show_title")    ?: ""
        episodeTitle = intent.getStringExtra("episode_title") ?: ""
        streamUrl    = intent.getStringExtra("stream_url")    ?: ""
        pageUrl      = intent.getStringExtra("page_url")      ?: ""
        durationMin  = intent.getIntExtra("duration_min", 45)
        progressPct  = intent.getIntExtra("progress_pct", 0)

        setupFullscreen()
        setupUI()
        initPlayer()
    }

    private fun setupFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                hide(WindowInsets.Type.systemBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
    }

    private fun setupUI() {
        // Метаданные
        binding.tvChannelBadge.text = "$channelEmoji $channelName"
        binding.tvShowTitle.text    = showTitle
        binding.tvEpisodeTitle.text = episodeTitle

        // Назад
        binding.btnBack.setOnClickListener { finish() }

        // Пауза/Play
        binding.btnPlayPause.setOnClickListener { togglePlayPause() }

        // Перемотка
        binding.btnRewind.setOnClickListener   { player?.seekBack() }
        binding.btnForward.setOnClickListener  { player?.seekForward() }

        // Следующий эпизод
        binding.btnNext.setOnClickListener     { finish() } // возврат для выбора след.

        // PiP
        binding.btnPip.setOnClickListener      { enterPip() }

        // Тап по экрану — показать/скрыть контролы
        binding.playerContainer.setOnClickListener { toggleControls() }

        // Timeline
        binding.timelineSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                player?.let { p ->
                    p.seekTo((value / 100f * p.duration).toLong())
                }
            }
        }
    }

    private fun initPlayer() {
        player = ExoPlayer.Builder(this).build()
        binding.playerView.player = player

        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> binding.loadingSpinner.visibility = View.VISIBLE
                    Player.STATE_READY     -> {
                        binding.loadingSpinner.visibility = View.GONE
                        // Если есть сохранённый прогресс — перематываем
                        if (progressPct > 0) {
                            player?.seekTo((progressPct / 100f * (player?.duration ?: 0)).toLong())
                        }
                    }
                    Player.STATE_ENDED     -> finish()
                    else -> {}
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                binding.btnPlayPause.setIconResource(
                    if (isPlaying) android.R.drawable.ic_media_pause
                    else android.R.drawable.ic_media_play
                )
                updateProgressLoop()
            }
        })

        // Пытаемся воспроизвести прямую ссылку или парсим страницу
        if (streamUrl.isNotEmpty()) {
            playUrl(streamUrl)
        } else if (pageUrl.isNotEmpty()) {
            extractAndPlay(pageUrl)
        } else {
            binding.tvError.visibility = View.VISIBLE
            binding.tvError.text = "Ссылка на видео не найдена.\nОткройте страницу в браузере."
        }
    }

    private fun playUrl(url: String) {
        val mediaItem = MediaItem.fromUri(url)
        player?.apply {
            setMediaItem(mediaItem)
            prepare()
            play()
        }
    }

    private fun extractAndPlay(pageUrl: String) {
        binding.loadingSpinner.visibility = View.VISIBLE
        lifecycleScope.launch {
            val url = StreamExtractor.extract(pageUrl)
            if (url != null) {
                playUrl(url)
            } else {
                binding.loadingSpinner.visibility = View.GONE
                binding.tvError.visibility = View.VISIBLE
                binding.tvError.text = "Не удалось извлечь видео.\nОткройте страницу в браузере:\n$pageUrl"
            }
        }
    }

    private fun togglePlayPause() {
        player?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    private fun toggleControls() {
        if (isControlsVisible) hideControls() else showControls()
    }

    private fun showControls() {
        isControlsVisible = true
        binding.controlsOverlay.visibility = View.VISIBLE
        binding.controlsOverlay.animate().alpha(1f).setDuration(200).start()
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, 3500)
    }

    private fun hideControls() {
        isControlsVisible = false
        binding.controlsOverlay.animate().alpha(0f).setDuration(300)
            .withEndAction { binding.controlsOverlay.visibility = View.INVISIBLE }
            .start()
    }

    private fun updateProgressLoop() {
        handler.post(object : Runnable {
            override fun run() {
                player?.let { p ->
                    if (p.duration > 0) {
                        val pct = p.currentPosition * 100f / p.duration
                        binding.timelineSlider.value = pct.coerceIn(0f, 100f)
                        binding.tvCurrentTime.text  = formatMs(p.currentPosition)
                        binding.tvTotalTime.text    = formatMs(p.duration)
                    }
                }
                if (player?.isPlaying == true) handler.postDelayed(this, 500)
            }
        })
    }

    private fun formatMs(ms: Long): String {
        val s = ms / 1000
        return "%02d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60)
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
    }
}
