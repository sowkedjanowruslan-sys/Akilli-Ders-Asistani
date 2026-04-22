package com.example.akillidersasistani

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.akillidersasistani.databinding.ActivityMathLeaderboardBinding
import com.google.firebase.database.*

/**
 * MATH WORLD PRO - GLOBAL LEADERBOARD [ULTRA PREMIUM]
 * Tasarım: Apple iOS 18 Game Center Style
 */
class MathLeaderboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMathLeaderboardBinding
    private val leaderList = mutableListOf<Map<String, Any>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMathLeaderboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Toolbar Ayarları
        setupToolbar()

        // Liste Ayarları
        binding.rvLeaderboard.layoutManager = LinearLayoutManager(this)

        // Verileri Çek
        fetchScores()
    }

    private fun setupToolbar() {
        // AppBar kaydırıldığında başlığın Toolbar'da görünmesi için alpha efekti
        binding.appBarLeaderboard.addOnOffsetChangedListener { appBarLayout, verticalOffset ->
            val percentage = Math.abs(verticalOffset).toFloat() / appBarLayout.totalScrollRange
            binding.txtToolbarTitle.alpha = percentage // Kaydırdıkça başlık belirir
        }
    }

    private fun fetchScores() {
        val ref = FirebaseDatabase.getInstance().getReference("MathLeaderboard")

        // En yüksek 20 skoru çek (Firebase küçükten büyüğe sıralar)
        ref.orderByChild("score").limitToLast(20).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (isFinishing || isDestroyed) return

                leaderList.clear()
                for (item in snapshot.children) {
                    val data = item.value as? Map<String, Any>
                    data?.let { leaderList.add(it) }
                }

                // En yüksek skor en üstte görünsün diye listeyi ters çeviriyoruz
                leaderList.reverse()

                setupAdapter()
                binding.loader.visibility = View.GONE
            }

            override fun onCancelled(error: DatabaseError) {
                binding.loader.visibility = View.GONE
            }
        })
    }

    private fun setupAdapter() {
        binding.rvLeaderboard.adapter = object : RecyclerView.Adapter<LeaderboardViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LeaderboardViewHolder {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_leaderboard, parent, false)
                return LeaderboardViewHolder(view)
            }

            override fun onBindViewHolder(holder: LeaderboardViewHolder, position: Int) {
                val item = leaderList[position]
                val rank = position + 1

                // Verileri Yerleştir
                holder.txtRank.text = rank.toString()
                holder.txtName.text = item["name"] as? String ?: "Anonim"
                holder.txtLevel.text = "Seviye ${item["level"] ?: 1}"
                holder.txtScore.text = item["score"].toString()

                // --- PREMIUM DOKUNUŞ: İlk 3 Öğrenci İçin Özel Renkler ---
                when (rank) {
                    1 -> {
                        holder.txtRank.setTextColor(Color.parseColor("#FFD60A")) // Altın
                        holder.txtRank.textSize = 22f
                    }
                    2 -> {
                        holder.txtRank.setTextColor(Color.parseColor("#AEAEB2")) // Gümüş
                        holder.txtRank.textSize = 20f
                    }
                    3 -> {
                        holder.txtRank.setTextColor(Color.parseColor("#FF9500")) // Bronz
                        holder.txtRank.textSize = 19f
                    }
                    else -> {
                        holder.txtRank.setTextColor(Color.parseColor("#007AFF")) // Standart iOS Mavisi
                        holder.txtRank.textSize = 18f
                    }
                }
            }

            override fun getItemCount() = leaderList.size
        }
    }

    // ViewHolder Sınıfı (Performans için)
    class LeaderboardViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val txtRank: TextView = v.findViewById(R.id.txtRank)
        val txtName: TextView = v.findViewById(R.id.txtName)
        val txtLevel: TextView = v.findViewById(R.id.txtLevel)
        val txtScore: TextView = v.findViewById(R.id.txtScore)
    }
}