package com.example.akillidersasistani

import android.content.Context
import android.content.Intent
import android.os.*
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.akillidersasistani.databinding.ActivityMainBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- 1. HAFIZA KONTROLÜ ---
        val sharedPref = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val userRole = sharedPref.getString("user_role", null)

        if (userRole != null) {
            goToDashboard(userRole)
            return
        }

        // --- 2. TASARIM YÜKLEME ---
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // --- 3. REHBER KONTROLÜ ---
        val pref = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        val isFirstTime = pref.getBoolean("isFirstTimeUsage", true)

        if (isFirstTime) {
            showUsageGuide()
            // Bir daha gösterme demek istersen alttaki satırı aç:
            // pref.edit().putBoolean("isFirstTimeUsage", false).apply()
        }

        // --- 4. BUTONLAR ---
        binding.btnNext.setOnClickListener {
            showUserTypeSelectionDialog()
        }

        binding.txtLoginLink.setOnClickListener {
            // İhtiyaç varsa login ekranına yönlendir
        }
    }

    private fun showUserTypeSelectionDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_user_selection, null)

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // 1. Yeni ID'lere göre referansları alıyoruz (MaterialButton yerine MaterialCardView)
        val cardTeacher = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardSelectTeacher)
        val cardStudent = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardSelectStudent)

// 2. Eğitmen Kartı Tıklama Dinleyicisi
        cardTeacher?.setOnClickListener {
            saveUserRole("teacher")
            dialog.dismiss()
            // Premium geçiş efekti ile başlat
            val intent = Intent(this, TeacherRegisterActivity::class.java)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }

// 3. Öğrenci Kartı Tıklama Dinleyicisi
        cardStudent?.setOnClickListener {
            saveUserRole("student")
            dialog.dismiss()
            // Premium geçiş efekti ile başlat
            val intent = Intent(this, StudentProfileActivity::class.java)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }

        dialog.show()
    }

    private fun saveUserRole(role: String) {
        val sharedPref = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        sharedPref.edit().putString("user_role", role).apply()
    }

    private fun showUsageGuide() {
        // 1. View'ı inflate et
        val inflater = layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_usage_guide, null)

        // 2. Diyaloğu oluştur
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Ekran genişliği ayarı
        val width = (resources.displayMetrics.widthPixels * 0.95).toInt()
        dialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)

        // 4. BİLEŞENLERİ BUL (Kritik Düzeltme: ImageButton yerine MaterialButton)
        val tabTeacher = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.tabTeacher)
        val tabStudent = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.tabStudent)
        val contentTeacher = dialogView.findViewById<LinearLayout>(R.id.guideContentTeacher)
        val contentStudent = dialogView.findViewById<LinearLayout>(R.id.guideContentStudent)
        val btnFinish = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnGuideFinish)

        // --- EĞİTMEN TAB TIKLAMA ---
        tabTeacher?.setOnClickListener {
            triggerHapticFeedback(30)

            // Aktif Sekme Görünümü (Beyaz arka plan, koyu yazı)
            tabTeacher.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE))
            tabTeacher.setTextColor(android.graphics.Color.parseColor("#1C1C1E"))

            // Pasif Sekme Görünümü (Şeffaf arka plan, gri yazı)
            tabStudent?.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT))
            tabStudent?.setTextColor(android.graphics.Color.parseColor("#8E8E93"))

            // İçerik Değişimi
            contentTeacher?.visibility = View.VISIBLE
            contentStudent?.visibility = View.GONE
        }

        // --- ÖĞRENCİ TAB TIKLAMA ---
        tabStudent?.setOnClickListener {
            triggerHapticFeedback(30)

            // Aktif Sekme Görünümü
            tabStudent.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE))
            tabStudent.setTextColor(android.graphics.Color.parseColor("#1C1C1E"))

            // Pasif Sekme Görünümü
            tabTeacher?.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT))
            tabTeacher?.setTextColor(android.graphics.Color.parseColor("#8E8E93"))

            // İçerik Değişimi
            contentStudent?.visibility = View.VISIBLE
            contentTeacher?.visibility = View.GONE
        }

        // --- BİTİR BUTONU ---
        btnFinish?.setOnClickListener {
            triggerHapticFeedback(100)
            getSharedPreferences("AppPrefs", MODE_PRIVATE).edit()
                .putBoolean("isFirstTimeUsage", false).apply()
            dialog.dismiss()
        }
    }
    private fun goToDashboard(role: String) {
        val intent = if (role == "teacher") {
            Intent(this, TeacherActivity::class.java)
        } else {
            Intent(this, StudentActivity::class.java)
        }
        startActivity(intent)
        finish()
    }

    private fun triggerHapticFeedback(ms: Long) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(ms)
        }
    }
}