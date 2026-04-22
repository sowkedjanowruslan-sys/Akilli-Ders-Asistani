package com.example.akillidersasistani

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnticipateOvershootInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.firebase.FirebaseApp
import java.util.Locale

/**
 * ========================================================================================
 * AKILLI DERS ASİSTANI - PLATINUM SPLASH ENGINE (v4.5)
 * ========================================================================================
 *
 * Bu sınıf, Apple'ın modern tasarım prensipleri temel alınarak geliştirilmiştir.
 * XML dosyasındaki (activity_splash.xml) katmanlı yapı ile tam uyumlu çalışır.
 *
 * DÜZELTİLEN REFERANSLAR:
 * - brandingWrapper (Eski: brandGroup)
 * - logoSurface (Eski: logoCard)
 * - ambientOrb1 (Eski: glowTop)
 * - ambientOrb2 (Eski: glowBottom)
 */
class SplashActivity : AppCompatActivity() {

    // --- GÖRSEL BİLEŞENLER (XML ID'LERİ İLE EŞLEŞTİRİLDİ) ---
    private var brandingWrapper: View? = null
    private var logoSurface: View? = null
    private var ambientOrb1: View? = null
    private var ambientOrb2: View? = null
    private var iosProgressBar: ProgressBar? = null
    private var txtAppName: TextView? = null

    // --- ANİMASYON MOTORU ---
    private val animationSet = AnimatorSet()
    private var pulseAnimator: ObjectAnimator? = null
    private val TAG = "PlatinumSplash"

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. SİSTEM DİL YAPILANDIRMASI
        injectGlobalLocale()

        super.onCreate(savedInstanceState)

        // 2. GÖRÜNÜMÜ YÜKLE
        setContentView(R.layout.activity_splash)

        // 3. TAM EKRAN MODU (Immersive UI)
        applyImmersiveEnvironment()

        // 4. ARKA PLAN SERVİSLERİ
        initializeBackendSystems()

        // 5. BİLEŞENLERİ BAĞLA (Referans hataları burada giderildi)
        try {
            initializeVisualElements()
        } catch (e: Exception) {
            Log.e(TAG, "ID Binding Error: ${e.message}")
        }

        // 6. PREMIUM ANİMASYON DİZİSİ
        startSupremeSequence()

        // 7. ANA EKRANA GEÇİŞ
        beginTransitionLifecycle()
    }

    private fun injectGlobalLocale() {
        val prefs = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val languageCode = prefs.getString("app_lang", "tr") ?: "tr"
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun applyImmersiveEnvironment() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller?.let {
            it.hide(WindowInsetsCompat.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun initializeBackendSystems() {
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Initialization Silent Error")
        }
    }

    /**
     * XML dosyasındaki yeni ID'leri Kotlin tarafındaki referanslara bağlar.
     */
    private fun initializeVisualElements() {
        // activity_splash.xml dosyasındaki ID isimleri ile birebir eşleştirme:
        brandingWrapper = findViewById(R.id.brandingWrapper)
        logoSurface = findViewById(R.id.logoSurface)
        ambientOrb1 = findViewById(R.id.ambientOrb1)
        ambientOrb2 = findViewById(R.id.ambientOrb2)
        txtAppName = findViewById(R.id.txtAppName)

        // ProgressBar doğrudan ID'ye sahip değilse progressContainer içinden alıyoruz
        val container = findViewById<View>(R.id.progressContainer)
        if (container != null) {
            // Container içindeki ProgressBar'ı bul (ID atanmamışsa tipine göre bulur)
            iosProgressBar = container.findViewById(ProgressBar::class.java.hashCode())
                ?: (container as? android.view.ViewGroup)?.getChildAt(0) as? ProgressBar
        }
    }

    private fun startSupremeSequence() {
        // Hazırlık: Başlangıç değerlerini ata
        brandingWrapper?.apply {
            alpha = 0f
            translationY = 80f
            scaleX = 0.9f
            scaleY = 0.9f
        }
        ambientOrb1?.alpha = 0f
        ambientOrb2?.alpha = 0f

        // 1. Arka Plan Işıkları (Soft Fade)
        val orb1Anim = ObjectAnimator.ofFloat(ambientOrb1, View.ALPHA, 0f, 0.4f).setDuration(2000)
        val orb2Anim = ObjectAnimator.ofFloat(ambientOrb2, View.ALPHA, 0f, 0.3f).setDuration(2000)

        // 2. Ana Grup Girişi (Slide-up & Scale)
        val brandAlpha = ObjectAnimator.ofFloat(brandingWrapper, View.ALPHA, 0f, 1f).setDuration(1500)
        val brandMove = ObjectAnimator.ofFloat(brandingWrapper, View.TRANSLATION_Y, 80f, 0f).setDuration(1600)
        brandMove.interpolator = AnticipateOvershootInterpolator(1.0f)

        // 3. Yükleme Çubuğu Dolumu
        val progressAnim = ObjectAnimator.ofInt(iosProgressBar, "progress", 0, 100).setDuration(2800)
        progressAnim.interpolator = DecelerateInterpolator()

        // Senkronize Başlat
        animationSet.playTogether(orb1Anim, orb2Anim, brandAlpha, brandMove, progressAnim)
        animationSet.start()

        // 4. Logo Sürekli Nabız (Pulse)
        startPulseEffect()
    }

    private fun startPulseEffect() {
        logoSurface?.let { card ->
            pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
                card,
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
    }

    private fun beginTransitionLifecycle() {
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
            finish()
        }, 3200)
    }

    override fun onDestroy() {
        super.onDestroy()
        animationSet.cancel()
        pulseAnimator?.cancel()
        brandingWrapper?.animate()?.cancel()
        logoSurface?.animate()?.cancel()
    }
}