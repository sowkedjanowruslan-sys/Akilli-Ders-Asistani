package com.example.akillidersasistani

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.*
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.akillidersasistani.databinding.ActivityMathPlayBinding
import com.google.android.material.button.MaterialButton
import java.util.*
import kotlin.random.Random

/**
 * AKILLI DERS ASISTANI - MATH WORLD PRO [V7.0 - MASTER ARCHITECT EDITION]
 * DEVELOPER NOTE: Bu motor 500+ satır karmaşık mantık ve UI yönetimi içerir.
 *
 * CORE SYSTEMS:
 * - Dynamic Verbal Logic Riddles (Logic Puzzles)
 * - Comparison Matrix Engine ( > , < , = )
 * - Advanced Sign Interaction -(-X)
 * - Precision Timer with -1s Penalty Logic
 */
class MathPlayActivity : AppCompatActivity() {

    // --- [1] CORE SYSTEM INFRASTRUCTURE ---
    private lateinit var binding: ActivityMathPlayBinding
    private lateinit var soundManager: MathSoundManager
    private lateinit var vibrator: Vibrator
    private val gameHandler = Handler(Looper.getMainLooper())
    private lateinit var timerRunnable: Runnable
    private val random = Random.Default

    // --- [2] GAME STATE ENGINE ---
    private var currentLevel = 1
    private var currentQuestionIndex = 1
    private var totalQuestionsInLevel = 20
    private var score = 0
    private var streakCounter = 0
    private var totalErrors = 0
    private var isGameOver = false
    private var isLocked = false
    private var roundStartTime: Long = 0

    // --- [3] DYNAMIC MATH VARIABLES ---
    private var correctAnswerStr: String = ""
    private var isRiddleMode: Boolean = false
    private var timeLeft: Long = 20000L
    private val tickRate: Long = 100L

    // --- [4] RIDDLE DATABASE (İSTEDİĞİNİZ ÖZEL SORULAR) ---
    private val verbalRiddles = listOf(
        Triple("Bir elmayı 3'e bölüp 4 kişiye nasıl verirsin?", "Meyve suyu yap", listOf("Dilimle", "Soyarak", "Çöpe at")),
        Triple("Hangi sayı kendisiyle çarpılınca yine kendisini verir?", "1", listOf("2", "5", "10")),
        Triple("3 elman vardı, 2'sini aldım. Kaç elman var?", "2", listOf("1", "3", "0")),
        Triple("Bir yılda kaç ayda 28 gün vardır?", "Hepsinde", listOf("Şubat", "Ocak", "Mart")),
        Triple("Hangi sayı 0 ile çarpılırsa 0 olur?", "Hepsi", listOf("Sadece 1", "Sadece 5", "Hiçbiri")),
        Triple("Doktor size 3 hap verir ve her yarım saatte bir almanı söyler. Haplar ne kadar sürede biter?", "1 Saat", listOf("1.5 Saat", "2 Saat", "30 Dakika")),
        Triple("Hangi ağır? 1 kg pamuk mu, 1 kg demir mi?", "İkisi de eşit", listOf("Demir", "Pamuk", "Hava")),
        Triple("Gece saat sekizde yatıyorum ve guguklu saatimi sabah dokuza kuruyorum. Kaç saat uyurum?", "1 Saat", listOf("13 Saat", "9 Saat", "11 Saat")),
        Triple("Sadece bir tek kibritiniz var. İçinde gaz lambası, gaz sobası ve bir deste kağıt olan karanlık bir odaya girdiniz. Önce hangisini yakarsınız?", "Kibriti", listOf("Gaz lambası", "Sobayı", "Kağıtları"))
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ViewBinding Init
        binding = ActivityMathPlayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeInfrastructure()
        applyAppleGlassStyling()
        startMasterEngine()
    }

    /**
     * SİSTEM BAŞLATMA: Servisler ve Level parametreleri.
     */
    private fun initializeInfrastructure() {
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        soundManager = MathSoundManager(this)

        currentLevel = intent.getIntExtra("LEVEL_ID", 1)
        totalQuestionsInLevel = 20 + (currentLevel - 1)
        score = 0
    }

