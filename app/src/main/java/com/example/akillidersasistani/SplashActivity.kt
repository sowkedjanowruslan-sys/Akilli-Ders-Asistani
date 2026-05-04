package com.example.akillidersasistani

import android.animation.*
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.*
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.akillidersasistani.databinding.ActivitySplashBinding
import com.google.firebase.FirebaseApp
import java.util.Locale

class SplashActivity : AppCompatActivity() {

    // 1. ViewBinding tanımı
    private lateinit var binding: ActivitySplashBinding
    private val animationSet = AnimatorSet()
    private var pulseAnimator: ObjectAnimator? = null
    private val TAG = "PlatinumSplash"

    override fun onCreate(savedInstanceState: Bundle?) {
        // Sistem dilini ayarla
        injectGlobalLocale()

        super.onCreate(savedInstanceState)

        // 2. KRİTİK DÜZELTME: Önce binding'i oluştur (inflate)
        binding = ActivitySplashBinding.inflate(layoutInflater)

        // 3. KRİTİK DÜZELTME: R.layout.activity_splash yerine binding.root kullan
        setContentView(binding.root)

        // UI ayarları
        applyImmersiveEnvironment()
        initializeBackendSystems()

        // 4. Animasyonları güvenli bir şekilde başlat
        // View'lar tam çizilmeden genişlik (width) 0 gelebilir, bu yüzden post kullanıyoruz
        binding.root.post {
            startNeutralAnimations()
            startSupremeSequence()
        }

        // Ana ekrana geçiş süreci
        beginTransitionLifecycle()
    }

    private fun startNeutralAnimations() {
        // Progress Bar Animasyonu
        val progressAnimator = ValueAnimator.ofInt(0, 100)
        progressAnimator.duration = 3000
        progressAnimator.interpolator = DecelerateInterpolator()

        progressAnimator.addUpdateListener { animation ->
            val progress = animation.animatedValue as Int

            // Binding artık initialize edildiği için güvenle kullanabiliriz
            val params = binding.splashProgressBar.layoutParams as FrameLayout.LayoutParams
            val wrapperWidth = binding.progressWrapper.width

            if (wrapperWidth > 0) {
                params.width = (wrapperWidth * progress) / 100
                binding.splashProgressBar.layoutParams = params
            }
        }
        progressAnimator.start()

        // Yazı Nefes Alma (Status)
        val breathing = AnimationUtils.loadAnimation(this, R.anim.status_breathing)
        binding.txtStatus.startAnimation(breathing)

        // Panel Giriş Animasyonu
        val entrance = AnimationUtils.loadAnimation(this, R.anim.progress_entrance)
        binding.progressArea.startAnimation(entrance)
    }

    private fun startSupremeSequence() {
        // Hazırlık
        binding.brandingWrapper.alpha = 0f
        binding.brandingWrapper.translationY = 80f
        binding.ambientOrb1.alpha = 0f
        binding.ambientOrb2.alpha = 0f

        // Animasyonlar
        val orb1Anim = ObjectAnimator.ofFloat(binding.ambientOrb1, View.ALPHA, 0f, 0.4f).setDuration(2000)
        val orb2Anim = ObjectAnimator.ofFloat(binding.ambientOrb2, View.ALPHA, 0f, 0.3f).setDuration(2000)
        val brandAlpha = ObjectAnimator.ofFloat(binding.brandingWrapper, View.ALPHA, 0f, 1f).setDuration(1500)
        val brandMove = ObjectAnimator.ofFloat(binding.brandingWrapper, View.TRANSLATION_Y, 80f, 0f).apply {
            duration = 1600
            interpolator = AnticipateOvershootInterpolator(1.0f)
        }

        animationSet.playTogether(orb1Anim, orb2Anim, brandAlpha, brandMove)
        animationSet.start()

        startPulseEffect()
    }

    private fun startPulseEffect() {
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            binding.logoSurface,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.05f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.05f)
        ).apply {
            duration = 2500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun beginTransitionLifecycle() {
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 4000)
    }

    private fun injectGlobalLocale() {
        val prefs = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val languageCode = prefs.getString("app_lang", "tr") ?: "tr"
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun applyImmersiveEnvironment() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun initializeBackendSystems() {
        try { FirebaseApp.initializeApp(this) } catch (e: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        animationSet.cancel()
        pulseAnimator?.cancel()
    }
}