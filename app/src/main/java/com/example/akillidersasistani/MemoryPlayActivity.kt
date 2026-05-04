package com.example.akillidersasistani

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.akillidersasistani.databinding.ActivityMemoryPlayBinding

class MemoryPlayActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMemoryPlayBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMemoryPlayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Mevcut Seviyeyi Yükle
        val prefs = getSharedPreferences("UserProfile", Context.MODE_PRIVATE)
        val unlockedLevel = prefs.getInt("memory_unlocked_level", 1)
        binding.txtMemoryUnlockedLevel.text = "LVL ${String.format("%02d", unlockedLevel)}"

        // Sıralama Butonu
        binding.btnShowMemoryRankings.setOnClickListener {
            val intent = Intent(this, FocusGameActivity::class.java)
            startActivity(intent)
        }

        // Oyunu Başlat
        binding.btnConfirmMemoryStart.setOnClickListener {
            val intent = Intent(this, MemoryMatchActivity::class.java)
            // Slider değerini ve seviyeyi gönderiyoruz
            intent.putExtra("GAME_SPEED", binding.speedSlider.value)
            intent.putExtra("LEVEL_ID", unlockedLevel)
            startActivity(intent)
            finish() // Hazırlık ekranını kapat
        }
    }
}