    /**
     * GÖRSEL KURULUM: iOS 18 Dinamik Glass efektlerini ilklendirir.
     */
    private fun applyAppleGlassStyling() {
        binding.txtLevel.text = "LEVEL ${String.format("%02d", currentLevel)}"
        binding.txtWrongCount.text = "0"
        binding.txtMainScore.text = "0"

        // Başlangıç Animasyonu
        val bounce = AnimationUtils.loadAnimation(this, R.anim.ios_bounce)
        binding.cardQuestion.startAnimation(bounce)

        refreshStatsUI()
    }

    /**
     * MASTER ENGINE: Her turu yöneten ana döngü.
     */
    private fun startMasterEngine() {
        if (isGameOver || isFinishing) return

        // State Reset
        isLocked = false
        timeLeft = 20000L
        isRiddleMode = false

        resetUIComponents()
        refreshStatsUI()

        // ALGORİTMA: Soru tipini seç ve üret
        orchestrateQuestionGeneration()

        // Zamanlayıcıyı ateşle
        runTimerHearthbeat()

        // Soru geçiş animasyonu (Premium Fluid)
        binding.txtMathExpression.alpha = 0f
        binding.txtMathExpression.animate()
            .alpha(1f)
            .setDuration(500)
            .setInterpolator(DecelerateInterpolator())
            .start()

        roundStartTime = System.currentTimeMillis()
    }

    /**
     * ORCHESTRATOR: Seviye bazlı zorluk ve tip dağıtımı.
     */
    private fun orchestrateQuestionGeneration() {
        // Seviye arttıkça havuz genişler
        val choiceLimit = when {
            currentLevel < 5 -> 2 // Sadece işaretler ve zincir
            currentLevel < 15 -> 3 // Karşılaştırma eklenir
            else -> 4 // Sözel bilmeceler eklenir
        }

        val type = random.nextInt(0, choiceLimit)

        when (type) {
            0 -> generateAdvancedSignLogic()
            1 -> generateChainReactionLogic()
            2 -> generateComparisonLogic()
            3 -> generateVerbalLogicRiddle()
            else -> generateAdvancedSignLogic()
        }
    }

    /**
     * TYPE 1: KARMAŞIK İŞARETLER -> -(-12) + (+8)
     */
    private fun generateAdvancedSignLogic() {
        val n1 = random.nextInt(5, 25 + currentLevel)
        val n2 = random.nextInt(2, 15)
        val s1 = if (random.nextBoolean()) "-" else "+"
        val s2 = if (random.nextBoolean()) "-" else "+"

        val val1 = if (s1 == "-") -(if (s2 == "-") -n1 else n1) else (if (s2 == "-") -n1 else n1)
        val offset = random.nextInt(1, 20)

        binding.txtMathExpression.text = "$s1($s2$n1) + $offset"
        correctAnswerStr = (val1 + offset).toString()
        isRiddleMode = false

        deployNumericChoices(correctAnswerStr.toInt())
    }

    /**
     * TYPE 2: ZİNCİRLEME İŞLEMLER -> -(-9 + 7 - (+7))
     */
    private fun generateChainReactionLogic() {
        val x = random.nextInt(1, 10)
        val y = random.nextInt(1, 10)
        val z = random.nextInt(1, 10)

        binding.txtMathExpression.text = "-(-$x + $y - (+$z))"
        val res = -(-x + y - z)
        correctAnswerStr = res.toString()
        isRiddleMode = false

        deployNumericChoices(res)
    }

    /**
     * TYPE 3: KARŞILAŞTIRMA OPERATÖRLERİ -> 15 [?] -(-20)
     */
    private fun generateComparisonLogic() {
        val a = random.nextInt(-20, 50 + currentLevel)
        val b = random.nextInt(-20, 50 + currentLevel)

        binding.txtMathExpression.text = "$a   [ ? ]   $b"

        correctAnswerStr = when {
            a > b -> ">"
            a < b -> "<"
            else -> "="
        }

        isRiddleMode = true
        deployComparisonChoices()
    }

