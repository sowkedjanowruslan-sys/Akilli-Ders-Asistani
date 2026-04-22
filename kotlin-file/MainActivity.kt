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

        val btnTeacher = dialogView.findViewById<MaterialButton>(R.id.btnSelectTeacher)
        val btnStudent = dialogView.findViewById<MaterialButton>(R.id.btnSelectStudent)

        btnTeacher?.setOnClickListener {
            saveUserRole("teacher")
            dialog.dismiss()
            startActivity(Intent(this, TeacherRegisterActivity::class.java))
            finish()
        }

        btnStudent?.setOnClickListener {
            saveUserRole("student")
            dialog.dismiss()
            startActivity(Intent(this, StudentProfileActivity::class.java))
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

        // 2. MaterialAlertDialogBuilder kullanarak diyaloğu oluştur
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        // 3. Arka planın şeffaf olması ve ekran boyutuna düzgün oturması için pencere ayarları
        dialog.show() // Önce show() demeliyiz ki window nesnesi oluşsun
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Ekranın %90'ını kaplaması için genişlik ayarı (Beyaz çubuk hatasını kesin çözer)
        val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        dialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)

        // 4. Bileşenleri bul ve işlemleri yap
        val tabTeacher = dialogView.findViewById<ImageButton>(R.id.tabTeacher)
        val tabStudent = dialogView.findViewById<ImageButton>(R.id.tabStudent)
        val contentTeacher = dialogView.findViewById<LinearLayout>(R.id.guideContentTeacher)
        val contentStudent = dialogView.findViewById<LinearLayout>(R.id.guideContentStudent)
        val btnFinish = dialogView.findViewById<Button>(R.id.btnGuideFinish)

        tabTeacher?.setOnClickListener {
            triggerHapticFeedback(30)
            tabTeacher.setBackgroundResource(R.drawable.bg_ios_round_button)
            tabTeacher.setColorFilter(android.graphics.Color.parseColor("#007AFF"))
            tabStudent?.setBackgroundResource(android.R.color.transparent) // .setBackgroundColor yerine .setBackgroundResource daha güvenlidir
            tabStudent?.setColorFilter(android.graphics.Color.parseColor("#8E8E93"))
            contentTeacher?.visibility = View.VISIBLE
            contentStudent?.visibility = View.GONE
        }

        tabStudent?.setOnClickListener {
            triggerHapticFeedback(30)
            tabStudent.setBackgroundResource(R.drawable.bg_ios_round_button)
            tabStudent.setColorFilter(android.graphics.Color.parseColor("#34C759"))
            tabTeacher?.setBackgroundResource(android.R.color.transparent)
            tabTeacher?.setColorFilter(android.graphics.Color.parseColor("#8E8E93"))
            contentStudent?.visibility = View.VISIBLE
            contentTeacher?.visibility = View.GONE
        }

        btnFinish?.setOnClickListener {
            triggerHapticFeedback(100)
            // Bir daha göstermemek için tercihi kaydet
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