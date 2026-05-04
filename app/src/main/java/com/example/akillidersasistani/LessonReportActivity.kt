package com.example.akillidersasistani

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.akillidersasistani.databinding.ActivityLessonReportBinding
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * =========================================================================================
 * PROJECT: AKILLI DERS ASISTANI - NEURAL REPORT ENGINE
 * VERSION: V2.0 [ARCHIVE ENHANCED]
 *
 * CORE RESPONSIBILITIES:
 * 1. AI Analysis via Gemini 1.5 Flash
 * 2. Dynamic Content Parsing ([ÖZET], [ODAK], [UYARI])
 * 3. Firebase Neural Archive Synchronization
 * 4. High-Fidelity Accordion UI Animations
 * =========================================================================================
 */
class LessonReportActivity : AppCompatActivity() {

    // --- ORCHESTRATION LAYER ---
    private lateinit var binding: ActivityLessonReportBinding
    private val aiManager = GeminiAiManager()
    private val database = FirebaseDatabase.getInstance().reference
    private val TAG = "NEURAL_REPORT_ENGINE"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. VIEW BINDING INITIALIZATION
        binding = ActivityLessonReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 2. SYSTEM ARCHITECTURE SETUP
        initializeSystemUI()
        setupAccordionLogic()

        // 3. TELEMETRY & DATA PROCESSING
        processIncomingIntentData()
    }

    /**
     * Gelen veriyi analiz eder; Geçmişten mi yoksa yeni bir analiz mi olduğunu belirler.
     */
    private fun processIncomingIntentData() {
        val isFromHistory = intent.getBooleanExtra("IS_FROM_HISTORY", false)
        val lessonTitle = intent.getStringExtra("LESSON_TITLE") ?: "Genel Analiz"
        val aiResultRaw = intent.getStringExtra("AI_RESULT") ?: ""

        // UI Başlığını Güncelle
        binding.txtReportTitle.text = lessonTitle

        if (isFromHistory && aiResultRaw.isNotEmpty()) {
            Log.d(TAG, "Loading from Neural Archive...")
            executeArchiveMode(aiResultRaw)
        } else {
            Log.d(TAG, "Initiating Live Neural Analysis...")
            executeLiveAnalysisMode(lessonTitle)
        }
    }

    /**
     * ARŞİV MODU: Firebase'den gelen ham metni parçalayıp ekrana basar.
     */
    private fun executeArchiveMode(rawContent: String) {
        binding.loadingOverlay.visibility = View.GONE
        renderReportContent(rawContent)
    }

    /**
     * CANLI ANALİZ MODU: Yapay zekayı tetikler.
     */
    private fun executeLiveAnalysisMode(title: String) {
        val rawText = intent.getStringExtra("RAW_TEXT") ?: ""
        if (rawText.isNotEmpty()) {
            startNeuralInference(rawText, title)
        } else {
            handleSystemError("Analiz edilecek kaynak metin bulunamadı.")
        }
    }

    /**
     * GEMINI AI INFERENCE ENGINE
     * Canlı ses transkriptini veya notu analiz eder.
     */
    private fun startNeuralInference(sourceText: String, title: String) {
        // Yükleme ekranını aktif et
        binding.loadingOverlay.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Gemini Analysis Call
                val analysisResult = aiManager.analyzeLesson(sourceText)

                // Ham metni (Raw) Firebase formatına uygun hale getir
                val rawForArchive = "[ÖZET]: ${analysisResult.summary}\n" +
                        "[ODAK]: ${analysisResult.predictions}\n" +
                        "[UYARI]: ${analysisResult.nonExamples}"

                withContext(Dispatchers.Main) {
                    finalizeReport(title, rawForArchive)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    handleSystemError("Neural Inference Failed: ${e.localizedMessage}")
                }
            }
        }
    }

    /**
     * Analiz sonuçlarını ekrana basar ve buluta yedekler.
     */
    private fun finalizeReport(title: String, rawContent: String) {
        binding.loadingOverlay.visibility = View.GONE

        // 1. UI Rendering
        renderReportContent(rawContent)

        // 2. Cloud Synchronization (Asenkron Kayıt)
        synchronizeWithCloudArchive(title, rawContent)

        // 3. Success Animation
        animateEntranceEffect()
    }

    /**
     * PARSING ENGINE: Ham AI metnini akordiyon bölümlerine dağıtır.
     */
    private fun renderReportContent(raw: String) {
        try {
            // RegEx veya Substring ile veriyi ayıklıyoruz
            val summary = raw.substringAfter("[ÖZET]:").substringBefore("[ODAK]:").trim()
            val focus = raw.substringAfter("[ODAK]:").substringBefore("[UYARI]:").trim()
            val alerts = raw.substringAfter("[UYARI]:").trim()

            // View Entegrasyonu
            binding.txtAiSummary.text = summary.ifEmpty { "Özet bilgisi işlenemedi." }
            binding.txtExamPredictions.text = focus.ifEmpty { "Kritik odak noktası bulunamadı." }
            binding.txtNonExamples.text = alerts.ifEmpty { "Dikkat edilecek uyarı bulunmuyor." }

            animateEntranceEffect()
            Log.d(TAG, "Content Rendering Completed Successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Parsing Error: ${e.message}")
            binding.txtAiSummary.text = raw // Hata durumunda tüm metni göster
        }
    }

    /**
     * FIREBASE CLOUD SYNC: Raporu öğrenci bazlı arşivler.
     */
    private fun synchronizeWithCloudArchive(title: String, content: String) {
        val prefs = getSharedPreferences("UserProfile", Context.MODE_PRIVATE)
        val studentNo = prefs.getString("ogrenci_no", "unknown_user") ?: "unknown_user"

        val reportReference = database.child("lesson_reports").child(studentNo).push()
        val reportId = reportReference.key ?: return

        val reportPayload = mapOf(
            "id" to reportId,
            "title" to title,
            "rawContent" to content,
            "timestamp" to System.currentTimeMillis()
        )

        reportReference.setValue(reportPayload)
            .addOnSuccessListener { Log.i(TAG, "Archive Synced: $reportId") }
            .addOnFailureListener { e -> Log.e(TAG, "Sync Failed: ${e.message}") }
    }

    /**
     * UI NAVIGATION & SYSTEM FEEDBACK
     */
    private fun initializeSystemUI() {
        binding.btnBackReport.setOnClickListener {
            provideHapticFeedback(30)
            finish()
        }

        binding.btnShareReport.setOnClickListener {
            provideHapticFeedback(50)
            Toast.makeText(this, "Rapor PDF olarak hazırlanıyor...", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * ACCORDION SYSTEM: Expand/Collapse Engine
     */
    private fun setupAccordionLogic() {
        binding.headerSummary.setOnClickListener {
            toggleAccordionSection(binding.contentSummary, binding.arrowSummary)
        }
        binding.headerPredictions.setOnClickListener {
            toggleAccordionSection(binding.contentPredictions, binding.arrowPredictions)
        }
        binding.headerAlerts.setOnClickListener {
            toggleAccordionSection(binding.contentAlerts, binding.arrowAlerts)
        }
    }

    private fun toggleAccordionSection(content: View, arrow: ImageView) {
        provideHapticFeedback(20)

        val transition = AutoTransition().apply {
            duration = 350
            interpolator = AccelerateDecelerateInterpolator()
        }

        TransitionManager.beginDelayedTransition(binding.accordionContainer, transition)

        if (content.visibility == View.VISIBLE) {
            content.visibility = View.GONE
            arrow.animate().rotation(0f).setDuration(300).start()
        } else {
            content.visibility = View.VISIBLE
            arrow.animate().rotation(90f).setDuration(300).start()
        }
    }

    private fun animateEntranceEffect() {
        val animation = AnimationUtils.loadAnimation(this, R.anim.ios_zoom_in)
        binding.accordionContainer.startAnimation(animation)
    }

    private fun provideHapticFeedback(duration: Long) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }

    private fun handleSystemError(message: String) {
        binding.loadingOverlay.visibility = View.GONE
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Log.e(TAG, "Critical System Error: $message")
    }
}