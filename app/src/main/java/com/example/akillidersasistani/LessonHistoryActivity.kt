package com.example.akillidersasistani

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.akillidersasistani.databinding.ActivityLessonHistoryBinding
import com.example.akillidersasistani.databinding.ItemHistoryCardBinding
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * =========================================================================================
 * PROJECT: AKILLI DERS ASISTANI - NEURAL ARCHIVE ENGINE v2.0
 *
 * UPDATES:
 * - Senin istediğin özel tarih formatı eklendi.
 * - Adapter içindeki tarih motoru güncellendi.
 * - LessonReportActivity'ye veri aktarımı optimize edildi.
 * =========================================================================================
 */
class LessonHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLessonHistoryBinding
    private val database = FirebaseDatabase.getInstance().reference
    private val historyList = mutableListOf<ReportModel>()
    private lateinit var historyAdapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLessonHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        fetchHistoryFromNeuralArchive()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            triggerIndustrialHaptic(20)
            onBackPressedDispatcher.onBackPressed()
        }

        // ADAPTER BAŞLATMA
        historyAdapter = HistoryAdapter(historyList) { report ->
            triggerIndustrialHaptic(40)

            // KARTA TIKLANDIĞINDA: Rapor ekranını "Arşiv Modunda" açar.
            val intent = Intent(this, LessonReportActivity::class.java).apply {
                putExtra("AI_RESULT", report.rawContent)
                putExtra("LESSON_TITLE", report.title)
                putExtra("IS_FROM_HISTORY", true) // Rapor ekranına "hazır veriyi bas" komutu
            }
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        binding.rvHistory.apply {
            layoutManager = LinearLayoutManager(this@LessonHistoryActivity)
            adapter = historyAdapter
            setHasFixedSize(true)
        }
    }

    private fun fetchHistoryFromNeuralArchive() {
        val prefs = getSharedPreferences("UserProfile", Context.MODE_PRIVATE)
        val studentNo = prefs.getString("ogrenci_no", null)

        if (studentNo == null) {
            Toast.makeText(this, "Öğrenci kimliği bulunamadı.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.cardLoading.visibility = View.VISIBLE
        binding.layoutEmpty.visibility = View.GONE

        database.child("lesson_reports").child(studentNo)
            .orderByChild("timestamp")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    historyList.clear()
                    if (snapshot.exists()) {
                        for (data in snapshot.children) {
                            try {
                                val report = data.getValue(ReportModel::class.java)
                                report?.let { historyList.add(it) }
                            } catch (e: Exception) {
                                Log.e("NEURAL_HISTORY", "Parsing Error: ${e.message}")
                            }
                        }
                        historyList.sortByDescending { it.timestamp }
                        historyAdapter.notifyDataSetChanged()

                        binding.layoutEmpty.visibility = if (historyList.isEmpty()) View.VISIBLE else View.GONE
                        binding.rvHistory.visibility = if (historyList.isEmpty()) View.GONE else View.VISIBLE
                    } else {
                        binding.layoutEmpty.visibility = View.VISIBLE
                        binding.rvHistory.visibility = View.GONE
                    }
                    binding.cardLoading.visibility = View.GONE
                }

                override fun onCancelled(error: DatabaseError) {
                    binding.cardLoading.visibility = View.GONE
                }
            })
    }

    private fun triggerIndustrialHaptic(ms: Long) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(ms)
        }
    }
}

// --- VERİ MODELİ ---
data class ReportModel(
    val id: String = "",
    val title: String = "",
    val rawContent: String = "",
    val timestamp: Long = 0
)

// --- ADAPTER ---
class HistoryAdapter(
    private val list: List<ReportModel>,
    private val onClick: (ReportModel) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    inner class ViewHolder(val itemBinding: ItemHistoryCardBinding) :
        RecyclerView.ViewHolder(itemBinding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]

        holder.itemBinding.apply {
            // 1. Kapak Başlığı
            txtHistoryTitle.text = item.title

            // 2. SENİN İSTEDİĞİN ÖZEL TARİH FORMATI
            // Örnek Çıktı: 03.05.2026 pazar günü saat 19:34 bunu sordunuz
            try {
                val sdf = SimpleDateFormat("dd.MM.yyyy EEEE 'günü saat' HH:mm", Locale("tr"))
                val formattedDate = sdf.format(Date(item.timestamp))
                txtHistoryDate.text = "$formattedDate bunu sordunuz"
            } catch (e: Exception) {
                txtHistoryDate.text = "Tarih bilgisi alınamadı"
            }

            // Satıra tıklama
            root.setOnClickListener { onClick(item) }
        }
    }

    override fun getItemCount() = list.size
}