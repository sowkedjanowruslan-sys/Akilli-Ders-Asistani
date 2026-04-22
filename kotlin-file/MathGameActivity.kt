package com.example.akillidersasistani
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.akillidersasistani.databinding.ActivityMathGameBinding

class MathGameActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMathGameBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMathGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBackFromGame.setOnClickListener { finish() }
// MathGameActivity.kt içinde onCreate veya initViews kısmına ekleyin
        binding.btnOpenLeaderboard.setOnClickListener {
            // Tıklama Titreşimi
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)

            // Sıralama Sayfasına Geçiş
            val intent = Intent(this, MathLeaderboardActivity::class.java)
            startActivity(intent)

            // Premium Geçiş Animasyonu
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        // 1. Öğrencinin açtığı en son seviyeyi SharedPreferences'tan oku
        val sharedPref = getSharedPreferences("UserProfile", Context.MODE_PRIVATE)
        val unlockedLevel = sharedPref.getInt("math_unlocked_level", 1) // Eğer yoksa 1. seviye açıktır

        val levels = (1..100).toList()
        binding.rvMathLevels.layoutManager = GridLayoutManager(this, 3)
        binding.rvMathLevels.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_math_level, parent, false)
                return object : RecyclerView.ViewHolder(v) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val levelNum = levels[position]
                val txt = holder.itemView.findViewById<TextView>(R.id.txtLevelNumber)
                val card = holder.itemView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.levelCard)

                txt.text = levelNum.toString()

                // 2. KONTROL: Seviye numarası, açılmış olan seviyeden küçük veya eşit mi?
                if (levelNum <= unlockedLevel) {
                    // SEVİYE AÇIK (Premium Blue Look)
                    card.setCardBackgroundColor(ColorStateList.valueOf(Color.parseColor("#007AFF")))
                    txt.setTextColor(Color.WHITE)
                    card.alpha = 1.0f

                    holder.itemView.setOnClickListener {
                        holder.itemView.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        val intent = Intent(this@MathGameActivity, MathPlayActivity::class.java)
                        intent.putExtra("LEVEL_ID", levelNum)
                        startActivity(intent)
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    }
                } else {
                    // SEVİYE KİLİTLİ (Gray & Mat Look)
                    card.setCardBackgroundColor(ColorStateList.valueOf(Color.parseColor("#F2F2F7")))
                    txt.setTextColor(Color.parseColor("#8E8E93"))
                    card.alpha = 0.5f // Daha estetik durması için hafif şeffaflık

                    holder.itemView.setOnClickListener {
                        Toast.makeText(this@MathGameActivity, "🔒 Bu seviyeyi açmak için önceki seviyede 50 puan yapmalısın!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            override fun getItemCount() = levels.size
        }
    }

    // 3. KRİTİK EKLEME: Oyundan dönünce kilitlerin açıldığını görmek için listeyi yenile
    override fun onResume() {
        super.onResume()
        setupRecyclerView()
    }
}