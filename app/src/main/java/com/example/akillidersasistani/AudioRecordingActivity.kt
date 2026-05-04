package com.example.akillidersasistani

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.*
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.semantics.text
import com.example.akillidersasistani.databinding.ActivityAudioRecordingBinding
import java.util.*

/**
 * =========================================================================================
 * PROJECT: AKILLI DERS ASİSTANI - AI VOICE STUDIO PRO
 * VERSION: V4.0 [ULTRA PREMIUM]
 * DESIGN SYSTEM: APPLE iOS 18 STUDIO MODE
 *
 * CORE FEATURES:
 * - Real-time Waveform Rendering (60 FPS)
 * - Live Transcript Streaming (Speech-to-Text)
 * - High-Precision Studio Timer
 * - Gemini AI Integration Bridge
 * =========================================================================================
 */
class AudioRecordingActivity : AppCompatActivity() {

    // --- [1] UI BINDING & SYSTEM INFRASTRUCTURE ---
    private lateinit var binding: ActivityAudioRecordingBinding
    private lateinit var audioHandler: AudioRecorderHandler
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var timerRunnable: Runnable

    // --- [2] STATE VARIABLES ---
    private var secondsElapsed = 0
    private var isRecordingActive = false
    private var capturedTranscript = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ViewBinding Layer Initialization
        binding = ActivityAudioRecordingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Android 14+ UI StatusBar Tuning
        window.statusBarColor = Color.BLACK

        initializeStudioEngine()
        setupInteractionListeners()

        // 1.5 saniyelik odaklanma süresinden sonra kaydı otomatik başlat
        mainHandler.postDelayed({
            startStudioSession()
        }, 1500)
    }

    /**
     * STUDIO ENGINE: Ses ve kelime yakalama motorunu bağlar.
     */
    private fun initializeStudioEngine() {
        audioHandler = AudioRecorderHandler(this)

        // 🔊 [BURAYA EKLEDİM]: SES ŞİDDETİ (RMS) GÜNCELLEMESİ
        audioHandler.onVolumeUpdate = { rms ->
            runOnUiThread {
                // RMS değerini görselleştiriciye (Custom View) gönderiyoruz
                binding.audioVisualizer.addAmplitude(rms)
            }
        }

        // ✍️ [BURAYA EKLEDİM]: KELİME AKIŞI (TRANSCRIPT) GÜNCELLEMESİ
        audioHandler.onTranscriptUpdate = { word ->
            runOnUiThread {
                capturedTranscript.append(word).append(" ")
                binding.txtTranscriptLive.text = word.uppercase()

                // Apple Taptic Engine simülasyonu (Hafif vuruş)
                binding.txtTranscriptLive.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }
    }

    /**
     * UI INTERACTION: Buton tıklamalarını ve Apple animasyonlarını yönetir.
     */
    private fun setupInteractionListeners() {
        // Kaydı Bitir ve Analiz Et Butonu
        binding.btnFinishAndAnalyze.setOnClickListener {
            finalizeStudioSession()
        }

        // Kapat Butonu (Kaydı İptal Et)
        binding.btnCloseStudio.setOnClickListener {
            abortStudioSession()
        }
    }

    /**
     * SESSION START: Kayıt sürecini ve zamanlayıcıyı ateşler.
     */
    private fun startStudioSession() {
        try {
            audioHandler.startRecording()
            isRecordingActive = true

            startStudioClock()
            executeMicrophonePulse()

            Toast.makeText(this, "AI Stüdyo Bağlantısı Kuruldu", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            handleStudioError(e)
        }
    }

    /**
     * PRECISION CLOCK: Saniyelik profesyonel zamanlayıcı.
     */
    private fun startStudioClock() {
        timerRunnable = object : Runnable {
            override fun run() {
                if (isRecordingActive) {
                    secondsElapsed++
                    val h = secondsElapsed / 3600
                    val m = (secondsElapsed % 3600) / 60
                    val s = secondsElapsed % 60

                    binding.txtRecordingTimer.text = String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
                    mainHandler.postDelayed(this, 1000)
                }
            }
        }
        mainHandler.post(timerRunnable)
    }

    /**
     * VISUAL PULSE: Mikrofonun etrafındaki halkanın Apple tarzı animasyonu.
     */
    private fun executeMicrophonePulse() {
        val pulse = ScaleAnimation(1.0f, 1.25f, 1.0f, 1.25f,
            Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f).apply {
            duration = 1200
            repeatCount = Animation.INFINITE
            repeatMode = Animation.REVERSE
        }
        // Eğer XML'de pulseCircle ID'si varsa başlatılır
        // binding.pulseCircle.startAnimation(pulse)
    }

    /**
     * FINALIZE: Kaydı durdurur ve veriyi Gemini AI Analiz paneline gönderir.
     */
    private fun finalizeStudioSession() {
        isRecordingActive = false
        mainHandler.removeCallbacks(timerRunnable)

        val finalResult = audioHandler.stopRecording()

        binding.btnFinishAndAnalyze.isEnabled = false
        binding.btnFinishAndAnalyze.text = "AI ANALİZİ BAŞLATILIYOR..."
        binding.btnFinishAndAnalyze.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#5856D6"))

        // ANALİZ PANELİNE (LessonReportActivity) YÖNLENDİRME
        val intent = Intent(this, LessonReportActivity::class.java)
        intent.putExtra("RAW_TEXT", finalResult)
        intent.putExtra("LESSON_TITLE", "Canlı Ders Analizi")
        startActivity(intent)

        finish()
    }

    private fun abortStudioSession() {
        isRecordingActive = false
        audioHandler.stopRecording()
        finish()
    }

    private fun handleStudioError(e: Exception) {
        Toast.makeText(this, "Hata: ${e.message}", Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onDestroy() {
        isRecordingActive = false
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}