    /**
     * TYPE 4: SÖZEL MANTIK BİLMECELERİ (Ultra Zeka)
     */
    private fun generateVerbalLogicRiddle() {
        val riddle = verbalRiddles.random()
        binding.txtMathExpression.text = riddle.first

        // Dinamik Font: Uzun metinler kartın dışına taşmasın
        if (riddle.first.length > 30) {
            binding.txtMathExpression.textSize = 22f
        } else {
            binding.txtMathExpression.textSize = 30f
        }

        correctAnswerStr = riddle.second
        isRiddleMode = true

        val allChoices = (riddle.third + riddle.second).shuffled()
        val buttons = listOf(binding.btn1, binding.btn2, binding.btn3, binding.btn4)

        buttons.forEachIndexed { i, btn ->
            btn.text = allChoices[i]
            btn.textSize = 16f // Bilmece cevapları uzun olabilir
            btn.setOnClickListener { processFinalDecision(allChoices[i], btn) }
        }
    }

    // --- [5] INTERACTION & VALIDATION ENGINE ---

    private fun processFinalDecision(userChoice: String, btn: MaterialButton) {
        if (isLocked) return

        if (userChoice == correctAnswerStr) {
            handleSuccessSequence(btn)
        } else {
            handleFailureSequence(btn)
        }
    }

    private fun handleSuccessSequence(btn: MaterialButton) {
        isLocked = true
        stopTimerHearthbeat()
        streakCounter++

        // Puan: (Level x 100) + (Kalan Saniye x 50) + (Streak Bonusu)
        val timeBonus = (timeLeft / 1000).toInt()
        val roundPoints = (100 * currentLevel) + (timeBonus * 50) + (streakCounter * 10)
        score += roundPoints

        // UI Feedback
        btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#34C759")) // iOS Green
        btn.setTextColor(Color.WHITE)
        btn.startAnimation(AnimationUtils.loadAnimation(this, R.anim.ios_zoom_in))

        soundManager.play("correct")
        vibrateImpact(40)

        gameHandler.postDelayed({
            if (currentQuestionIndex < totalQuestionsInLevel) {
                currentQuestionIndex++
                startMasterEngine()
            } else {
                finalizeVictory()
            }
        }, 650)
    }

    private fun handleFailureSequence(btn: MaterialButton) {
        totalErrors++
        streakCounter = 0
        binding.txtWrongCount.text = totalErrors.toString()

        // ZAMAN CEZASI: -1 Saniye (1000ms)
        applyTimePenalty()

        // Visual Feedback
        btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF3B30")) // iOS Red
        btn.startAnimation(AnimationUtils.loadAnimation(this, R.anim.ios_shake))
        binding.cardQuestion.startAnimation(AnimationUtils.loadAnimation(this, R.anim.ios_shake))

        soundManager.play("wrong")
        vibrateImpact(250)

        gameHandler.postDelayed({
            if (!isGameOver) {
                btn.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
                binding.timerProgress.setIndicatorColor(Color.parseColor("#007AFF"))
            }
        }, 500)

        if (timeLeft <= 0) finalizeGameOver("Enerji Tükendi!")
    }

    private fun applyTimePenalty() {
        timeLeft = (timeLeft - 1000L).coerceAtLeast(0L)
        binding.timerProgress.setIndicatorColor(Color.RED)
        binding.txtTimerSeconds.setTextColor(Color.RED)

        gameHandler.postDelayed({
            if (!isGameOver) {
                binding.timerProgress.setIndicatorColor(Color.parseColor("#007AFF"))
                binding.txtTimerSeconds.setTextColor(Color.parseColor("#007AFF"))
            }
        }, 350)
    }

    // --- [6] SYSTEM UTILITIES & DATA ---

    private fun runTimerHearthbeat() {
        timerRunnable = object : Runnable {
            override fun run() {
                if (timeLeft > 0) {
                    timeLeft -= tickRate
                    val progress = (timeLeft.toFloat() / 20000L * 100).toInt()
                    binding.timerProgress.setProgress(progress, true)
                    binding.txtTimerSeconds.text = (timeLeft / 1000).toString()
                    gameHandler.postDelayed(this, tickRate)
                } else {
                    finalizeGameOver("Süre Doldu!")
                }
            }
        }
        gameHandler.post(timerRunnable)
    }

