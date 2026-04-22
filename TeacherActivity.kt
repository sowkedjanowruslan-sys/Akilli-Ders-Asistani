package com.example.akillidersasistani

import android.Manifest
import java.text.SimpleDateFormat
import java.util.Date
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import android.util.Log
import java.util.Locale
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.io.FileOutputStream
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import java.io.File
import java.util.*
import com.example.akillidersasistani.databinding.ActivityTeacherBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.MainScope
import com.google.ai.client.generativeai.type.generationConfig
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.delay

class TeacherActivity : AppCompatActivity() {
    private lateinit var projectManager: ProjectManager
    private var alertMediaPlayer: android.media.MediaPlayer? = null
    private val lessonTranscript = StringBuilder()
    private var mediaPlayer: MediaPlayer? = null
    private var tts: android.speech.tts.TextToSpeech? = null
    private lateinit var db: DatabaseReference
    private lateinit var quizManager: QuizResultManager
    private lateinit var feedbackAdapter: FeedbackAdapter
    private val feedbackList = mutableListOf<FeedbackModel>()
    private var sessionCode: String? = null
    private var teacherName: String = ""
    private var sessionStartTime: Long = 1
    private var liveTranscriptionHelper: LiveTranscriptionHelper? = null
    private var analysisDialog: BottomSheetDialog? = null
    private var audioManager: AudioManager? = null
    private lateinit var binding: ActivityTeacherBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. ÖNCE: ViewBinding (Arayüz bileşenlerine erişmek için şart)
        binding = ActivityTeacherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 2. ÖNCE: Firebase başlatılmalı (Çünkü alttaki Manager'lar bunu kullanıyor)
        db = FirebaseDatabase.getInstance().reference

        // 3. SONRA: Manager'lar başlatılmalı (db artık hazır olduğu için hata vermez)
        projectManager = ProjectManager(this, db)
        quizManager = QuizResultManager(this, db)

        // 4. Sistem ve UI Ayarları
        tts = android.speech.tts.TextToSpeech(this) { status ->
            if (status != android.speech.tts.TextToSpeech.ERROR) {
                tts?.language = Locale("tr")
            }
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // 5. Etkileşim Kilidi Dinleyicisi
        binding.switchLockInteractions.isChecked = true
        binding.switchLockInteractions.setOnCheckedChangeListener { _, isChecked ->
            sessionCode?.let { code ->
                db.child("sessions").child(code).child("config")
                    .child("buttonsEnabled").setValue(isChecked)
                    .addOnSuccessListener {
                        val status = if (isChecked) "açıldı" else "kapatıldı"
                        Toast.makeText(this, "Öğrenci etkileşimi $status", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        // 6. İzin Kontrolleri (Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        }

        // 7. Fonksiyonları Çağır
        initViews()
        loadTeacherProfile()
        setupChart()
        setupAIButtons()
        PDFBoxResourceLoader.init(applicationContext)
    }
    private val analysisPdfLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            // PDF seçildiğinde analizi başlatacak fonksiyonu çağırıyoruz (Bir sonraki adımda yazacağız)
            startGeminiAnalysis(it)
        }
    }

    private fun openSecureEducationalAsistan() {
        // 1. Layout'u şişir (Sadece BİR kez)
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_gemini_chat, null)

        // 2. Bileşenleri Bağla
        val txtChatHistory = dialogView.findViewById<TextView>(R.id.txtChatHistory)
        val edtInput = dialogView.findViewById<TextInputEditText>(R.id.edtTeacherInput)
        val btnSend = dialogView.findViewById<MaterialButton>(R.id.btnSendToGemini)
        val btnClose = dialogView.findViewById<MaterialButton>(R.id.btnFinishAndSend)
        val scroll = dialogView.findViewById<ScrollView>(R.id.scrollViewChat)
        val progressLoading = dialogView.findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.progressAiLoading)

        // --- GÜVENLİK VE ROL TANIMI ---
        val systemInstruction = """
        Sen bir 'Akıllı Ders Asistanı'sın. Sadece akademik ve eğitimsel konularda bilgi verirsin.
        1. Siyaset, cinsellik, şiddet, kumar veya yasa dışı içeriklerle ilgili hiçbir soruya cevap verme.
        2. Türkiye Cumhuriyeti yasalarına uygun davran.
        3. Sadece metin tabanlı akademik destek sun.
        4. Eğer kullanıcı yasaklı konuya girerse 'Üzgünüm, sadece eğitim odaklı sorulara cevap verebilirim.' de.
    """.trimIndent()

        val config = generationConfig {
            temperature = 0.7f
            topK = 40
            topP = 0.95f
        }

        val generativeModel = GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = "AIzaSyChToSge2Kx4m8ITBfSwpZb00eG3iCO6Ao",
            systemInstruction = content { text(systemInstruction) },
            generationConfig = config
        )

        val chat = generativeModel.startChat()
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setBackground(ColorDrawable(Color.TRANSPARENT))
            .create()

        btnSend.setOnClickListener {
            val prompt = edtInput.text.toString().trim()
            if (prompt.isNotEmpty()) {
                // Arayüz Hazırlığı
                txtChatHistory.append("\n\n📍 Soru: $prompt")
                edtInput.text?.clear()

                // --- YÜKLEME BAŞLADI ---
                progressLoading.visibility = View.VISIBLE
                btnSend.isEnabled = false
                btnSend.text = "Düşünüyor..."

                MainScope().launch {
                    try {
                        val response = chat.sendMessage(prompt)
                        val reply = response.text ?: "Bilgiye ulaşılamadı."

                        // --- YÜKLEME BİTTİ ---
                        progressLoading.visibility = View.GONE
                        btnSend.isEnabled = true
                        btnSend.text = "Bilgi Al"

                        appendAnimatedText(txtChatHistory, "\n\n📘 Bilgi: $reply", scroll)

                    } catch (e: Exception) {
                        progressLoading.visibility = View.GONE
                        btnSend.isEnabled = true
                        btnSend.text = "Bilgi Al"
                        txtChatHistory.append("\n\n⚠️ Hata: Bağlantı kesildi veya kısıtlamaya takıldı.")
                    }
                }
            }
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    // Yumuşak yazı efekti
    private fun appendAnimatedText(tv: TextView, text: String, scroll: ScrollView) {
        MainScope().launch {
            text.forEach { char ->
                tv.append(char.toString())
                delay(10)
                scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
            }
        }
    }
    private fun sendCreatedQuizToFirebase(quizText: String) {
        if (sessionCode != null) {val quizData = mapOf(
            "quiz_content" to quizText,
            "timestamp" to System.currentTimeMillis(),
            "type" to "ai_generated"
        )
            // Firebase'de o anki dersin altına gönder
            db.child("sessions").child(sessionCode!!).child("live_quiz").setValue(quizData)
        }
    }
    private fun startGeminiAnalysis(uri: Uri) {
        val dialogView = analysisDialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?: analysisDialog?.window?.decorView

        if (dialogView == null) return

        val txtUrgent = dialogView.findViewById<TextView>(R.id.txtUrgentAction)
        val txtPlan = dialogView.findViewById<TextView>(R.id.txtTeacherPlan)
        val container = dialogView.findViewById<LinearLayout>(R.id.containerAnalysisDetails)
        val txtLesson = dialogView.findViewById<TextView>(R.id.txtSelectedLesson)

        txtUrgent.text = "Analiz yapılıyor, lütfen bekleyin..."
        container.removeAllViews()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val pdfText = readTextFromUri(uri)

                val generativeModel = com.google.ai.client.generativeai.GenerativeModel(
                    modelName = "gemini-2.5-flash",
                    apiKey = "AIzaSyChToSge2Kx4m8ITBfSwpZb00eG3iCO6Ao"
                )

                val prompt = """
                Aşağıdaki ders metnini analiz et:
                $pdfText
                
                Lütfen şu formatta cevap ver:
                KRİTİK DURUM: (En önemli sorun)
                KAZANIMLAR:
                - Konu | Durum
                PLAN: (Hoca önerisi)
            """.trimIndent()

                val response = generativeModel.generateContent(prompt)
                val resultText = response.text ?: "Analiz alınamadı."
                withContext(Dispatchers.Main) {
                    txtLesson.text = "Analiz Edilen: ${uri.lastPathSegment}"
                    parseAnalysisResult(resultText, txtUrgent, txtPlan, container)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    txtUrgent.text = "Hata: ${e.localizedMessage}"
                }
            }
        }
    }
    private fun parseAnalysisResult(fullText: String, txtUrgent: TextView, txtPlan: TextView, container: LinearLayout) {
        try {
            container.removeAllViews()

            // Basit split mantığı ile bölümleri alıyoruz
            val sections = fullText.split("KRİTİK DURUM:", "KAZANIMLAR:", "PLAN:")

            if (sections.size >= 4) {
                txtUrgent.text = sections[1].trim()

                // Kazanımları listeye ekleme
                val kazanımlar = sections[2].trim().split("\n")
                kazanımlar.forEach { satir ->
                    if (satir.isNotBlank()) {
                        val tv = TextView(this).apply {
                            text = satir.trim()
                            setPadding(0, 12, 0, 12)
                            setTextColor(Color.BLACK)
                            textSize = 14f
                        }
                        container.addView(tv)
                    }
                }

                txtPlan.text = sections[3].trim()
            } else {
                // Eğer format bozuk gelirse direkt yazdır
                txtPlan.text = fullText
            }
        } catch (e: Exception) {
            txtPlan.text = "Veri ayrıştırma hatası: ${e.message}"
        }
    }
    private fun setupChart() {
        binding.performanceChart.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            setDrawBarShadow(false)
            setFitBars(true)

            // Tıklama (Hareket) Listener'ı
            setOnChartValueSelectedListener(object : com.github.mikephil.charting.listener.OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: com.github.mikephil.charting.highlight.Highlight?) {
                    // Sütuna basınca tüm grafiği yukarı doğru zıplatır (animasyon)
                    binding.performanceChart.animateY(401)
                    triggerHapticFeedback(31)
                }
                override fun onNothingSelected() {}
            })

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 2f
                // X ekseni etiketleri
                valueFormatter = com.github.mikephil.charting.formatter.IndexAxisValueFormatter(
                    listOf("Anlayan", "Anlamayan", "Tekrar", "Acil")
                )
            }
            axisLeft.axisMinimum = 1f
            axisRight.isEnabled = false
            legend.isEnabled = false // Renkler zaten belli
        }
        updateChartTime() // Saati başlat
    }
    private fun listenSessionStats(code: String) {
        db.child("sessions").child(code).child("stats").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val uCount = snapshot.child("understood").getValue(Int::class.java) ?: 1
                val nuCount = snapshot.child("not_understood").getValue(Int::class.java) ?: 1
                val rCount = snapshot.child("repeat").getValue(Int::class.java) ?: 1
                val eCount = snapshot.child("emergency").getValue(Int::class.java) ?: 1

                // Grafiği güncelle
                updateChartData("understood", uCount)
                updateChartData("not_understood", nuCount)
                updateChartData("repeat", rCount)
                updateChartData("emergency", eCount)


                updateStatValue(R.id.cardUnderstood, uCount)
                updateStatValue(R.id.cardNotUnderstood, nuCount)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }
    private fun updateChartData(type: String, count: Int) {
        // 2. Verileri hazırla (0:Anlayan, 1:Anlamayan, 2:Tekrar, 3:Acil)
        // Firebase'den gelen anlık değerleri alıyoruz
        val u = binding.txtUnderstoodCount.text.toString().toFloatOrNull() ?: 1f
        val nu = binding.txtNotUnderstoodCount.text.toString().toFloatOrNull() ?: 1f
        val r = feedbackList.count { it.type == "repeat" }.toFloat()
        val e = feedbackList.count { it.type == "emergency" }.toFloat()

        val entries = ArrayList<com.github.mikephil.charting.data.BarEntry>()
        entries.add(com.github.mikephil.charting.data.BarEntry(1f, u))
        entries.add(com.github.mikephil.charting.data.BarEntry(2f, nu))
        entries.add(com.github.mikephil.charting.data.BarEntry(3f, r))
        entries.add(com.github.mikephil.charting.data.BarEntry(4f, e))

        val dataSet = com.github.mikephil.charting.data.BarDataSet(entries, "Durumlar")

        // 3. Her sütuna özel renk ata
        dataSet.colors = listOf(
            Color.parseColor("#5CAF50"), // Yeşil (Anlayan)
            Color.parseColor("#F44337"), // Kırmızı (Anlamayan)
            Color.parseColor("#FFC108"), // Sarı (Tekrar)
            Color.parseColor("#9C27B0")  // Mor (Acil)
        )

        dataSet.valueTextSize = 13f
        dataSet.setDrawValues(true) // Sütun üstünde sayı yazsın

        val barData = com.github.mikephil.charting.data.BarData(dataSet)
        barData.barWidth = 1.5f

        binding.performanceChart.data = barData
        binding.performanceChart.invalidate() // Grafiği tazele

        updateChartTime() // Her veri geldiğinde saati güncelle
    }
    private fun updateChartTime() {
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        binding.txtChartTime.text = "Son Güncelleme: $currentTime"
    }


    fun listenForEmergencies(code: String) {
        val emergencyRef = FirebaseDatabase.getInstance().getReference("Emergencies").child(code)

        emergencyRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                // Eğer "active" durumunda bir sinyal gelmişse
                if (snapshot.exists() && snapshot.child("status").value == "active") {
                    val studentName = snapshot.child("studentName").value.toString()

                    playEmergencySound()

                    showEmergencyDialog(studentName, code)
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })
    }
    private fun showEmergencyDialog(studentName: String, code: String) {
        // Klasik Android Dialog (Compose hatası almamak için tam yol belirttik)
        val dialog = android.app.Dialog(this)
        val dialogBinding = com.example.akillidersasistani.databinding.DialogEmergencyAlertBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCancelable(false)

        dialogBinding.txtEmergencyStudentName.text = "$studentName acil yardım istiyor!"

        dialogBinding.btnDismissEmergency.setOnClickListener {
            // Firebase'deki durumu "resolved" (çözüldü) yap ki tekrar çalmasın
            FirebaseDatabase.getInstance().getReference("Emergencies")
                .child(code).child("status").setValue("resolved")

            // Sesi durdur ve pencereyi kapat
            alertMediaPlayer?.stop()
            alertMediaPlayer?.release()
            alertMediaPlayer = null
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun playEmergencySound() {
        if (alertMediaPlayer == null) {
            val alertUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
            alertMediaPlayer = android.media.MediaPlayer.create(this, alertUri)
            alertMediaPlayer?.isLooping = true
            alertMediaPlayer?.start()
        }
    }

    private fun showStudentAnalysisDialog() {
        analysisDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_student_analysis, null)
        analysisDialog?.setContentView(view)

        val btnSelectPDF = view.findViewById<Button>(R.id.btnSelectPDFForAnalysis)

        btnSelectPDF.setOnClickListener {
            triggerHapticFeedback(31)
            // PDF Seçiciyi Başlat (Grilik gidecek)
            analysisPdfLauncher.launch("application/pdf")
        }

        analysisDialog?.show()
    }

    private fun showStudentQuizDialog(content: String) {    android.app.AlertDialog.Builder(this)
        .setTitle("Gönderilen Test (Hoca Önizleme)")
        .setMessage(content)
        .setPositiveButton("Tamam") { dialog, _ ->
            dialog.dismiss()
        }
        .setNegativeButton("Kapat", null)
        .show()
    }

    private val getFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            lifecycleScope.launch(Dispatchers.IO) {
                val fileContent = readTextFromUri(it) // PDF'den metni okur
                withContext(Dispatchers.Main) {
                    if (fileContent.isNotEmpty()) {
                        // Doğrudan oluşturmak yerine önce kaç soru istediğini soruyoruz
                        showQuestionCountDialog(0, fileContent)
                    } else {
                        Toast.makeText(this@TeacherActivity, "PDF içeriği boş veya okunamadı!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    private fun showQuestionCountDialog(sourceType: Int, data: String) {
        val counts = arrayOf("3 Soru", "5 Soru", "10 Soru", "15 Soru")
        MaterialAlertDialogBuilder(this)
            .setTitle("Soru Sayısını Seçin")
            .setItems(counts) { _, which ->
                val count = when(which) {
                    0 -> 3; 1 -> 5; 2 -> 10; 3 -> 15; else -> 5
                }
                generateAutoQuizFromPdf(data, count) // AI'yı seçilen sayı ile çağır
            }
            .show()
    }
    private fun generateAutoQuizFromPdf(content: String, count: Int) {
        val progressDialog = android.app.ProgressDialog(this).apply {
            setMessage("AI $count Adet Test Hazırlıyor...")
            setCancelable(false)
            show()
        }

        val aiPrompt = """
        Metne dayanarak tam $count adet çoktan seçmeli soru hazırla.
        FORMAT: Her soruyu tek bir satıra yaz ve aralara '|' işareti koy.
        Soru | A | B | C | D | DoğruCevapHarfi
        Metin: $content
    """.trimIndent()

        val generativeModel = com.google.ai.client.generativeai.GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = "AIzaSyChToSge2Kx4m8ITBfSwpZb00eG3iCO6Ao"
        )

        lifecycleScope.launch {
            try {
                // 3. response.text kullanarak sonucu alıyoruz
                val response = generativeModel.generateContent(aiPrompt)
                val resultText = response.text ?: "Cevap oluşturulamadı."

                withContext(Dispatchers.Main) {
                    showAIQuizDialog(resultText)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@TeacherActivity, "Hata: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e("GEMINI_SDK_ERROR", e.message ?: "Bilinmeyen Hata")
                }
            } finally {
                progressDialog.dismiss()
            }
        }
    }
    private fun readTextFromUri(uri: Uri): String {
        return try {
            val content = StringBuilder()
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val document = PDDocument.load(inputStream)
                val pdfStripper = PDFTextStripper()
                val text = pdfStripper.getText(document)
                content.append(text)

                // EĞER CANLI DERS METNİ VARSA ONU DA EKLE (BİRLEŞTİRME)
                if (lessonTranscript.isNotEmpty()) {
                    content.append("\n\n--- CANLI DERS NOTLARI ---\n")
                    content.append(lessonTranscript.toString())
                }

                document.close()
            }
            content.toString().trim()
        } catch (e: Exception) {
            Log.e("PDF_ERROR", "Hata: ${e.message}")
            ""
        }
    }
    private fun setupAIButtons() {
        binding.btnGenerateAIQuiz.setOnClickListener {
            val options = arrayOf("PDF'den Otomatik Test Oluştur", "Canlı Dersten Test Oluştur")
            MaterialAlertDialogBuilder(this)
                .setTitle("Test Kaynağı Seçin")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> getFileLauncher.launch("application/pdf") // PDF Seçince launcher'a gider
                        1 -> {
                            if (lessonTranscript.isNotEmpty()) {
                                showQuestionCountDialog(1, lessonTranscript.toString())
                            } else {
                                Toast.makeText(this, "Henüz yeterli ders notu birikmedi.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                .show()
        }
          binding.btnCreateProject.setOnClickListener {
              sessionCode?.let { code ->
                  val topic = binding.txtCode.text.toString()
                  projectManager.checkAndShowProjectDialog(code, topic)
              } ?: Toast.makeText(this, "Önce bir ders başlatın!", Toast.LENGTH_SHORT).show()
          }
    }

    private fun checkAndShowAnalysisIntro() {
        val sharedPref = getSharedPreferences("TeacherPrefs", Context.MODE_PRIVATE)
        val isFirstTime = sharedPref.getBoolean("isFirstAnalysisUsage", true)

        if (isFirstTime) {
            // İlk kullanım: Tanıtımı göster
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_analysis_intro, null)
            val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            dialogView.findViewById<Button>(R.id.btnAnalysisIntroStart).setOnClickListener {
                // Bir daha gösterme
                sharedPref.edit().putBoolean("isFirstAnalysisUsage", false).apply()
                dialog.dismiss()
                showStudentAnalysisDialog() // Tanıtımdan sonra asıl analiz ekranını aç
            }
            dialog.show()
        } else {
            // Zaten görülmüş: Direkt analiz ekranını aç
            showStudentAnalysisDialog()
        }
    }
    private fun showAIQuizDialog(quiz: String) {
        // 2. Diyalog tasarımını yükle
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_ai_quiz_result, null)

        // 3. XML içindeki bileşenleri diyalog penceresi üzerinden bul (Binding burada çalışmaz)
        val txtContent = dialogView.findViewById<TextView>(R.id.txtAiQuizContent)
        val btnSend = dialogView.findViewById<Button>(R.id.btnSendQuizToStudents)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelQuiz)

        // 4. İçeriği yerleştir
        txtContent.text = quiz

        // 5. Diyaloğu oluştur
        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        // Arka planı şeffaf yaparak yuvarlak köşelerin görünmesini sağla
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // 6. "Öğrencilere Gönder" Butonu
        btnSend.setOnClickListener {
            sessionCode?.let { code ->
                // Firebase'e "live_quiz" düğümü altına testi gönderiyoruz
                val quizData = mapOf(
                    "quizContent" to quiz,
                    "isActive" to true,
                    "timestamp" to com.google.firebase.database.ServerValue.TIMESTAMP
                )

                db.child("sessions").child(code).child("live_quiz").setValue(quizData)
                    .addOnSuccessListener {
                        triggerHapticFeedback(101)
                        Toast.makeText(this, "Test başarıyla sınıfa gönderildi! ✅", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
            } ?: Toast.makeText(this, "Aktif ders bulunamadı!", Toast.LENGTH_SHORT).show()
        }

        // 7. İptal Butonu
        btnCancel.setOnClickListener {
            triggerHapticFeedback(21)
            dialog.dismiss()
        }

        dialog.show()
    }
    private fun listenForLiveQuiz(code: String) {
        db.child("sessions").child(code).child("live_quiz")
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    val isActive = snapshot.child("isActive").getValue(Boolean::class.java) ?: false
                    val content = snapshot.child("quizContent").getValue(String::class.java) ?: ""

                    if (isActive && content.isNotEmpty()) {
                        showStudentQuizDialog(content)
                    }
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
    }

    // Bu bir yardımcı fonksiyondur, analiz sayfasında kullanılır.
    private fun generateAILessonReview(history: LessonHistory): String {
        return if (history.successRate < 61) {
            // Anlamayanları listele
            val anlamayanlar = history.studentList.filter { it.status == "not_understood" }
                .joinToString("\n") { "- ${it.name} (No: ${it.no}) | ${it.department}" }

            "⚠️ DİKKAT: '${history.topic}' konusu sınıfın yarısından fazlası tarafından anlaşılamadı.\n\n" +
                    "Hocam, yarın özellikle şu öğrencilerle ilgilenmenizi öneririm:\n$anlamayanlar"
        } else {
            "✅ TEBRİKLER: '${history.topic}' konusu başarıyla kavranmış. Sınıf genelinde %${history.successRate} başarı sağlandı."
        }
    }
    private fun archiveLessonData(code: String, topic: String) {
        val sharedPref = getSharedPreferences("TeacherProfile", Context.MODE_PRIVATE)
        val teacherUsername = sharedPref.getString("teacher_username", "unknown") ?: "unknown"

        // 1. Önce bu derse ait projeleri Firebase'den çekelim (Gerçek veri arşivi için)
        db.child("sessions").child(code).child("projects").get().addOnSuccessListener { projectSnapshot ->

            val projectsListArchive = mutableListOf<Map<String, Any>>()

            // 2. Projeleri listeye doldur (Böylece değişken gri kalmaz ve veri dolar)
            for (proj in projectSnapshot.children) {
                val data = proj.value as? Map<String, Any>
                data?.let { projectsListArchive.add(it) }
            }

            // 3. Öğrenci listesini hazırla
            val archivedStudents = feedbackList.map { student ->
                StudentSimpleModel(
                    name = student.name,
                    no = student.no,
                    department = student.department,
                    status = student.type
                )
            }

            // 4. Başarı oranını hesapla
            val understoodCount = feedbackList.count { it.type == "understood" }
            val total = feedbackList.size
            val rate = if (total > 0) (understoodCount * 100) / total else 0

            // 5. Arşiv Nesnesini Oluştur (projectsListArchive'i içine ekledik)
            val history = LessonHistory(
                sessionId = code,
                topic = topic,
                date = System.currentTimeMillis(),
                totalStudents = total,
                successRate = rate,
                studentList = archivedStudents,
                projects = projectsListArchive // <--- DEĞİŞKEN BURADA KULLANILDI, GRİLİK GİTTİ!
            )

            // 6. Firebase'e Kaydet (Hocanın özel arşivine)
            val safeUsername = teacherUsername.replace(".", "_")
            db.child("TeacherArchives").child(safeUsername).child(code).setValue(history)
                .addOnSuccessListener {
                    Log.d("ARCHIVE", "Ders ve projeler detaylı olarak hafızaya alındı ✅")
                }
        }.addOnFailureListener {
            Log.e("ARCHIVE_ERROR", "Projeler çekilemediği için sadece ders verileri arşivlendi.")
        }
    }
    private fun endSession(sessionCode: String) {
        // 2. Önce Firebase'den o dersin gerçek KONUSUNU (Topic) çekelim
        db.child("sessions").child(sessionCode).get().addOnSuccessListener { snapshot ->
            val lessonTopic = snapshot.child("topic").getValue(String::class.java) ?: "Bilinmeyen Konu"

            // 3. ÖĞRENCİ BAZLI DETAYLI ARŞİVİ ŞİMDİ YAPALIM (Senin istediğin kalıcı hafıza)
            archiveLessonData(sessionCode, lessonTopic)

            // 4. Oturumu Pasife Çek
            db.child("sessions").child(sessionCode).child("active").setValue(false)

            // 5. İstatistikleri Hesapla (Mevcut kodun)
            val studentCount = binding.txtStudentsCount.text.toString().toIntOrNull() ?: 1
            val understoodCount = binding.txtUnderstoodCount.text.toString().toIntOrNull() ?: 1
            val percent = if (studentCount > 1) (understoodCount * 100) / studentCount else 0

            // 6. PDF ve Rapor Kayıtları (Mevcut kodun)
            saveLessonToArchive("Konu: $lessonTopic", studentCount, percent)

            // 7. Toplam Ders Sayısını Güncelle (Mevcut kodun)
            val pref = getSharedPreferences("TeacherProfile", Context.MODE_PRIVATE)
            val newTotal = pref.getInt("total_lessons_count", 1) + 1
            pref.edit().putInt("total_lessons_count", newTotal).apply()

            val username = pref.getString("teacher_username", "")
            if (!username.isNullOrEmpty()) {
                db.child("teachers").child(username).child("totalSessionsCount").setValue(newTotal)
            }
            binding.txtSessionsCount.text = newTotal.toString()

            // 8. AI ANALİZİNİ TETİKLE (Gri görünmesin diye ve akıllı asistan notu için)
            val currentHistory = LessonHistory(
                topic = lessonTopic,
                successRate = percent,
                studentList = feedbackList.map { StudentSimpleModel(it.name, it.no, it.department, it.type) }
            )
            val aiReview = generateAILessonReview(currentHistory)
            android.app.AlertDialog.Builder(this)
                .setTitle("AI Ders Analizi")
                .setMessage(aiReview)
                .setPositiveButton("Tamam", null)
                .show()            // 9. EKRANI TEMİZLE (Mevcut temizlik kodların)
            clearUI()

            Toast.makeText(this, "Ders Bitti: $lessonTopic Arşivlendi ✅", Toast.LENGTH_SHORT).show()
        }
    }

    // Temizlik kısmını buraya topladık, endSession içine yazmak yerine bunu çağırıyoruz.
    // Bunu ders BİTTİĞİNDE çağır (Zaten endSession içinde çağırıyorsun)
    private fun clearUI() {
        // Tüm listeyi ve grafiği temizle
        feedbackList.clear()
        feedbackAdapter.notifyDataSetChanged()

        binding.performanceChart.clear()

        // UI öğelerini başlangıç haline getir
        binding.apply {
            txtCode.text = "------"
            txtUnderstoodCount.text = "0"
            txtNotUnderstoodCount.text = "0"
            txtStudentsCount.text = "0"

            // GÖRÜNÜRLÜK AYARLARI: Ders bittiği için paneli gizle, "Yeni Ders" butonunu göster
            layoutEmptyState.visibility = View.VISIBLE   // Önceki adımda eklediğimiz boş durum alanı
            layoutActiveSession.visibility = View.GONE // Aktif ders paneli gizlenir
        }

        this.sessionCode = null

        // Dinleyicileri durdur
        if (::quizManager.isInitialized) {
            quizManager.stopListening()
        }
    }

    private fun resetStatsAndChart() {

        feedbackList.clear()
        feedbackAdapter.notifyDataSetChanged()

        binding.performanceChart.clear()
        binding.performanceChart.invalidate()

        // Sayaçları sıfırla (Başlangıç değerleri)
        binding.txtUnderstoodCount.text = "0"
        binding.txtNotUnderstoodCount.text = "0"
        binding.txtStudentsCount.text = "0"

    }
    private fun initViews() {
        // --- 1. SAYAÇLAR VE LİSTELER BAŞLANGIÇ ---
        binding.txtStudentsCount.text = "0" // Başlangıç 0 olmalı
        binding.txtCode.text = "---"

        feedbackAdapter = FeedbackAdapter(feedbackList) { photo, name, department, no, userId ->
            triggerHapticFeedback(41)
            showFullScreenImage(photo, name, "$department | No: $no")
        }
        binding.rvFeedbackList.layoutManager = LinearLayoutManager(this)
        binding.rvFeedbackList.adapter = feedbackAdapter

        // --- 2. ÖĞRENCİ ANALİZ BUTONU (Düzeltildi) ---
        binding.btnOpenHistoryArchive.setOnClickListener {
            triggerHapticFeedback(41)

            checkAndShowAnalysisIntro()
        }
        binding.teacherScrollView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (scrollY > oldScrollY + 10) {
                // Aşağı kaydırırken sadece ikona dönüşür
                binding.btnFloatingAiAsistan.shrink()
            } else if (scrollY < oldScrollY - 10) {
                // Yukarı kaydırırken yazı tekrar açılır
                binding.btnFloatingAiAsistan.extend()
            }
        }
        binding.btnFloatingAiAsistan.setOnClickListener {
            triggerHapticFeedback(41) // Hafif titreşim
            openSecureEducationalAsistan() // Senin yazdığın o profesyonel pencereyi açar
        }
        binding.txtCode.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Ders Kodu", binding.txtCode.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Kod panoya kopyalandı!", Toast.LENGTH_SHORT).show()
        }

        binding.switchLiveSubtitles.setOnCheckedChangeListener { _, isChecked ->
            val code = sessionCode
            if (code == null) {
                binding.switchLiveSubtitles.isChecked = false
                Toast.makeText(this, "Önce bir ders başlatın!", Toast.LENGTH_SHORT).show()
                return@setOnCheckedChangeListener
            }

            if (isChecked) {
                // İzin kontrolü yap ve başlat
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    startTranscription()
                } else {
                    requestMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            } else {
                liveTranscriptionHelper?.stop()
                sessionCode?.let { db.child("sessions").child(it).child("live_text").setValue("") }
            }
        }
        binding.btnCreateCode.setOnClickListener {
            triggerHapticFeedback(31)
            generateNewSession()
        }

        binding.btnGoToReports.setOnClickListener {
            triggerHapticFeedback(41)
            val intent = Intent(this, LessonReportActivity::class.java)
            intent.putExtra("CURRENT_SESSION_CODE", sessionCode)
            startActivity(intent)
        }

        binding.btnDownloadReport.setOnClickListener {
            triggerHapticFeedback(51)
            if (!sessionCode.isNullOrEmpty()) {
                generateDetailedReport()
            } else {
                Toast.makeText(this, "Aktif bir ders bulunamadı!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnFinishSession.setOnClickListener {
            triggerHapticFeedback(51)
            sessionCode?.let { code ->
                lifecycleScope.launch(Dispatchers.IO) {
                    saveTranscriptToDocuments()
                    withContext(Dispatchers.Main) {
                        endSession(code)
                        Toast.makeText(this@TeacherActivity, "Ders Arşivlendi ✅", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // Profil ve Diğer Ayarlar
        binding.imgTeacherProfileSmall.setOnClickListener {
            startActivity(Intent(this, TeacherProfileActivity::class.java).apply { putExtra("isEditing", true) })
        }

        binding.btnTeacherLogout.setOnClickListener {
            getSharedPreferences("TeacherProfile", MODE_PRIVATE).edit().clear().apply()
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }

        loadTeacherStats()
    }
    private val requestMicPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) startTranscription()
        else Toast.makeText(this, "İzin reddedildi.", Toast.LENGTH_SHORT).show()
    }

    private fun startTranscription() {
        val code = sessionCode ?: return
        liveTranscriptionHelper = LiveTranscriptionHelper(this, code) { finalSentence ->
            lessonTranscript.append(finalSentence).append(". ")
            db.child("sessions").child(code).child("live_text").setValue("")
        }
        liveTranscriptionHelper?.start()
        db.child("sessions").child(code).child("isSubtitlesActive").setValue(true)
    }
    private fun loadTeacherStats() {val pref = getSharedPreferences("TeacherProfile", MODE_PRIVATE)
        binding.txtTeacherName.text = pref.getString("teacher_name", "Öğretmen")

        // txtTeacherBranch hatası için:
        binding.txtTeacherBranch.text = pref.getString("teacher_branch", "Branş Belirtilmedi")

        // txtSessionsCount hatası ve String/Int uyuşmazlığı için:
        val sessionCount = pref.getInt("total_lessons_count", 1)
        binding.txtSessionsCount.text = sessionCount.toString() // toString() zorunludur

        binding.imgTeacherProfileSmall.setImageResource(R.drawable.ic_teacher_avatar_default)
    }
    private fun saveTranscriptToDocuments() {
        if (lessonTranscript.isEmpty()) return

        val pdfDocument = PdfDocument()
        val paint = Paint()
        paint.textSize = 13f

        val pageWidth = 596
        val pageHeight = 843
        var pageCount = 1

        // İlk sayfayı başlat
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageCount).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        var y = 81f

        // PDF Başlığı
        paint.isFakeBoldText = true
        paint.textSize = 17f
        canvas.drawText("DERS DÖKÜMÜ - $sessionCode", 51f, 50f, paint)
        paint.isFakeBoldText = false
        paint.textSize = 13f

        lessonTranscript.toString().split("\n").forEach { line ->
            // Satırı parçalara ayır (Eğer satır çok uzunsa yana taşmasın)
            if (y > 781) { // Sayfa sınırı
                pdfDocument.finishPage(page)
                pageCount++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageCount).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                y = 51f // Yeni sayfa başlangıç yüksekliği
            }

            if (line.trim().isNotEmpty()) {
                canvas.drawText(line, 51f, y, paint)
                y += 26f // Satır aralığı
            }
        }

        pdfDocument.finishPage(page)

        // --- BELGELER (DOCUMENTS) KLASÖRÜNE KAYDETME ---
        val fileName = "AkilliDers_${sessionCode}_${System.currentTimeMillis()}.pdf"
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), fileName)

        try {
            val fos = FileOutputStream(file)
            pdfDocument.writeTo(fos)
            fos.close()
            // Dosyayı sisteme tanıt (Dosyalarım uygulamasında görünmesi için)
            android.media.MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), null, null)
            Toast.makeText(this, "Dosya Belgeler klasörüne kopyalandı!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("PDF_ERROR", e.message ?: "Hata")
        } finally {
            pdfDocument.close()
        }
    }
    private fun saveLessonToArchive(lessonName: String, studentCount: Int, understoodPercent: Int) {
        val sharedPref = getSharedPreferences("TeacherProfile", Context.MODE_PRIVATE)
        val username = sharedPref.getString("teacher_username", "unknown") ?: "unknown"
        val safeId = username.replace(".", "_")
        val reportId = db.child("Reports").child(safeId).push().key ?: return
        val report = LessonReport(
            reportId = reportId,
            lessonName = lessonName,
            date = System.currentTimeMillis(),
            totalStudents = studentCount,
            understoodPercent = understoodPercent
        )

        // Firebase'e kaydet (Sadece bu öğretmenin altına)
        db.child("Reports").child(safeId).child(reportId).setValue(report)
            .addOnSuccessListener {
                Toast.makeText(this, "Ders arşive kaydedildi ✅", Toast.LENGTH_SHORT).show()

                // Global Toplam Ders sayısını 2 artır
                db.child("GlobalStats/totalLessons").runTransaction(object : Transaction.Handler {
                    override fun doTransaction(data: MutableData): Transaction.Result {
                        val current = data.getValue(Int::class.java) ?: 1
                        data.value = current + 1
                        return Transaction.success(data)
                    }
                    override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {}
                })
            }
    }
    private fun triggerHapticFeedback(ms: Long) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(ms)
        }
    }
    private fun generateDetailedReport() {
        val currentCode = sessionCode ?: return
        val pdfDocument = PdfDocument()
        val paint = Paint()
        val titlePaint = Paint()

        // 2. Gerekli Bilgileri Hazırla
        val sharedPref = getSharedPreferences("TeacherProfile", MODE_PRIVATE)
        val schoolName = sharedPref.getString("teacher_university", "Belirtilmemiş Eğitim Kurumu")
        val teacherBranch = sharedPref.getString("teacher_branch", "Branş Belirtilmemiş")

        val durationMillis = System.currentTimeMillis() - sessionStartTime
        val durationMinutes = durationMillis / (1000 * 60)
        val startTimeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(sessionStartTime))
        val endTimeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

        // 3. Firebase'den Tüm Verileri Çek (Tek seferde snapshot alıyoruz)
        db.child("sessions").child(currentCode).get().addOnSuccessListener { snapshot ->
            try {
                var pageCount = 1
                var pageInfo = PdfDocument.PageInfo.Builder(596, 842, pageCount).create()
                var page = pdfDocument.startPage(pageInfo)
                var canvas = page.canvas
                var y = 51f

                // --- BAŞLIK VE KURUMSAL BİLGİLER ---
                titlePaint.isFakeBoldText = true
                titlePaint.textSize = 19f
                titlePaint.color = Color.parseColor("#5F46E5")
                canvas.drawText("AKILLI DERS ASİSTANI - PROFESYONEL ANALİZ RAPORU", 51f, y, titlePaint)

                y += 41f
                paint.textSize = 13f
                paint.isFakeBoldText = true
                canvas.drawText("EĞİTİM KURUMU: $schoolName", 51f, y, paint)
                y += 21f
                paint.isFakeBoldText = false
                canvas.drawText("EĞİTMEN: $teacherName ($teacherBranch)", 51f, y, paint)
                y += 21f
                canvas.drawText("DERS KODU: $currentCode | SINIF: ${snapshot.child("class_no").value ?: "---"}", 51f, y, paint)
                y += 21f
                canvas.drawText("TARİH: $dateStr | SAAT: $startTimeStr - $endTimeStr ($durationMinutes dk)", 51f, y, paint)

                // --- GENEL İSTATİSTİKLER (Bento Stil) ---
                y += 41f
                paint.isFakeBoldText = true
                paint.textSize = 15f
                canvas.drawText("GENEL ETKİLEŞİM ÖZETİ", 51f, y, paint)
                y += 26f
                paint.textSize = 13f
                paint.isFakeBoldText = false

                val stats = snapshot.child("stats")
                val uTotal = stats.child("understood").getValue(Int::class.java) ?: 1
                val nTotal = stats.child("not_understood").getValue(Int::class.java) ?: 1
                val rTotal = stats.child("repeat").getValue(Int::class.java) ?: 1
                val eTotal = stats.child("emergency").getValue(Int::class.java) ?: 1

                canvas.drawText("• Toplam Anlayan: $uTotal  |  • Toplam Anlamayan: $nTotal", 71f, y, paint)
                y += 21f
                canvas.drawText("• Tekrar İsteği: $rTotal  |  • Acil Durum Çağrısı: $eTotal", 71f, y, paint)

                // --- ÖĞRENCİ LİSTESİ VE KİMLİK DURUMLARI ---
                y += 41f
                paint.isFakeBoldText = true
                canvas.drawText("KATILIMCI DETAYLARI", 51f, y, paint)
                y += 26f
                paint.isFakeBoldText = false

                val users = snapshot.child("users")
                var ghostCount = 1
                var activeCount = 1

                for (st in users.children) {
                    activeCount++
                    val name = st.child("name").getValue(String::class.java) ?: "Gizli"
                    val no = st.child("no").getValue(String::class.java) ?: "---"
                    val isAnon = st.child("isAnonymous").getValue(Boolean::class.java) ?: false

                    if (isAnon) {
                        ghostCount++
                        canvas.drawText("- [KİMLİK GİZLİ] (Öğrenci $ghostCount)", 71f, y, paint)
                    } else {
                        canvas.drawText("- $name (No: $no)", 71f, y, paint)
                    }

                    y += 21f
                    if (y > 781) { // Yeni Sayfa Kontrolü
                        pdfDocument.finishPage(page)
                        pageCount++
                        pageInfo = PdfDocument.PageInfo.Builder(596, 842, pageCount).create()
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        y = 51f
                    }
                }
                canvas.drawText("Özet: $activeCount Katılımcı ($ghostCount Anonim)", 51f, y, paint)

                // --- DETAYLI MESAJ VE GERİ BİLDİRİM LOGLARI ---
                y += 41f
                pdfDocument.finishPage(page)
                pageCount++
                pageInfo = PdfDocument.PageInfo.Builder(596, 842, pageCount).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                y = 51f

                paint.isFakeBoldText = true
                canvas.drawText("KRONOLOJİK ÖĞRENCİ GERİ BİLDİRİMLERİ", 51f, y, paint)
                y += 31f
                paint.isFakeBoldText = false

                val feedbacks = snapshot.child("feedback_list")
                for (fb in feedbacks.children) {
                    val stName = fb.child("name").getValue(String::class.java) ?: "Öğrenci"
                    val msg = fb.child("message").getValue(String::class.java) ?: ""
                    val type = fb.child("type").getValue(String::class.java) ?: ""
                    val time = fb.child("timeInfo").getValue(String::class.java) ?: ""

                    val tag = when(type) {
                        "understood" -> "[ANLADI]"
                        "not_understood" -> "[ANLAMADI]"
                        "repeat" -> "[TEKRAR]"
                        else -> "[MESAJ]"
                    }

                    paint.isFakeBoldText = true
                    canvas.drawText("$tag $stName ($time):", 71f, y, paint)
                    y += 19f
                    paint.isFakeBoldText = false
                    paint.textSize = 11f
                    canvas.drawText("   \"$msg\"", 81f, y, paint)
                    paint.textSize = 13f
                    y += 26f

                    if (y > 781) {
                        pdfDocument.finishPage(page)
                        pageCount++
                        pageInfo = PdfDocument.PageInfo.Builder(596, 842, pageCount).create()
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        y = 51f
                    }
                }

                // --- SON SAYFA: DERS TRANSCRIPT (ALTYAZILAR) ---
                pdfDocument.finishPage(page)
                pageCount++
                pageInfo = PdfDocument.PageInfo.Builder(596, 842, pageCount).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                y = 51f

                paint.isFakeBoldText = true
                canvas.drawText("DERS SES DÖKÜMÜ (TRANSCRIPT)", 51f, y, paint)
                y += 31f
                paint.isFakeBoldText = false
                paint.textSize = 11f

                val lines = lessonTranscript.toString().split("\n")
                for (line in lines) {
                    if (line.trim().isEmpty()) continue

                    // Uzun satırları sayfa genişliğine uydur
                    val safeLine = if (line.length > 91) line.substring(0, 87) + "..." else line
                    canvas.drawText(safeLine, 51f, y, paint)
                    y += 16f

                    if (y > 801) {
                        pdfDocument.finishPage(page)
                        pageCount++
                        pageInfo = PdfDocument.PageInfo.Builder(596, 842, pageCount).create()
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        y = 51f
                    }
                }

                pdfDocument.finishPage(page)

                // --- KAYDETME VE BİTİRİŞ ---
                val fileName = "AkilliDers_DetayliRapor_${currentCode}.pdf"
                val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
                val fos = FileOutputStream(file)
                pdfDocument.writeTo(fos)
                fos.close()
                pdfDocument.close()

                android.media.MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), null, null)
                Toast.makeText(this, "Profesyonel Rapor İndirilenler'e kaydedildi!", Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                Log.e("PDF_ERROR", "Rapor Hatası: ${e.message}")
                Toast.makeText(this, "Hata: Rapor oluşturulamadı.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun listenForStudentMessages() {
        val code = sessionCode ?: return
        // Öğrencinin bastığı butonlar "messages" altına kaydediliyor olmalı
        db.child("sessions").child(code).child("feedback_list") // Doğru yol burası
            .addChildEventListener(object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    val time = snapshot.child("timestamp").getValue(Long::class.java) ?: 1L

                    // Sadece ders başladıktan sonra gelen yeni bildirimleri göster
                    if (time > sessionStartTime) {
                        val name = snapshot.child("name").getValue(String::class.java) ?: "Öğrenci"
                        val message = snapshot.child("message").getValue(String::class.java) ?: ""
                        val type = snapshot.child("type").getValue(String::class.java) ?: "understood"

                        runOnUiThread {
                            // Bu fonksiyon senin XML'lerini (understood, repeat, not_understood) açar
                            showMessagePanel(name, message, type)
                        }
                    }
                }
                // Diğer metodlar (onChildChanged vb.) aynı kalsın...
                override fun onChildChanged(s: DataSnapshot, p: String?) {}
                override fun onChildRemoved(s: DataSnapshot) {}
                override fun onChildMoved(s: DataSnapshot, p: String?) {}
                override fun onCancelled(e: DatabaseError) {}
            })
    }
    private fun setupStatCard(cardId: Int, title: String, color: String) {
        val cardView = findViewById<View>(cardId) ?: return // Null kontrolü
        cardView.findViewById<TextView>(R.id.txtStatTitle).text = title
        cardView.findViewById<TextView>(R.id.txtStatValue).setTextColor(android.graphics.Color.parseColor(color))
    }

    private fun updateStatValue(cardId: Int, value: Int) {
        runOnUiThread {
            when (cardId) {
                R.id.cardUnderstood -> {
                    binding.txtUnderstoodCount.text = value.toString()
                }
                R.id.cardNotUnderstood -> {
                    binding.txtNotUnderstoodCount.text = value.toString()
                }
            }
        }
    }

    private fun loadTeacherProfile() {
        val sharedPref = getSharedPreferences("TeacherProfile", Context.MODE_PRIVATE)

        // 2. Verileri SharedPreferences'tan güvenli bir şekilde çek
        val realName = sharedPref.getString("teacher_name", "Öğretmen") ?: "Öğretmen"
        teacherName = realName
        val branch = sharedPref.getString("teacher_branch", "Branş Belirtilmemiş") ?: "Branş Belirtilmemiş"
        val savedPhoto = sharedPref.getString("teacher_photo", null)
        val username = sharedPref.getString("teacher_username", "") ?: ""

        // 3. UI Güncelleme (ViewBinding Kullanarak)
        // Not: XML'deki ID'lerin binding nesnesinde karşılığı olduğundan emin olun.
        binding.apply {
            // İsim ve Karşılama (txtTeacherGreeting ID'sine sahip TextView)
            // Eğer id'niz farklıysa örn: binding.txtGreeting.text = ...
            txtTeacherGreeting.text = "Hoş geldiniz,\n$realName"

            // Branş/Bölüm (txtTeacherBranch ID'sine sahip TextView)
            txtTeacherBranch.text = branch

            // 4. Profil Fotoğrafını Yükle ve Tıklanabilir Yap
            // Hem büyük hem küçük avatar ihtimaline karşı ikisini de deneyelim (Hangisi varsa)
            val imgProfile = if (imgTeacherProfileLarge != null) imgTeacherProfileLarge else imgTeacherProfileSmall

            if (!savedPhoto.isNullOrEmpty()) {
                try {
                    val photoUri = Uri.parse(savedPhoto)
                    imgProfile?.setImageURI(photoUri)
                    imgProfile?.scaleType = ImageView.ScaleType.CENTER_CROP
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Hata durumunda varsayılan bir ikon koyulabilir
                    imgProfile?.setImageResource(R.drawable.ic_teacher_avatar_default)
                }
            }

            // AVATARA TIKLAYINCA PROFİLE GİT
            imgProfile?.setOnClickListener {
                triggerHapticFeedback(31)
                val intent = Intent(this@TeacherActivity, TeacherProfileActivity::class.java)
                intent.putExtra("from_main", true)
                startActivity(intent)
            }
        }

        // 5. İstatistikleri Firebase'den Çek (Öğrenci Sayısı ve Ders Sayısı)
        if (username.isNotEmpty()) {
            db.child("teachers").child(username).get().addOnSuccessListener { snapshot ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener

                // Toplam katılan öğrenci sayısı
                val totalStudents = snapshot.child("totalStudentsCount").getValue(Int::class.java) ?: 1
                // Toplam açılan ders kodu sayısı
                val totalSessions = snapshot.child("totalSessionsCount").getValue(Int::class.java) ?: 1

                // İstatistik kartlarındaki değerleri güncelle
                binding.apply {
                    txtStudentsCount.text = totalStudents.toString()
                    // Eğer XML'de txtSessionsCount varsa:
                    txtSessionsCount.text = totalSessions.toString()
                }
            }.addOnFailureListener {
                // Hata durumunda (İnternet yoksa vb.) yerel verileri göster
                val localSessions = sharedPref.getInt("total_lessons_count", 1)
                binding.txtSessionsCount.text = localSessions.toString()
            }
        }
    }
    private fun incrementSessionCount() {
        val sharedPref = getSharedPreferences("TeacherProfile", Context.MODE_PRIVATE)
        val username = sharedPref.getString("teacher_username", "")

        if (!username.isNullOrEmpty()) {
            val teacherRef = db.child("teachers").child(username).child("totalSessionsCount")
            teacherRef.get().addOnSuccessListener { snapshot ->
                val currentCount = snapshot.getValue(Int::class.java) ?: 1
                teacherRef.setValue(currentCount + 1) // Her yeni ders kodunda 1 artır
            }
        }
    }

    private fun generateNewSession() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_new_session, null)

        val edtUni = dialogView.findViewById<EditText>(R.id.edtUniversity)
        val edtDept = dialogView.findViewById<EditText>(R.id.edtDepartment)
        val edtTopic = dialogView.findViewById<EditText>(R.id.edtLessonTopic)
        val edtClass = dialogView.findViewById<EditText>(R.id.edtClassRoom)
        val btnStart = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnStartLessonCustom)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancelCustom)

        val sharedPref = getSharedPreferences("TeacherProfile", Context.MODE_PRIVATE)
        val teacherNameFromPref = sharedPref.getString("teacher_name", "Eğitmen") ?: "Eğitmen"

        edtUni.setText(sharedPref.getString("teacher_university", ""))
        edtDept.setText(sharedPref.getString("teacher_branch", ""))

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnStart.setOnClickListener {
            val universityName = edtUni.text.toString().trim()
            val departmentName = edtDept.text.toString().trim()
            val lessonTopic = edtTopic.text.toString().trim()
            val classRoomNo = edtClass.text.toString().trim()

            if (universityName.isNotEmpty() && lessonTopic.isNotEmpty() && classRoomNo.isNotEmpty()) {
                val allowedChars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
                val newCode = (1..8).map { allowedChars.random() }.joinToString("")

                // KRİTİK: sessionCode'u anında sınıfa ata (Adım 2 hatasını çözer)
                this.sessionCode = newCode
                this.sessionStartTime = System.currentTimeMillis()

                binding.apply {
                    txtCode.text = newCode
                    layoutEmptyState.visibility = View.GONE   // Yeni ders butonunu gizle
                    layoutActiveSession.visibility = View.VISIBLE // Kodu ve Bitir butonunu göster
                }

                // Firebase Veri Paketi (config eklendi - Adım 6 için)
                val sessionData = mapOf(
                    "active" to true,
                    "teacher_name" to teacherNameFromPref,
                    "university" to universityName,
                    "department" to departmentName,
                    "topic" to lessonTopic,
                    "class_no" to classRoomNo,
                    "start_time" to sessionStartTime,
                    "isSubtitlesActive" to binding.switchLiveSubtitles.isChecked,
                    "config" to mapOf("buttonsEnabled" to true) // Başlangıçta kilit açık
                )

                db.child("sessions").child(newCode).setValue(sessionData)
                    .addOnSuccessListener {
                        if (isFinishing || isDestroyed) return@addOnSuccessListener

                        incrementSessionCount()
                        listenAllFirebaseData(newCode)
                        listenForStudentMessages()
                        quizManager.startListening(newCode)
                        listenForEmergencies(newCode)
                        listenForLiveQuiz(newCode)
                        resetStatsAndChart()
                        listenSessionStats(newCode)

                        dialog.dismiss()
                        triggerHapticFeedback(51)


                    }
            } else {
                Toast.makeText(this, "Lütfen gerekli alanları doldurun!", Toast.LENGTH_SHORT).show()
            }
        }
        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun listenAllFirebaseData(code: String) {
        if (code.isEmpty()) return
        binding.txtCode.text = code

        db.child("sessions").child(code).child("users").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (isFinishing || isDestroyed) return

                lifecycleScope.launch(Dispatchers.Default) {
                    try {
                        val newList = mutableListOf<FeedbackModel>()
                        var onlineCount = 0 // Sayacı her zaman 0'dan başlatıyoruz

                        for (userSnap in snapshot.children) {
                            val status = userSnap.child("type").getValue(String::class.java) ?: "offline"

                            // SADECE durumu "offline" olmayanları say (online, understood vb. hepsi online'dır)
                            if (status != "offline") {
                                onlineCount++
                            }

                            // Veriyi modele dönüştür ve userId/type alanlarını ata
                            userSnap.getValue(FeedbackModel::class.java)?.let { model ->
                                // Bu satırların hata vermemesi için Model dosyasında 'var' yapmalısın
                                model.userId = userSnap.key ?: ""
                                model.type = status
                                newList.add(model)
                            }
                        }

                        withContext(Dispatchers.Main) {
                            // Sayaç kutusunu güncelle (Hiç online yoksa 0 yazar)
                            binding.txtStudentsCount.text = onlineCount.toString()

                            feedbackList.clear()
                            feedbackList.addAll(newList)
                            feedbackList.sortByDescending { it.lastTimestamp }
                            feedbackAdapter.notifyDataSetChanged()

                            if (feedbackList.isNotEmpty()) updatePerformanceChart()
                        }
                    } catch (e: Exception) {
                        Log.e("Firebase_Error", "Hata: ${e.message}")
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })


        // --- 3. CANLI MESAJLARI (BUTON BASIMLARINI) DİNLE ---
        db.child("sessions").child(code).child("messages").addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val messageTime = snapshot.child("timestamp").getValue(Long::class.java) ?: 1L

                // Sadece ders başladıktan sonra gelenleri göster
                if (messageTime > sessionStartTime) {
                    val type = snapshot.child("type").getValue(String::class.java) ?: ""
                    val name = snapshot.child("name").getValue(String::class.java) ?: "Öğrenci"
                    val message = snapshot.child("message").getValue(String::class.java) ?: ""

                    runOnUiThread {
                        showMessagePanel(name, message, type)
                    }
                }
            }
            override fun onChildChanged(s: DataSnapshot, p: String?) {}
            override fun onChildRemoved(s: DataSnapshot) {}
            override fun onChildMoved(s: DataSnapshot, p: String?) {}
            override fun onCancelled(e: DatabaseError) {}
        })


    }
    private fun updatePerformanceChart() {
        // 2. Verileri Hazırla (Anlayan, Anlamayan, Tekrar, Acil)
        val u = binding.txtUnderstoodCount.text.toString().toFloatOrNull() ?: 1f
        val nu = binding.txtNotUnderstoodCount.text.toString().toFloatOrNull() ?: 1f

        // Diğer verileri feedback listesinden anlık sayalım
        val r = feedbackList.count { it.type == "repeat" }.toFloat()
        val e = feedbackList.count { it.type == "emergency" }.toFloat()

        val entries = ArrayList<com.github.mikephil.charting.data.BarEntry>()
        entries.add(com.github.mikephil.charting.data.BarEntry(1f, u))
        entries.add(com.github.mikephil.charting.data.BarEntry(2f, nu))
        entries.add(com.github.mikephil.charting.data.BarEntry(3f, r))
        entries.add(com.github.mikephil.charting.data.BarEntry(4f, e))

        val dataSet = com.github.mikephil.charting.data.BarDataSet(entries, "Sınıf Durumu")

        // 3. RENKLER: Her sütuna ayrı renk tanımlıyoruz
        dataSet.colors = listOf(
            Color.parseColor("#5CAF50"), // Yeşil (Anlayan)
            Color.parseColor("#F44337"), // Kırmızı (Anlamayan)
            Color.parseColor("#FFC108"), // Sarı (Tekrar)
            Color.parseColor("#10C27B0")  // Mor (Acil)
        )

        dataSet.valueTextSize = 13f
        dataSet.setDrawValues(true)

        val barData = com.github.mikephil.charting.data.BarData(dataSet)
        barData.barWidth = 1.6f

        binding.performanceChart.apply {
            // Çizgi grafiğinden Sütun grafiğine geçiş ayarları
            data = barData

            // 4. TIKLAYINCA HAREKET ETME (Zıplama Efekti)
            setOnChartValueSelectedListener(object : com.github.mikephil.charting.listener.OnChartValueSelectedListener {
                override fun onValueSelected(e: com.github.mikephil.charting.data.Entry?, h: com.github.mikephil.charting.highlight.Highlight?) {
                    animateY(401) // Sütuna basınca grafik zıplar
                    triggerHapticFeedback(31) // Titreşim verir
                }
                override fun onNothingSelected() {}
            })

            // Görsel Ayarlar
            description.isEnabled = false
            legend.isEnabled = false
            xAxis.apply {
                position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 2f
                valueFormatter = com.github.mikephil.charting.formatter.IndexAxisValueFormatter(
                    listOf("Anlayan", "Anlamayan", "Tekrar", "Acil")
                )
            }
            axisLeft.axisMinimum = 1f
            axisRight.isEnabled = false

            invalidate() // Grafiği yenile
        }

        // 5. SAATİ GÜNCELLE (Sağ alt köşedeki TextView için)
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        binding.txtChartTime.text = "Son Güncelleme: $currentTime"
    }
    private fun showMessagePanel(name: String, message: String, type: String) {
        // 2. Gelen tipe göre hangi tasarımı (Layout) kullanacağımızı seçiyoruz
        val layoutId = when (type) {
            "understood" -> R.layout.dialog_notif_understood
            "not_understood" -> R.layout.dialog_notif_not_understood // Eğer bu dosya yoksa understood kullanabilirsin
            "repeat" -> R.layout.dialog_notif_repeat
            else -> R.layout.dialog_notif_understood
        }

        // 3. Diyaloğu oluşturma
        val dialogView = LayoutInflater.from(this).inflate(layoutId, null)
        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        // Arka planı şeffaf yapıyoruz (XML'deki yuvarlak köşeler görünsün diye)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // 4. Verileri XML'e yerleştirme (Senin paylaştığın XML ID'lerini kullanıyoruz)
        val txtName = dialogView.findViewById<TextView>(R.id.txtNotifStudentName)
        val txtNote = dialogView.findViewById<TextView>(R.id.txtNotifStudentNote)
        val btnOk = dialogView.findViewById<Button>(R.id.btnNotifOk)
        val imgAvatar = dialogView.findViewById<ImageView>(R.id.imgNotifAvatar)
        imgAvatar?.setOnClickListener {
            triggerHapticFeedback(31)
            // Burada 'name' ve 'message' zaten fonksiyon parametresi olarak geliyor.
            // Eğer öğrenci nesnesi (student) elindeyse bölüm bilgisini de ekleyebilirsin.
            showFullScreenImage(null, name, "Geri Bildirim Detayı")
        }
        txtName?.text = name
        txtNote?.text = message

        // 5. SES ÇALMA (Sadece Öğretmende)
        // raw klasöründeki dosyalarının isimlerinin bunlarla aynı olduğundan emin ol
        playNotificationSound(type)

        // 6. Kapatma Butonu
        btnOk?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun playNotificationSound(type: String) {
        try {
            val soundResId = when (type) {
                "understood" -> R.raw.anladim // raw içindeki dosya adın
                "not_understood" -> R.raw.anlamadim
                "repeat" -> R.raw.tekrar
                "emergency" -> R.raw.emergency_alert
                else -> R.raw.tiklama
            }

            val mediaPlayer = android.media.MediaPlayer.create(this, soundResId)
            mediaPlayer.start()
            mediaPlayer.setOnCompletionListener { it.release() } // Bitince hafızadan sil
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }





    private fun showFullScreenImage(photoData: String?, studentName: String, studentInfo: String) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_full_image, null)

        val imgFull = view.findViewById<ImageView>(R.id.imgFull)
        val imgFullBlur = view.findViewById<ImageView>(R.id.imgFullBlur)
        val txtFullTitle = view.findViewById<TextView>(R.id.txtFullTitle)
        val txtInfo = view.findViewById<TextView>(R.id.txtFullSubtitle)
        txtFullTitle.text = studentName
        txtInfo?.text = studentInfo // Burada "Bölüm | No" bilgisi görünecek

        // --- CİNSİYET TEMALI RENK VE GÖRSEL MANTIĞI ---
        when (photoData) {
            "female_default" -> {
                imgFull.setImageResource(R.drawable.ic_student_female)
                imgFullBlur.setImageResource(R.drawable.ic_student_female)
                txtFullTitle.setTextColor(Color.parseColor("#FFC1CB")) // Pembe Başlık
            }
            "male_default" -> {
                imgFull.setImageResource(R.drawable.ic_student_male)
                imgFullBlur.setImageResource(R.drawable.ic_student_male)
                txtFullTitle.setTextColor(Color.parseColor("#008AFF")) // Mavi Başlık
            }
            null -> {
                imgFull.setImageResource(R.drawable.ic_anonymous_user)
                imgFullBlur.setImageResource(R.drawable.ic_anonymous_user)
            }
            else -> {
                // ÖĞRENCİNİN KENDİ FOTOĞRAFI VARSA
                try {
                    val uri = Uri.parse(photoData)
                    imgFull.setImageURI(uri)
                    imgFullBlur.setImageURI(uri)
                    txtFullTitle.setTextColor(Color.WHITE)
                } catch (e: Exception) {
                    imgFull.setImageResource(R.drawable.ic_student_male)
                    txtFullTitle.setTextColor(Color.WHITE)
                }
            }
        }

        view.findViewById<View>(R.id.btnCloseFull).setOnClickListener { dialog.dismiss() }
        dialog.setContentView(view)
        dialog.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
    }
}

