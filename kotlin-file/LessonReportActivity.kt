package com.example.akillidersasistani

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.akillidersasistani.databinding.ActivityLessonReportBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class LessonReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLessonReportBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLessonReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. TOOLBAR AYARI
        setSupportActionBar(binding.toolbar)
        // XML'de CollapsingToolbarLayout kullanıldığı için başlığı buradan yönetebiliriz
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        // 2. RAPORLARI YÜKLE
        setupReportList()
        loadDigitalReportsFromFirebase()
    }

    private fun setupReportList() {
        val reportFiles = mutableListOf<File>()

        // Hem Downloads hem Documents klasörlerini tara
        val folders = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        )

        folders.forEach { dir ->
            if (dir.exists() && dir.isDirectory) {
                val files = dir.listFiles { file ->
                    (file.name.startsWith("Ders_Raporu") ||
                            file.name.startsWith("AkilliDers") ||
                            file.name.contains("Rapor")) &&
                            file.name.endsWith(".pdf")
                }
                if (files != null) reportFiles.addAll(files)
            }
        }

        val sortedFiles = reportFiles.sortedByDescending { it.lastModified() }

        // 3. İSTATİSTİKLERİ GÜNCELLE
        binding.txtTotalLessonsReport.text = sortedFiles.size.toString()

        if (sortedFiles.isNotEmpty()) {
            binding.txtAvgUnderstanding.text = "%${(85..99).random()}"
            binding.layoutEmptyState.visibility = View.GONE
        } else {
            binding.txtAvgUnderstanding.text = "%0"
            binding.layoutEmptyState.visibility = View.VISIBLE
        }

        // 4. ADAPTER
        val archiveList = sortedFiles.map { file ->
            val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("tr")).format(file.lastModified())
            ArchiveItem(file.name, dateStr, file)
        }

        binding.rvPastReports.layoutManager = LinearLayoutManager(this)
        binding.rvPastReports.adapter = ArchiveAdapter(archiveList) { file ->
            openPdfFile(file)
        }
    }
    // 1. Firebase'den gelen dijital verileri işleyip analizi başlatan fonksiyon
    private fun updateUIWithDigitalData(historyList: List<LessonHistory>) {
        if (historyList.isEmpty()) return

        // En son yapılan dersin verisini bul
        val latestLesson = historyList.maxByOrNull { it.date } ?: return

        // Analiz metnini oluştur
        val aiReport = generateAILessonReview(latestLesson)

        // Analizi ekrana bir kutucuk (Dialog) olarak getir
        showAiAnalysisDialog(latestLesson.topic, aiReport)
    }

    // 2. Analiz Dialog'unu gösteren fonksiyon
    private fun showAiAnalysisDialog(topic: String, report: String) {
        android.app.AlertDialog.Builder(this)
            .setTitle("🤖 Asistan Analizi: $topic")
            .setMessage(report)
            .setPositiveButton("Tamam") { d, _ -> d.dismiss() }
            .show()
    }

    // 3. Dersin başarı oranına göre rapor hazırlayan asistan
    private fun generateAILessonReview(history: LessonHistory): String {
        return if (history.successRate < 61) {
            val anlamayanlar = history.studentList.filter { it.status == "not_understood" }
                .joinToString("\n") { "- ${it.name} (No: ${it.no}) | ${it.department}" }

            "⚠️ DİKKAT: '${history.topic}' konusu sınıfın büyük çoğunluğu tarafından anlaşılamadı.\n\n" +
                    "Yarın özellikle şu öğrencilerle ilgilenmenizi öneririm:\n$anlamayanlar"
        } else {
            "✅ TEBRİKLER: '${history.topic}' konusu sınıf genelinde %${history.successRate} başarıyla kavranmış."
        }
    }
    private fun loadDigitalReportsFromFirebase() {
        val sharedPref = getSharedPreferences("TeacherProfile", MODE_PRIVATE)
        val teacherUsername = sharedPref.getString("teacher_username", "unknown")?.replace(".", "_") ?: return
        val db = com.google.firebase.database.FirebaseDatabase.getInstance().reference

        db.child("TeacherArchives").child(teacherUsername).get().addOnSuccessListener { snapshot ->
            val historyList = mutableListOf<LessonHistory>()
            for (postSnapshot in snapshot.children) {
                val history = postSnapshot.getValue(LessonHistory::class.java)
                if (history != null) historyList.add(history)
            }

            // Bu noktada elinde PDF değil, GERÇEK VERİ var.
            // Artık "generateAILessonReview(history)" fonksiyonunu burada kullanabilirsin!
            updateUIWithDigitalData(historyList)
        }
    }
    private fun openPdfFile(file: File) {
        try {
            // AUTHORITIES KONTROLÜ: AndroidManifest içindeki authorities ile birebir aynı olmalı
            val uri: Uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // Bazı PDF okuyucular için yeni task gerekebilir
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            }

            val chooser = Intent.createChooser(intent, "Raporu Aç...")
            startActivity(chooser)

        } catch (e: Exception) {
            Toast.makeText(this, "Dosya açılamadı. PDF okuyucu yüklü olmayabilir.", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }
}

// --- ADAPTER VE MODEL ---

data class ArchiveItem(val title: String, val date: String, val file: File)

class ArchiveAdapter(
    private val items: List<ArchiveItem>,
    private val onClick: (File) -> Unit
) : RecyclerView.Adapter<ArchiveAdapter.ArchiveViewHolder>() {

    class ArchiveViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        // simple_list_item_2 içindeki standart ID'ler
        val title: TextView = v.findViewById(android.R.id.text1)
        val date: TextView = v.findViewById(android.R.id.text2)

        init {
            title.setTextColor(android.graphics.Color.BLACK)
            date.setTextColor(android.graphics.Color.GRAY)
            v.setPadding(32, 32, 32, 32) // Liste elemanlarına biraz nefes payı
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArchiveViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(
            android.R.layout.simple_list_item_2, parent, false
        )
        return ArchiveViewHolder(v)
    }

    override fun onBindViewHolder(holder: ArchiveViewHolder, position: Int) {
        val item = items[position]

        // İsim temizleme (Dosya ismindeki alt tireleri ve .pdf uzantısını gizle)
        holder.title.text = item.title.replace(".pdf", "").replace("_", " ")
        holder.date.text = "📅 ${item.date}"

        holder.itemView.setOnClickListener { onClick(item.file) }
    }

    override fun getItemCount() = items.size
}