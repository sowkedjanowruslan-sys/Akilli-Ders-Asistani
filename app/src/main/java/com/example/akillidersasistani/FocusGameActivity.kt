package com.example.akillidersasistani

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.akillidersasistani.databinding.ActivityFocusGameBinding

class FocusGameActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFocusGameBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFocusGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Buraya ileride Firebase'den veri çekip podyuma (imgRank1, Rank2, Rank3)
        // yerleştirme kodlarını ekleyeceğiz.
    }
}