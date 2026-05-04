package com.example.akillidersasistani

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.*
import android.util.DisplayMetrics
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.akillidersasistani.databinding.ActivityFocusMatrixBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import java.util.*
import kotlin.random.Random

/**
 * =========================================================================================
 * PROJECT: AKILLI DERS ASISTANI - FOCUS MATRIX PRO
 * VERSION: V7.0 MASTER ENGINE [STÜDYO SÜRÜMÜ]
 *
 * CORE ARCHITECTURE:
 * - Neural Pattern Recognition (NPR) Motoru
 * - Dinamik Matematiksel Kafes (Safe-Zone Grid Scaling)
 * - Firebase Real-time Leaderboard Entegrasyonu
 * - Gelişmiş Haptik Geri Bildirim Sistemi
 *
 * DEVELOPER NOTE: Bu sınıf, bellek yönetimi ve GPU hızlandırmalı animasyonlar için
 * optimize edilmiştir. Taşma ve donma riskleri minimize edilmiştir.
 * =========================================================================================
 */
class FocusMatrixActivity : AppCompatActivity() {

    // --- SİSTEM VE DONANIM BAĞLANTILARI ---
    private lateinit var binding: ActivityFocusMatrixBinding
    private lateinit var soundManager: MathSoundManager
    private lateinit var vibrator: Vibrator
    private val mainHandler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    private val randomGenerator = Random.Default

    // --- OYUN MOTORU PARAMETRELERİ ---
    private var currentLevel = 1
    private var gridSize = 3
    private var sequenceLength = 3
    private var gameSpeedMultiplier = 1.0f
    private val gameSequence = mutableListOf<Int>()
    private val playerInputSequence = mutableListOf<Int>()

    // --- ANALİTİK VERİLER VE DURUM YÖNETİMİ ---
    private var totalScore = 0
    private var currentStreak = 0
    private var totalErrors = 0
    private var isGameOver = false
    private var isWatchingPhase = true
    private var timeLeft: Long = 20000L
    private var userId: String = ""
    private var studentName: String = "Student"

    // --- SABİT DEĞERLER (MATEMATİKSEL KAFES) ---
    companion object {
        private const val TAG = "FocusMatrixEngine"
        private const val MAX_TIME_MS = 20000L
        private const val BASE_SCORE_UNIT = 500
        private const val HAPTIC_STRENGTH_CLICK = 30L
        private const val HAPTIC_STRENGTH_ERROR = 150L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "Initializing Neural Studio Engine...")

        // 1. ViewBinding ve Temel Mimari Kurulumu
        try {
            binding = ActivityFocusMatrixBinding.inflate(layoutInflater)
            setContentView(binding.root)
        } catch (e: Exception) {
            Log.e(TAG, "Binding Error: ${e.message}")
            finish()
            return
        }

        // 2. Alt Sistemlerin Başlatılması
        initializeSystemServices()
        loadUserSessionData()
        applyInitialUIState()

        // 3. Olay Dinleyicileri (Interactions)
        attachGlobalEventListeners()

