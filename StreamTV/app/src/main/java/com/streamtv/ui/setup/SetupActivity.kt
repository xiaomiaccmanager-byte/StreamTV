package com.streamtv.ui.setup

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.streamtv.data.model.SourceConfig
import com.streamtv.data.model.SourceType
import com.streamtv.databinding.ActivitySetupBinding
import com.streamtv.ui.main.MainActivity
import com.streamtv.utils.Prefs

class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding

    private val presets = listOf(
        Triple("LostFilm",  "https://www.lostfilm.tv/",  SourceType.LOSTFILM),
        Triple("HDRezka",   "https://rezka.ag/",          SourceType.HDREZKA),
        Triple("Kinogo",    "https://kinogo.co/",         SourceType.KINOGO),
        Triple("AnimeGo",   "https://animego.org/",       SourceType.ANIMEGO)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Если уже был источник — сразу запускаем
        val saved = Prefs.getSource(this)
        if (saved != null) {
            launchMain(saved)
            return
        }

        setupPresets()
        setupInput()
        setupLaunchButton()
    }

    private fun setupPresets() {
        val chips = listOf(
            binding.presetLostfilm,
            binding.presetRezka,
            binding.presetKinogo,
            binding.presetAnime
        )
        chips.forEachIndexed { i, chip ->
            chip.text = presets[i].first
            chip.setOnClickListener {
                chips.forEach { c -> c.isSelected = false }
                chip.isSelected = true
                binding.inputUrl.setText(presets[i].second)
            }
        }
        // По умолчанию выбран LostFilm
        chips[0].isSelected = true
    }

    private fun setupInput() {
        binding.inputUrl.addTextChangedListener {
            val url = it.toString().trim()
            binding.btnLaunch.isEnabled = url.startsWith("http")
        }
    }

    private fun setupLaunchButton() {
        binding.btnLaunch.setOnClickListener {
            val url = binding.inputUrl.text.toString().trim()
            val type = presets.firstOrNull { binding.inputUrl.text.toString().contains(it.second.drop(8).take(10)) }
                ?.third ?: SourceType.CUSTOM
            val name = try { android.net.Uri.parse(url).host ?: url } catch (e: Exception) { url }
            val config = SourceConfig(url = url, name = name, type = type)
            Prefs.saveSource(this, config)
            launchMain(config)
        }

        // Кнопка демо-режима
        binding.btnDemo.setOnClickListener {
            val config = SourceConfig(url = "demo", name = "Demo", type = SourceType.LOSTFILM)
            launchMain(config)
        }
    }

    private fun launchMain(config: SourceConfig) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("source_url",  config.url)
            putExtra("source_name", config.name)
            putExtra("source_type", config.type.name)
        }
        startActivity(intent)
        // Не завершаем SetupActivity, чтобы можно было вернуться
    }
}
