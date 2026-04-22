package com.example.akillidersasistani

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.*
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.GridLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.akillidersasistani.databinding.ActivityFocusPlayBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import java.util.*
import kotlin.random.Random

/**
 * AKILLI DERS ASISTANI - FOCUS MATRIX PRO [V2.0 MASTER ENGINE]
 * ÇÖZÜLEN HATALAR: AnnotatedString Silindi, ID'ler Senkronize Edildi.
 */
class FocusMatrixActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFocusPlayBinding
    private lateinit var soundManager: MathSoundManager
    private lateinit var vibrator: Vibrator
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var timerRunnable: Runnable
    private val randomGenerator = Random.Default

    private var currentLevel = 1
    private var gridSize = 3
    private var sequenceLength = 3
    private val gameSequence = mutableListOf<Int>()
    private val playerInputSequence = mutableListOf<Int>()

    private var totalScore = 0
    private var currentStreak = 0
    private var totalErrors = 0
    private var isGameOver = false
    private var isWatchingPhase = true
    private var timeLeft: Long = 20000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFocusPlayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupHardware()
        setupUI()
        buildGrid()

        mainHandler.postDelayed({ startRound() }, 1500)
    }

    private fun setupHardware() {
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        soundManager = MathSoundManager(this)
        currentLevel = intent.getIntExtra("LEVEL_ID", 1)
        gridSize = if (currentLevel <= 10) 3 else if (currentLevel <= 25) 4 else 5
        sequenceLength = 3 + (currentLevel / 10)
    }

    private fun setupUI() {
        binding.txtFocusLevel.text = "LEVEL ${String.format("%02d", currentLevel)}"
        binding.txtTileCount.text = "${gridSize}x${gridSize} Matrix"
        binding.txtStreak.text = "0"
        binding.txtWrongCount.text = "0"
        binding.txtMainScore.text = "0"
        binding.matrixCard.startAnimation(AnimationUtils.loadAnimation(this, R.anim.ios_bounce))
    }

    private fun buildGrid() {
        binding.matrixGrid.removeAllViews()
        binding.matrixGrid.columnCount = gridSize
        binding.matrixGrid.rowCount = gridSize

        val metrics = resources.displayMetrics
        val tileSize = (metrics.widthPixels - 200) / gridSize

        for (i in 0 until (gridSize * gridSize)) {
            val tile = LayoutInflater.from(this).inflate(R.layout.item_focus_tile, binding.matrixGrid, false) as MaterialCardView
            val lp = GridLayout.LayoutParams()
            lp.width = tileSize; lp.height = tileSize
            lp.setMargins(10, 10, 10, 10)
            tile.layoutParams = lp
            tile.tag = i
            tile.setOnClickListener { if (!isWatchingPhase && !isGameOver) handleInput(i, tile) }
            binding.matrixGrid.addView(tile)
        }
    }

    private fun startRound() {
        if (isGameOver) return
        isWatchingPhase = true
        playerInputSequence.clear()
        gameSequence.clear()
        binding.txtFocusInstruction.text = "WATCH CAREFULLY"
        repeat(sequenceLength) { gameSequence.add(randomGenerator.nextInt(0, gridSize * gridSize)) }
        playSequence()
    }

    private fun playSequence() {
        var delay = 800L
        gameSequence.forEachIndexed { idx, tag ->
            mainHandler.postDelayed({
                val tile = binding.matrixGrid.findViewWithTag<MaterialCardView>(tag)
                flashTile(tile)
                if (idx == gameSequence.size - 1) {
                    mainHandler.postDelayed({
                        isWatchingPhase = false
                        binding.txtFocusInstruction.text = "YOUR TURN"
                        timeLeft = 20000L
                        runTimer()
                    }, 1000)
                }
            }, delay)
            delay += 600
        }
    }

    private fun flashTile(tile: MaterialCardView?) {
        tile?.let {
            val oldColor = it.cardBackgroundColor
            it.setCardBackgroundColor(ColorStateList.valueOf(Color.parseColor("#5856D6")))
            binding.matrixGrid.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            mainHandler.postDelayed({ it.setCardBackgroundColor(oldColor) }, 400)
        }
    }

    private fun handleInput(tag: Int, tile: MaterialCardView) {
        playerInputSequence.add(tag)
        val step = playerInputSequence.size - 1
        if (playerInputSequence[step] == gameSequence[step]) {
            tile.setCardBackgroundColor(ColorStateList.valueOf(Color.parseColor("#34C759")))
            binding.matrixGrid.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            mainHandler.postDelayed({ tile.setCardBackgroundColor(Color.parseColor("#1AFFFFFF")) }, 250)
            if (playerInputSequence.size == gameSequence.size) winRound()
        } else {
            totalErrors++; binding.txtWrongCount.text = totalErrors.toString()
            timeLeft -= 2000; soundManager.play("wrong")
            tile.startAnimation(AnimationUtils.loadAnimation(this, R.anim.ios_shake))
            playerInputSequence.clear(); binding.txtFocusInstruction.text = "TRY AGAIN!"
        }
    }

    private fun winRound() {
        stopTimer(); currentStreak++; binding.txtStreak.text = currentStreak.toString()
        soundManager.play("correct")
        totalScore += (500 * currentLevel) + (timeLeft / 100).toInt()
        binding.txtMainScore.text = totalScore.toString()
        mainHandler.postDelayed({ saveAndNext() }, 1000)
    }

    private fun runTimer() {
        timerRunnable = object : Runnable {
            override fun run() {
                if (timeLeft > 0) {
                    timeLeft -= 100
                    binding.focusTimerProgress.setProgress((timeLeft.toFloat() / 20000 * 100).toInt(), true)
                    binding.txtFocusTimer.text = (timeLeft / 1000).toString()
                    mainHandler.postDelayed(this, 100)
                } else { die("Time Out!") }
            }
        }
        mainHandler.post(timerRunnable)
    }

    private fun stopTimer() { if (::timerRunnable.isInitialized) mainHandler.removeCallbacks(timerRunnable) }

    private fun die(reason: String) {
        isGameOver = true; stopTimer(); soundManager.play("game_over")
        AlertDialog.Builder(this).setTitle("Game Over").setMessage(reason)
            .setPositiveButton("Retry") { _, _ -> recreate() }.setCancelable(false).show()
    }

    private fun saveAndNext() {
        val prefs = getSharedPreferences("UserProfile", Context.MODE_PRIVATE)
        val u = prefs.getInt("focus_unlocked_level", 1)
        if (currentLevel == u) prefs.edit().putInt("focus_unlocked_level", u + 1).apply()
        FirebaseDatabase.getInstance().reference.child("FocusLeaderboard")
            .child(prefs.getString("name", "Student")!!.replace(".", "_"))
            .setValue(mapOf("score" to totalScore, "level" to currentLevel))

        AlertDialog.Builder(this).setTitle("Perfect!").setMessage("Level $currentLevel Cleared")
            .setPositiveButton("Next") { _, _ ->
                val intent = Intent(this, FocusMatrixActivity::class.java)
                intent.putExtra("LEVEL_ID", currentLevel + 1)
                startActivity(intent); finish()
            }.setCancelable(false).show()
    }

    override fun onDestroy() { stopTimer(); soundManager.release(); super.onDestroy() }
}