        Log.d(TAG, "Engine Ready. Awaiting User Initialization.")
    }

    /**
     * Donanım servislerini ve ses yöneticisini hazırlar.
     */
    private fun initializeSystemServices() {
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        soundManager = MathSoundManager(this)
    }

    /**
     * Kullanıcı profilini ve önceki oyun verilerini yükler.
     */
    private fun loadUserSessionData() {
        val prefs = getSharedPreferences("UserProfile", Context.MODE_PRIVATE)
        userId = prefs.getString("ogrenci_no", UUID.randomUUID().toString()) ?: ""
        studentName = prefs.getString("name", "Student") ?: "Student"

        // PlayActivity'den gelen parametreler
        currentLevel = intent.getIntExtra("LEVEL_ID", 1)
        totalScore = intent.getIntExtra("SCORE", 0)
        currentStreak = intent.getIntExtra("STREAK", 0)
        gameSpeedMultiplier = intent.getFloatExtra("GAME_SPEED", 1.0f)

        Log.d(TAG, "Session Loaded: Level $currentLevel, Speed $gameSpeedMultiplier")
    }

    /**
     * Arayüzü başlangıç durumuna getirir ve giriş animasyonlarını oynatır.
     */
    private fun applyInitialUIState() {
        updateStatsPanel()

        // Premium Matrix Açılış Animasyonu
        binding.matrixCard.apply {
            alpha = 0f
            scaleX = 0.8f
            scaleY = 0.8f
            translationY = 150f
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(1200)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        // --- COMPACT BENTO (Üst Panel) GİRİŞ ANİMASYONU ---
        // Yeni XML'deki ID 'compactBento' olarak güncellendi.
        binding.compactBento.apply {
            alpha = 0f
            translationY = -60f // Çok kaba olmayan, profesyonel bir süzülme mesafesi
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(900)
                .setStartDelay(300) // Matrix kartından hemen sonra başlar
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }
    }

    /**
     * Buton tıklamalarını ve navigasyon olaylarını yönetir.
     */
    private fun attachGlobalEventListeners() {
        binding.btnStartGame.setOnClickListener {
            triggerAdvancedHaptic(HAPTIC_STRENGTH_CLICK)
            initiateGameFlow()
        }

        binding.btnBackFromFocus.setOnClickListener {
            triggerAdvancedHaptic(20)
            terminateEngine()
        }

        binding.btnRefreshStatus.setOnClickListener {
            triggerAdvancedHaptic(40)
            it.animate().rotationBy(360f).setDuration(500).start()
            Toast.makeText(this, "Neural Cache Cleared", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Oyun akışını başlatan ana tetikleyici.
     */
    private fun initiateGameFlow() {
        binding.btnStartGame.visibility = View.GONE
        binding.txtStudioStatus.text = "PROCESSING..."
        binding.txtStudioStatus.setTextColor(Color.parseColor("#FF9500"))

        binding.bottomControlPanel.animate()
            .alpha(0.05f) // Tamamen silmek yerine çok hafif bir gölge bırakır (Pro Look)
            .translationY(100f) // Paneli ekranın dışına doğru iter
            .setDuration(700)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .start()

        // --- NEURAL ENGINE ÇALIŞTIRMA ---
        calculateLevelDifficulty()
        buildMathematicalGrid() // Bu fonksiyon artık availableH değerini geniş tuttuğumuz için asla taşma yapmayacak
        prepareRoundLogic()
    }

    /**
     * Seviyeye göre ızgara boyutunu ve dizi uzunluğunu hesaplar.
     */
    private fun calculateLevelDifficulty() {
        gridSize = when {
            currentLevel <= 3 -> 2
            currentLevel <= 10 -> 3
            currentLevel <= 22 -> 4
            else -> 5
        }
        sequenceLength = 2 + currentLevel
        Log.d(TAG, "Difficulty set to $gridSize x $gridSize Matrix with $sequenceLength items.")
    }

    /**
     * TAŞMAYI ÖNLEYEN MATEMATİKSEL KAFES SİSTEMİ
     * Ekran boyutlarını milimetrik hesaplayarak kareleri sığdırır.
     */
    private fun buildMathematicalGrid() {
        binding.matrixGrid.removeAllViews()
        binding.matrixGrid.columnCount = gridSize
        binding.matrixGrid.rowCount = gridSize

        val metrics = resources.displayMetrics

        // Bento, Header ve Analytics bar için ayrılan güvenli alan (dp to px)
        val horizontalReserved = (100 * metrics.density).toInt()
        val verticalReserved = (480 * metrics.density).toInt()

        val availableW = metrics.widthPixels - horizontalReserved
        val availableH = metrics.heightPixels - verticalReserved

        // En dar boyutu seçerek kare formu garanti altına al
        val safeDimension = if (availableW < availableH) availableW else availableH
        val tileSize = safeDimension / gridSize

        for (i in 0 until (gridSize * gridSize)) {
            val tile = LayoutInflater.from(this).inflate(R.layout.item_focus_tile, binding.matrixGrid, false) as MaterialCardView

            val lp = GridLayout.LayoutParams()
            val gap = (tileSize * 0.12).toInt() // Estetik %12 boşluk
            val finalTileSize = tileSize - gap

            lp.width = finalTileSize
            lp.height = finalTileSize
            val m = gap / 2
            lp.setMargins(m, m, m, m)

            tile.layoutParams = lp
            tile.tag = i

            // Kutu iç tasarımını optimize et
            tile.getChildAt(0)?.let { inner ->
                if (inner is MaterialCardView) {
                    inner.setContentPadding(0, 0, 0, 0)
                    val innerLp = inner.layoutParams as? android.widget.FrameLayout.LayoutParams
                    innerLp?.setMargins(6, 6, 6, 6)
                }
            }

            tile.setOnClickListener {
                if (!isWatchingPhase && !isGameOver) {
                    triggerAdvancedHaptic(15)
                    handleUserInteraction(i, tile)
                }
            }
            binding.matrixGrid.addView(tile)
        }
    }

    /**
     * Tur öncesi veri temizliği ve hazırlık yapar.
     */
    private fun prepareRoundLogic() {
        isGameOver = false
        isWatchingPhase = true
        playerInputSequence.clear()
        gameSequence.clear()

        binding.txtFocusInstruction.text = "WATCH NEURAL PATTERN"
        binding.txtFocusInstruction.setTextColor(Color.parseColor("#5856D6"))

        // Örüntü oluşturma
        repeat(sequenceLength) {
            gameSequence.add(randomGenerator.nextInt(0, gridSize * gridSize))
        }

        mainHandler.postDelayed({ executeSequencePlayback() }, 1200)
    }

    /**
     * Bilgisayarın diziyi oyuncuya gösterdiği faz.
     */
    private fun executeSequencePlayback() {
        var accumulatedDelay = 0L
        val stepInterval = (750 / gameSpeedMultiplier).toLong()

        gameSequence.forEachIndexed { index, tag ->
            mainHandler.postDelayed({
                val tile = binding.matrixGrid.findViewWithTag<MaterialCardView>(tag)
                performFlashAnimation(tile, (index + 1).toString())

                // Dizi bitti mi kontrol et
                if (index == gameSequence.size - 1) {
                    mainHandler.postDelayed({
                        transitionToInputPhase()
                    }, stepInterval)
                }
            }, accumulatedDelay)
            accumulatedDelay += stepInterval
        }
    }

    /**
     * Kutu yanma ve numara gösterme efektini yönetir.
     */
    private fun performFlashAnimation(tile: MaterialCardView?, step: String) {
        tile?.let { t ->
            // Katmanları bul
            val outer = t.findViewById<MaterialCardView>(R.id.outerTileCard)
            val inner = t.findViewById<MaterialCardView>(R.id.innerTileCard)
            val txtNumber = t.findViewById<TextView>(R.id.txtTileNumber)

            // Renk Tanımları
            val purple = ColorStateList.valueOf(Color.parseColor("#5856D6"))
            val neutralOuter = ColorStateList.valueOf(Color.parseColor("#E5E5EA"))
            val neutralInner = ColorStateList.valueOf(Color.parseColor("#FFFFFF"))

            // --- RENKLERİ DEĞİŞTİR (Açılış) ---
            outer?.setCardBackgroundColor(purple)
            inner?.setCardBackgroundColor(purple)

            txtNumber?.apply {
                text = step
                visibility = View.VISIBLE
                alpha = 0f
                animate().alpha(1f).setDuration(200).start()
            }

            triggerAdvancedHaptic(20)

            // --- ESKİ HALİNE DÖNDÜR (Kapanış) ---
            mainHandler.postDelayed({
                outer?.setCardBackgroundColor(neutralOuter)
                inner?.setCardBackgroundColor(neutralInner)

                txtNumber?.animate()?.alpha(0f)?.setDuration(200)
                    ?.withEndAction { txtNumber.visibility = View.GONE }
                    ?.start()
            }, 500)
        }
    }

    /**
     * Oyuncunun giriş yapabileceği faza geçiş yapar.
     */
    private fun transitionToInputPhase() {
        isWatchingPhase = false
        binding.txtFocusInstruction.text = "YOUR TURN: REPLICATE"
        binding.txtFocusInstruction.setTextColor(Color.parseColor("#34C759"))

        timeLeft = MAX_TIME_MS
        initiateCountdownTimer()
    }

    private fun handleUserInteraction(tag: Int, tile: MaterialCardView) {
        playerInputSequence.add(tag)
        val currentStep = playerInputSequence.size - 1

        if (playerInputSequence[currentStep] == gameSequence[currentStep]) {
            // --- BAŞARILI SEÇİM: ÇİFT KATMANLI YEŞİL ---
            val outer = tile.findViewById<MaterialCardView>(R.id.outerTileCard)
            val inner = tile.findViewById<MaterialCardView>(R.id.innerTileCard)

            val green = ColorStateList.valueOf(Color.parseColor("#34C759"))
            val neutralOuter = ColorStateList.valueOf(Color.parseColor("#E5E5EA"))
            val neutralInner = ColorStateList.valueOf(Color.parseColor("#FFFFFF"))

            outer?.setCardBackgroundColor(green)
            inner?.setCardBackgroundColor(green)

            // 250ms sonra orijinal Apple gri/beyaz renklerine dön
            mainHandler.postDelayed({
                outer?.setCardBackgroundColor(neutralOuter)
                inner?.setCardBackgroundColor(neutralInner)
            }, 250)

            if (playerInputSequence.size == gameSequence.size) {
                mainHandler.postDelayed({ processVictory() }, 300)
            }
        } else {
            // Hata durumunda sadece hatayı işle
            processFailure("NEURAL MISMATCH")
        }
    }
    private fun processVictory() {
        killTimer()
        currentStreak++
        soundManager.play("correct")

        // Puan Algoritması: (Baz Puan * Seviye) + Kalan Zaman Bonusu
        val timeBonus = (timeLeft / 100).toInt()
        val roundScore = (BASE_SCORE_UNIT * currentLevel) + timeBonus
        totalScore += roundScore

        animateScoreText()

        mainHandler.postDelayed({
            val nextIntent = Intent(this, FocusMatrixActivity::class.java).apply {
                putExtra("LEVEL_ID", currentLevel + 1)
                putExtra("SCORE", totalScore)
                putExtra("STREAK", currentStreak)
                putExtra("GAME_SPEED", gameSpeedMultiplier)
            }
            startActivity(nextIntent)
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }, 1200)
    }

    /**
     * Skor metnini profesyonelce büyütüp küçülten animasyon.
     */
    private fun animateScoreText() {
        binding.txtMainScore.animate()
            .scaleX(1.5f)
            .scaleY(1.5f)
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                binding.txtMainScore.text = totalScore.toString()
                binding.txtMainScore.animate().scaleX(1f).scaleY(1f).setDuration(300).start()
            }.start()
    }

    /**
     * Geri sayım sayacını dairesel bar ile senkronize çalıştırır.
     */
    private fun initiateCountdownTimer() {
        timerRunnable = object : Runnable {
            override fun run() {
                if (timeLeft > 0 && !isWatchingPhase && !isGameOver) {
                    timeLeft -= 100
                    val progress = (timeLeft.toFloat() / MAX_TIME_MS * 100).toInt()

                    (binding.focusTimerProgress as? CircularProgressIndicator)?.setProgress(progress, true)

                    if (timeLeft < 5000) {
                        (binding.focusTimerProgress as? CircularProgressIndicator)?.setIndicatorColor(Color.RED)
                    }

                    binding.txtFocusTimer.text = (timeLeft / 1000).toString()
                    mainHandler.postDelayed(this, 100)
                } else if (timeLeft <= 0) {
                    processFailure("TEMPORAL DEPLETION")
                }
            }
        }
        mainHandler.post(timerRunnable!!)
    }

    /**
     * Kaybetme durumunda çalışır.
     */
    private fun processFailure(reason: String) {
        isGameOver = true
        killTimer()
        totalErrors++
        soundManager.play("game_over")
        triggerAdvancedHaptic(HAPTIC_STRENGTH_ERROR)

        saveFinalScoreToCloud()
        showGameOverDialog(reason)
    }

    /**
     * iOS 18 Stüdyo Modu Oyun Sonu Penceresi.
     */
    private fun showGameOverDialog(reason: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_focus_game_over, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // UI Bağlantıları
        val txtScore = dialogView.findViewById<TextView>(R.id.txtFinalScore)
        val txtLevel = dialogView.findViewById<TextView>(R.id.txtFinalLevel)
        val btnRetry = dialogView.findViewById<MaterialButton>(R.id.btnRetry)
        val btnExit = dialogView.findViewById<MaterialButton>(R.id.btnExit)

        dialogView.findViewById<TextView>(R.id.txtGameOverReason).text = reason.uppercase()
        txtLevel.text = "LEVEL $currentLevel"

        // Puan Sayma Animasyonu (Premium)
        val scoreAnimator = ValueAnimator.ofInt(0, totalScore)
        scoreAnimator.duration = 1800
        scoreAnimator.addUpdateListener { animator ->
            txtScore?.text = animator.animatedValue.toString()
        }

        btnRetry?.setOnClickListener {
            dialog.dismiss()
            val resetIntent = Intent(this, FocusMatrixActivity::class.java).apply {
                putExtra("LEVEL_ID", 1)
            }
            startActivity(resetIntent)
            finish()
        }

        btnExit?.setOnClickListener {
            dialog.dismiss()
            finish()
        }

        dialog.show()
        scoreAnimator.start()
    }

    /**
     * Skoru Firebase bulut veritabanına asenkron kaydeder.
     */
    private fun saveFinalScoreToCloud() {
        val databaseRef = FirebaseDatabase.getInstance().getReference("FocusLeaderboard")
        val record = mapOf(
            "name" to studentName,
            "score" to totalScore,
            "level" to currentLevel,
            "timestamp" to ServerValue.TIMESTAMP
        )
        databaseRef.child(userId).setValue(record)
            .addOnFailureListener { e -> Log.e(TAG, "Cloud Sync Failed: ${e.message}") }
    }

    /**
     * İstatistik panelindeki verileri günceller.
     */
    private fun updateStatsPanel() {
        binding.txtStreak.text = currentStreak.toString()
        binding.txtWrongCount.text = totalErrors.toString()
        binding.txtMainScore.text = totalScore.toString()
    }

    /**
     * Profesyonel titreşim geri bildirimi üretir.
     */
    private fun triggerAdvancedHaptic(duration: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }

    private fun killTimer() {
        timerRunnable?.let { mainHandler.removeCallbacks(it) }
    }

    private fun terminateEngine() {
        killTimer()
        finish()
    }

    override fun onDestroy() {
        killTimer()
        soundManager.release()
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}