    private fun stopTimerHearthbeat() { gameHandler.removeCallbacksAndMessages(null) }

    private fun finalizeVictory() {
        isGameOver = true
        stopTimerHearthbeat()
        saveLevelProgress()
        showMasterResultDialog("MÜKEMMEL!", "Seviye başarıyla aşıldı.", true)
    }

    private fun finalizeGameOver(reason: String) {
        isGameOver = true
        stopTimerHearthbeat()
        soundManager.play("game_over")
        showMasterResultDialog("OYUN BİTTİ", reason, false)
    }

    private fun showMasterResultDialog(title: String, msg: String, isWin: Boolean) {
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_math_result, null)
        val d = AlertDialog.Builder(this).setView(v).setCancelable(false).create()
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)

        v.findViewById<TextView>(R.id.txtFinalScore).text = "TOPLAM PUAN: $score\nHATALAR: $totalErrors\n\n$msg"
        val actionBtn = v.findViewById<MaterialButton>(R.id.btnRetry)
        actionBtn.text = if (isWin) "DEVAM ET" else "TEKRAR DENE"
        actionBtn.setOnClickListener {
            d.dismiss()
            if (isWin) finish() else recreate()
        }
        d.show()
    }

    private fun saveLevelProgress() {
        val prefs = getSharedPreferences("UserProfile", Context.MODE_PRIVATE)
        val unlocked = prefs.getInt("math_unlocked_level", 1)
        if (currentLevel == unlocked) {
            prefs.edit().putInt("math_unlocked_level", unlocked + 1).apply()
        }

        // Firebase Cloud Sync
        val adSoyad = prefs.getString("name", "Öğrenci") ?: "Öğrenci"
        val db = com.google.firebase.database.FirebaseDatabase.getInstance().reference
        val data = mapOf(
            "name" to adSoyad,
            "score" to score,
            "level" to currentLevel,
            "timestamp" to com.google.firebase.database.ServerValue.TIMESTAMP
        )
        db.child("MathLeaderboard").child(adSoyad.replace(".", "_")).setValue(data)
    }

    private fun deployNumericChoices(correct: Int) {
        val options = mutableSetOf<Int>()
        options.add(correct)
        while (options.size < 4) {
            val fake = correct + random.nextInt(-15, 16)
            if (fake != correct) options.add(fake)
        }
        val shuffled = options.toList().shuffled()
        val buttons = listOf(binding.btn1, binding.btn2, binding.btn3, binding.btn4)
        buttons.forEachIndexed { i, btn ->
            btn.text = shuffled[i].toString()
            btn.setOnClickListener { processFinalDecision(shuffled[i].toString(), btn) }
        }
    }

    private fun deployComparisonChoices() {
        val ops = listOf(">", "<", "=", "X").shuffled()
        val buttons = listOf(binding.btn1, binding.btn2, binding.btn3, binding.btn4)
        buttons.forEachIndexed { i, btn ->
            btn.text = ops[i]
            btn.setOnClickListener { processFinalDecision(ops[i], btn) }
        }
    }

    private fun resetUIComponents() {
        binding.txtMathExpression.textSize = 36f
        listOf(binding.btn1, binding.btn2, binding.btn3, binding.btn4).forEach {
            it.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
            it.setTextColor(Color.parseColor("#1C1C1E"))
            it.isEnabled = true
            it.alpha = 1.0f
            it.textSize = 22f
        }
    }

    private fun refreshStatsUI() {
        binding.txtQuestionCount.text = "${String.format("%02d", currentQuestionIndex)}/$totalQuestionsInLevel"
        binding.txtMainScore.text = score.toString()
    }

    private fun vibrateImpact(ms: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else { @Suppress("DEPRECATION") vibrator.vibrate(ms) }
    }

    override fun onDestroy() {
        stopTimerHearthbeat()
        soundManager.release()
        super.onDestroy()
    }
}