class FeedbackAdapter(
    private val list: List<FeedbackModel>,
    private val onImageClick: (String?, String, String, String, String) -> Unit
) : RecyclerView.Adapter<FeedbackAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val avatarCard: com.google.android.material.card.MaterialCardView = v.findViewById(R.id.avatarCard)
        val img: ImageView = v.findViewById(R.id.imgStudentAvatar)
        val name: TextView = v.findViewById(R.id.txtStudentName)
        val txtStudentDept: TextView = v.findViewById(R.id.txtStudentDept)
        val uCount: TextView = v.findViewById(R.id.txtUnderstoodCount)
        val rCount: TextView = v.findViewById(R.id.txtRepeatCount)
        val nCount: TextView = v.findViewById(R.id.txtNotUnderstoodCount)
        val statusBadge: View = v.findViewById(R.id.viewStatusBadge)
        val statusText: TextView = v.findViewById(R.id.txtFeedbackType)
        val statusCard: com.google.android.material.card.MaterialCardView = v.findViewById(R.id.feedbackChip)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_feedback, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, p: Int) {
        val item = list[p]
        val isFemale = item.gender.lowercase(Locale.getDefault()) == "kadın"

        // --- ADIM 7: Kimliği Gizle Mantığı ---
        if (item.isAnonymous) {
            h.name.text = "🔒 Gizli Katılımcı"
            h.txtStudentDept.text = "Bilgiler Gizli"
            h.img.setImageResource(R.drawable.ic_anonymous_user)
            h.avatarCard.setStrokeColor(ColorStateList.valueOf(Color.GRAY))
            h.itemView.setOnClickListener {
                Toast.makeText(h.itemView.context, "Öğrenci kimliğini gizledi.", Toast.LENGTH_SHORT).show()
            }
        } else {

            h.name.text = item.name
            h.txtStudentDept.text = "${item.department} | No: ${item.no}"

            // Cinsiyete göre renk
            val strokeColor = if (isFemale) "#FFC1CB" else "#007AFF"
            h.avatarCard.setStrokeColor(ColorStateList.valueOf(Color.parseColor(strokeColor)))

            // Fotoğraf yükleme
            if (!item.photo.isNullOrBlank()) {
                try {
                    h.img.setImageURI(Uri.parse(item.photo))
                } catch (e: Exception) {
                    setDefaultAvatar(h, item)
                }
            } else {
                setDefaultAvatar(h, item)
            }

            // ADIM 6: Fotoğrafa Tıklayınca Tam Ekran Aç (Geri Bildirim Paneli)
            val displayPhoto = if (!item.photo.isNullOrBlank()) item.photo
            else (if (isFemale) "female_default" else "male_default")

            val clickAction = View.OnClickListener {
                onImageClick(displayPhoto, item.name, item.department, item.no, item.userId)
            }
            h.img.setOnClickListener(clickAction)
            h.itemView.setOnClickListener(clickAction)
        }

        // SAYAÇLAR: Asla gizlenmez
        h.uCount.text = "${item.understoodCount} OK"
        h.rCount.text = "${item.repeatCount} RPT"
        h.nCount.text = "${item.notUnderstoodCount} NO"

        // DURUM IŞIĞI
        val isRecentlyActive = (System.currentTimeMillis() - item.lastTimestamp) < 300000
        if (item.type == "offline" || !isRecentlyActive) {
            h.statusText.text = "AYRILDI"
            h.statusBadge.setBackgroundResource(R.drawable.bg_status_offline)
            h.statusCard.setCardBackgroundColor(Color.parseColor("#FEE3E2"))
        } else {
            h.statusText.text = "AKTİF 🟢"
            h.statusBadge.setBackgroundResource(R.drawable.bg_status_online)
            h.statusCard.setCardBackgroundColor(Color.parseColor("#DCFCE8"))
        }
    }

    private fun setDefaultAvatar(h: VH, item: FeedbackModel) {
        if (item.gender.lowercase(Locale.getDefault()) == "kadın") {
            h.img.setImageResource(R.drawable.ic_student_female)
        } else {
            h.img.setImageResource(R.drawable.ic_student_male)
        }
    }

    override fun getItemCount(): Int = list.size
}
