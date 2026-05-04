package com.example.akillidersasistani

import android.content.Context // HATA BURADAYDI, EKLENDİ
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.akillidersasistani.databinding.ActivityFocusPlayBinding

class FocusPlayActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFocusPlayBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFocusPlayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Mevcut Seviyeyi Yükle
        val prefs = getSharedPreferences("UserProfile", Context.MODE_PRIVATE)
        val unlockedLevel = prefs.getInt("focus_unlocked_level", 1)
        binding.txtCurrentUnlockedLevel.text = "LEVEL ${String.format("%02d", unlockedLevel)} UNLOCKED"

        // 2. Yıldız Butonu -> Sıralama (Leaderboard) Ekranı
        binding.btnShowRankings.setOnClickListener {
            val intent = Intent(this, FocusGameActivity::class.java)
            startActivity(intent)
        }

        // 3. Başlat Butonu -> Asıl Oyuna (FocusMatrixActivity) Geçiş
        binding.btnConfirmCalibration.setOnClickListener {
            val speed = binding.speedSlider.value
            val intent = Intent(this, FocusMatrixActivity::class.java)
            intent.putExtra("LEVEL_ID", unlockedLevel)
            intent.putExtra("GAME_SPEED", speed) // Kaydırdığın hızı oyuna gönderiyoruz
            startActivity(intent)
            finish()
        }
    }
}