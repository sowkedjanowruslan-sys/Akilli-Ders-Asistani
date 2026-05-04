package com.example.akillidersasistani

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

/**
 * =========================================================================================
 * PROJECT: AKILLI DERS ASISTANI - AI VOICE STUDIO PRO
 * COMPONENT: HIGH-PERFORMANCE AUDIO VISUALIZER (RETINA EDITION)
 * DESIGN: APPLE iOS 18 STUDIO WAVEFORM
 *
 * VERSION: 4.2 [PROFESSIONAL ARCHITECT]
 * Satır Sayısı: 230+
 *
 * DESCRIPTION:
 * Bu özel görünüm (Custom View), mikrofondan gelen anlık RMS (ses şiddeti) verilerini
 * kullanarak gerçek zamanlı, simetrik ve gradyan geçişli ses dalgaları çizer.
 * =========================================================================================
 */
class AudioVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // --- [1] TASARIM PARAMETRELERİ (DESIGN TOKENS) ---
    private var spikeColor = Color.parseColor("#FF9500") // Apple Turuncusu
    private var spikeWidth = 10f // Çubuk kalınlığı
    private var spikeSpacing = 8f // Çubuklar arası boşluk
    private var cornerRadius = 20f // Çubukların yuvarlaklığı
    private var maxSpikes = 65 // Ekrana sığacak toplam dalga sayısı

    // --- [2] ÇİZİM ARAÇLARI (GRAPHICS TOOLS) ---
    private val spikePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        strokeCap = Paint.Cap.ROUND
    }

    private val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        alpha = 30 // Buzlu cam üzerindeki ince çizgi efekti
        strokeWidth = 2f
    }

    // --- [3] VERİ YÖNETİMİ (DATA MANAGEMENT) ---
    // Ses genliklerini tutan liste (Queue mantığıyla çalışır)
    private val amplitudes = mutableListOf<Float>()

    // Gradyan efekti için (Daha profesyonel görünüm sağlar)
    private var gradient: LinearGradient? = null

    init {
        // XML'den gelen özel öznitelikler varsa burada okunabilir
        // Şimdilik varsayılan profesyonel ayarlar kullanılıyor.

        // Donanımsal hızlandırmayı zorla (Daha akışkan animasyon için)
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    /**
     * SES VERİSİ ENJEKSİYONU:
     * Mikrofondan gelen RMS değerini dalga boyuna çevirir.
     * @param amplitude Gelen ses şiddeti (Genelde -2 ile 12 arası)
     */
    fun addAmplitude(amplitude: Float) {
        // 1. Gelen ham veriyi normalize et (Matematiksel Ölçekleme)
        // Negatif değerleri temizle ve çarpan ile yükselt
        var normalizedHeight = (amplitude + 2f) * 15f

        // 2. Minimum ve Maksimum sınırları belirle (Taşmaları engelle)
        normalizedHeight = max(15f, normalizedHeight) // Boştayken bile hafif hareket

        if (normalizedHeight > height * 0.8f) {
            normalizedHeight = height * 0.8f // Kartın dışına çıkma
        }

        // 3. Veriyi listeye ekle
        amplitudes.add(normalizedHeight)

        // 4. Liste dolduysa en eskiyi sil (Kaydırma Efekti)
        if (amplitudes.size > maxSpikes) {
            amplitudes.removeAt(0)
        }

        // 5. Ekranı yenile (onDraw tetiklenir)
        postInvalidateOnAnimation()
    }

    /**
     * CLEAR ENGINE: Kayıt bittiğinde veya iptal edildiğinde temizle.
     */
    fun clearVisualizer() {
        amplitudes.clear()
        invalidate()
    }

    /**
     * ÖLÇÜLENDİRME: Gradyan renklerini ekran boyuna göre ayarlar.
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // Apple Stili Gradyan: Üstten alta Turuncu -> Beyaz -> Turuncu geçişi
        gradient = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(
                Color.parseColor("#FF9500"),
                Color.WHITE,
                Color.parseColor("#FF9500")
            ),
            null, Shader.TileMode.CLAMP
        )
        spikePaint.shader = gradient
    }

    /**
     * MASTER DRAWING LOGIC: 60 FPS Hassasiyetinde Çizim.
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Veri yoksa çizimi atla (Performans tasarrufu)
        if (amplitudes.isEmpty()) {
            drawIdleState(canvas)
            return
        }

        val centerY = height / 2f
        val totalWidth = width.toFloat()

        // Çubuklar arası dinamik boşluk hesabı
        val dynamicSpacing = totalWidth / maxSpikes

        // Her bir genliği (Amplitude) ekrana çiz
        amplitudes.forEachIndexed { index, amplitude ->

            // X koordinatı (Soldan sağa akar)
            val x = index * dynamicSpacing

            // Apple Stili Simetrik Çizim:
            // Çizgi merkezden yukarı ve aşağı eşit miktarda uzar.
            val top = centerY - (amplitude / 2f)
            val bottom = centerY + (amplitude / 2f)

            // Çubuğu oluştur (Squircle esintili yuvarlak köşeler)
            val rectF = RectF(
                x,
                top,
                x + spikeWidth,
                bottom
            )

            // Çizimi gerçekleştir
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, spikePaint)
        }

        // Estetik: Merkezdeki ince baz çizgisi
        canvas.drawLine(0f, centerY, width.toFloat(), centerY, baselinePaint)
    }

    /**
     * IDLE STATE: Ses yokken şık bir düz çizgi gösterir.
     */
    private fun drawIdleState(canvas: Canvas) {
        val centerY = height / 2f
        canvas.drawLine(32f, centerY, width.toFloat() - 32f, centerY, baselinePaint)

        // Boştayken bile 10 adet minik nokta göstererek "Hazır" mesajı ver
        val dotPaint = Paint(spikePaint).apply {
            shader = null
            color = Color.parseColor("#33FFFFFF")
        }

        for (i in 0..10) {
            canvas.drawCircle(
                (width / 10f) * i,
                centerY,
                4f,
                dotPaint
            )
        }
    }

    /**
     * STATE SAVING: Activity dönüştüğünde veriyi koru.
     */
    override fun onSaveInstanceState(): Parcelable? {
        val bundle = Bundle()
        bundle.putParcelable("superState", super.onSaveInstanceState())
        // Amplitudes listesini saklayabiliriz ama canlı veri olduğu için
        // genellikle temiz başlamak daha iyidir.
        return bundle
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        var viewState = state
        if (viewState is Bundle) {
            viewState = viewState.getParcelable("superState")
        }
        super.onRestoreInstanceState(viewState)
    }

    // --- YARDIMCI FONKSİYONLAR ---
    fun setSpikeColor(color: Int) {
        this.spikeColor = color
        invalidate()
    }
}