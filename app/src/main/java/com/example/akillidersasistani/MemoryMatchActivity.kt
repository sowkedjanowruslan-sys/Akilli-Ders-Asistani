package com.example.akillidersasistani

import android.animation.*
import android.content.Context
import android.graphics.Color
import android.os.*
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.animation.*
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.akillidersasistani.databinding.ActivityMemoryMatchBinding
import com.google.android.material.card.MaterialCardView
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import java.util.*


class MemoryMatchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMemoryMatchBinding
    private lateinit var vibrator: Vibrator
    private val mainHandler = Handler(Looper.getMainLooper())
    private var soundManager: MathSoundManager? = null

    // Game Variables
    private var lives = 5
    private var score = 0
    private var pairsFound = 0
    private val totalPairs = 8
    private var isBusy = false
    private var isGameOver = false

    private var firstCard: MaterialCardView? = null
    private var firstPos: Int = -1

    private val icons = listOf(
        "https://cdn-icons-png.flaticon.com/512/2103/2103633.png",
        "https://cdn-icons-png.flaticon.com/512/2103/2103603.png",
        "https://cdn-icons-png.flaticon.com/512/2103/2103615.png",
        "https://cdn-icons-png.flaticon.com/512/2103/2103630.png",
        "https://cdn-icons-png.flaticon.com/512/2103/2103622.png",
        "https://cdn-icons-png.flaticon.com/512/2103/2103612.png",
        "https://cdn-icons-png.flaticon.com/512/2103/2103628.png",
        "https://cdn-icons-png.flaticon.com/512/2103/2103639.png"
    )

    private val activeBoard = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMemoryMatchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupCoreSystems()
        runEntranceSequence()
    }

    /**
     * CORE INITIALIZATION
     */
    private fun setupCoreSystems() {
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        soundManager = MathSoundManager(this)

        binding.btnStartGame.setOnClickListener {
            executeStudioHaptic()
            it.animate().alpha(0f).translationY(50f).setDuration(400).start()
            initiateNeuralGame()
        }

        binding.btnBackFromMemory.setOnClickListener {
            executeStudioHaptic()
            finish()
        }
    }

    /**
     * ANIMATION: STUDIO ENTRANCE
     */
    private fun runEntranceSequence() {
        binding.arenaContainer.alpha = 0f
        binding.arenaContainer.scaleX = 0.95f
        binding.arenaContainer.animate()
            .alpha(1f).scaleX(1f).setDuration(800)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    /**
     * GAME ENGINE: INITIATE SESSION
     */
    private fun initiateNeuralGame() {
        isBusy = true
        lives = 5
        score = 0
        pairsFound = 0
        isGameOver = false

        binding.txtLives.text = lives.toString()
        binding.txtScore.text = "0"
        binding.txtStudioStatus.text = "SYNCING..."
        binding.topSmallLoader.visibility = View.VISIBLE

        prepareBoardData()
    }

    private fun prepareBoardData() {
        activeBoard.clear()
        val selection = icons.shuffled().take(totalPairs)
        activeBoard.addAll(selection)
        activeBoard.addAll(selection)
        activeBoard.shuffle()

        renderCompactGrid()
    }

    /**
     * UX: COMPACT GRID RENDERING
     * Calculates size to fit small containers.
     */
    private fun renderCompactGrid() {
        binding.memoryGrid.removeAllViews()

        val metrics = resources.displayMetrics
        val availableSize = (metrics.widthPixels - 200) / 4

        for (i in 0 until activeBoard.size) {
            val card = LayoutInflater.from(this).inflate(R.layout.item_memory_tile, binding.memoryGrid, false) as MaterialCardView
            val lp = GridLayout.LayoutParams()
            lp.width = availableSize - 16
            lp.height = availableSize - 16
            lp.setMargins(8, 8, 8, 8)
            card.layoutParams = lp

            val img = card.findViewById<ImageView>(R.id.imgTile)
            Glide.with(this).load(activeBoard[i]).into(img)

            card.setOnClickListener { onTileClicked(card, i) }
            binding.memoryGrid.addView(card)
        }

        mainHandler.postDelayed({
            hideTilesWithEffect()
            binding.topSmallLoader.visibility = View.GONE
            binding.txtStudioStatus.text = "ACTIVE"
            isBusy = false
        }, 3000)
    }

    private fun hideTilesWithEffect() {
        for (i in 0 until binding.memoryGrid.childCount) {
            val card = binding.memoryGrid.getChildAt(i) as MaterialCardView
            performNeuralFlip(card, false, null)
        }
    }

    /**
     * INPUT: INTERACTION LOGIC
     */
    private fun onTileClicked(card: MaterialCardView, pos: Int) {
        if (isBusy || isGameOver || card == firstCard) return

        performNeuralFlip(card, true, activeBoard[pos])

        if (firstCard == null) {
            firstCard = card
            firstPos = pos
        } else {
            isBusy = true
            processMatch(card, pos)
        }
    }

    private fun processMatch(secondCard: MaterialCardView, secondPos: Int) {
        if (activeBoard[firstPos] == activeBoard[secondPos]) {
            pairsFound++
            score += 150 + (lives * 10)
            binding.txtScore.text = score.toString()
            binding.txtStatus.text = "NEURAL MATCH FOUND"

            executeStudioHaptic()
            firstCard = null
            isBusy = false

            if (pairsFound == totalPairs) concludeGame(true)
        } else {
            lives--
            binding.txtLives.text = lives.toString()
            binding.txtStatus.text = "SYNC FAILED"

            mainHandler.postDelayed({
                performNeuralFlip(firstCard!!, false, null)
                performNeuralFlip(secondCard, false, null)
                firstCard = null
                isBusy = false
                if (lives <= 0) concludeGame(false)
            }, 1000)
        }
    }

    /**
     * ANIMATION: NEURAL FLIP
     */
    private fun performNeuralFlip(card: MaterialCardView, show: Boolean, url: String?) {
        val anim1 = ObjectAnimator.ofFloat(card, "rotationY", 0f, 90f).setDuration(200)
        val anim2 = ObjectAnimator.ofFloat(card, "rotationY", -90f, 0f).setDuration(200)

        anim1.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                val img = card.findViewById<ImageView>(R.id.imgTile)
                if (show && url != null) {
                    Glide.with(this@MemoryMatchActivity).load(url).into(img)
                    card.setCardBackgroundColor(Color.WHITE)
                } else {
                    img.setImageResource(R.drawable.bg_ios_pro_dock)
                    card.setCardBackgroundColor(Color.parseColor("#1C1C1E"))
                }
                anim2.start()
            }
        })
        anim1.start()
    }

    /**
     * END: SESSION SUMMARY
     */
    private fun concludeGame(win: Boolean) {
        isGameOver = true
        if (win) uploadToCloud()

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
            .setTitle(if (win) "SYNC COMPLETE" else "TERMINATED")
            .setMessage(if (win) "Neural links stable. Score: $score" else "Memory override failed.")
            .setPositiveButton("RETRY") { _, _ -> recreate() }
            .setCancelable(false).show()
    }

    private fun uploadToCloud() {
        val prefs = getSharedPreferences("UserProfile", Context.MODE_PRIVATE)
        val id = prefs.getString("ogrenci_no", "guest") ?: "guest"
        FirebaseDatabase.getInstance().getReference("MemoryPro")
            .child(id).push().setValue(mapOf("s" to score, "t" to ServerValue.TIMESTAMP))
    }

    private fun executeStudioHaptic() {
        binding.root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}