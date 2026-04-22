    package com.example.akillidersasistani

    import android.animation.ArgbEvaluator
    import android.animation.ObjectAnimator
    import android.app.Activity
    import android.content.Context
    import android.content.Intent
    import android.graphics.Color
    import android.os.*
    import android.text.Editable
    import android.text.TextWatcher
    import android.view.View
    import android.view.animation.CycleInterpolator
    import android.widget.Toast
    import androidx.appcompat.app.AppCompatActivity
    import androidx.compose.ui.semantics.text
    import com.example.akillidersasistani.databinding.ActivityTeacherChangePasswordBinding
    import com.google.firebase.auth.FirebaseAuth
    import com.google.firebase.database.FirebaseDatabase
    import java.util.Locale


    class TeacherChangePasswordActivity : AppCompatActivity() {

        private lateinit var binding: ActivityTeacherChangePasswordBinding
        private val db = FirebaseDatabase.getInstance().reference
        private var isUpdating = false

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            // 1. ViewBinding & Layout Initialization
            binding = ActivityTeacherChangePasswordBinding.inflate(layoutInflater)
            setContentView(binding.root)

            // 2. iOS UI Setup
            setupAppleUI()

            // 3. Real-time Security Analysis
            setupPasswordStrengthMonitor()

            // 4. Click Listeners
            setupActionListeners()
        }

        private fun setupAppleUI() {
            // iOS Modal Entrance Animation
            binding.root.alpha = 0f
            binding.root.animate().alpha(1f).setDuration(400).start()

            // Şifre Gücü Başlangıç Rengi
            binding.passwordStrengthProgress.setIndicatorColor(Color.parseColor("#FF3B30")) // iOS Red
        }
        // btnSavePassword.setOnClickListener içine gelecek mantık:
        private fun changeTeacherPassword() {
            val oldPass = binding.edtOldPassword.text.toString()
            val newPass = binding.edtNewPassword.text.toString()
            val confirmPass = binding.edtNewPasswordConfirm.text.toString()

            if (newPass != confirmPass) {
                Toast.makeText(this, "Yeni şifreler uyuşmuyor!", Toast.LENGTH_SHORT).show()
                return
            }

            if (newPass.length < 6) {
                Toast.makeText(this, "Şifre en az 6 karakter olmalı!", Toast.LENGTH_SHORT).show()
                return
            }

            val user = FirebaseAuth.getInstance().currentUser
            val pref = getSharedPreferences("TeacherProfile", MODE_PRIVATE)
            val username = pref.getString("teacher_username", "") ?: ""

            // 1. Firebase Auth Şifresini Güncelle
            user?.updatePassword(newPass)?.addOnCompleteListener { task ->
                if (task.isSuccessful) {

                    // 2. Firebase Realtime Database'deki şifreyi de güncelle (Giriş için)
                    val db = FirebaseDatabase.getInstance().reference
                    db.child("teachers").child(username).child("password").setValue(newPass)
                        .addOnSuccessListener {

                            // 3. Başarılı bildirimi ver
                            Toast.makeText(this, "Şifreniz başarıyla güncellendi ✅", Toast.LENGTH_LONG).show()

                            // 4. Öğretmen Paneline Geri Gönder
                            val intent = Intent(this, TeacherActivity::class.java)
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP) // Eski sayfaları kapat
                            startActivity(intent)
                            finish() // Bu sayfayı kapat
                        }
                } else {
                    Toast.makeText(this, "Hata: Mevcut şifreniz yanlış olabilir veya oturum süresi dolmuş.", Toast.LENGTH_LONG).show()
                }
            }
        }
        private fun setupActionListeners() {
            // Vazgeç Butonu
            binding.btnCancelPassword.setOnClickListener {
                triggerHapticFeedback(20)
                finish()
            }

            // Bitti (Kaydet) Butonu
            binding.btnSavePassword.setOnClickListener {
                // validateInputs() içinde edtNewPasswordConfirm kullandığın için
                // hata almamak adına önce girdileri kontrol ediyoruz
                if (validateInputs()) {
                    // gri rengi gidermek için fonksiyonu burada çağırıyoruz
                    changeTeacherPassword()
                }
            }
        }

        private fun setupPasswordStrengthMonitor() {
            binding.edtNewPassword.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val password = s.toString()
                    updateStrengthUI(password)
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }

        private fun updateStrengthUI(password: String) {
            var score = 0
            if (password.length >= 8) score += 25
            if (password.any { it.isUpperCase() }) score += 25
            if (password.any { it.isDigit() }) score += 25
            if (password.any { "!@#$%^&*()_+".contains(it) }) score += 25

            // Progress Bar Animasyonu
            binding.passwordStrengthProgress.setProgress(score, true)

            // iOS Color States
            val color = when {
                score <= 25 -> "#FF3B30" // Red
                score <= 50 -> "#FF9500" // Orange
                score <= 75 -> "#FFCC00" // Yellow
                else -> "#34C759"        // iOS Green (Strong)
            }

            binding.passwordStrengthProgress.setIndicatorColor(Color.parseColor(color))

            // "Bitti" butonunun aktiflik durumu (Alpha animasyonu)
            if (score >= 75) {
                binding.btnSavePassword.animate().alpha(1.0f).setDuration(200).start()
                binding.btnSavePassword.isClickable = true
            } else {
                binding.btnSavePassword.animate().alpha(0.5f).setDuration(200).start()
                binding.btnSavePassword.isClickable = false
            }
        }

        private fun validateInputs(): Boolean {
            val oldPass = binding.edtOldPassword.text.toString()
            val newPass = binding.edtNewPassword.text.toString()
            val confirmPass = binding.edtNewPasswordConfirm.text.toString()

            val sharedPref = getSharedPreferences("TeacherProfile", Context.MODE_PRIVATE)
            val currentSavedPass = sharedPref.getString("teacher_password", "")

            // 1. Mevcut Şifre Kontrolü
            if (oldPass != currentSavedPass) {
                triggerHapticFeedback(100)
                performGlitchEffect(binding.edtOldPassword)
                showAppleAlert("Mevcut şifre hatalı.")
                return false
            }

            // 2. Yeni Şifre Eşleşme Kontrolü
            if (newPass != confirmPass) {
                performGlitchEffect(binding.edtNewPasswordConfirm)
                showAppleAlert("Şifreler birbiriyle eşleşmiyor.")
                return false
            }

            return true
        }

        private fun executeSecureTransformation() {
            if (isUpdating) return
            isUpdating = true

            // UI Feedback
            binding.btnSavePassword.text = "Bekleyin..."
            triggerHapticFeedback(50)

            val newPass = binding.edtNewPassword.text.toString()
            val sharedPref = getSharedPreferences("TeacherProfile", Context.MODE_PRIVATE)
            val username = sharedPref.getString("teacher_username", "unknown") ?: "unknown"

            val safeId = username.replace(".", "_").replace(" ", "_").lowercase(Locale.ROOT)

            // Firebase Cloud Sync
            db.child("teachers").child(safeId).child("security").child("password").setValue(newPass)
                .addOnSuccessListener {
                    // Yerel Güncelleme
                    sharedPref.edit().putString("teacher_password", newPass).apply()

                    successAnimation()
                }
                .addOnFailureListener {
                    isUpdating = false
                    binding.btnSavePassword.text = "Bitti"
                    showAppleAlert("Bağlantı hatası oluştu.")
                }
        }

        private fun successAnimation() {
            triggerHapticFeedback(150)
            Toast.makeText(this, "✅ Şifreniz Güvenle Güncellendi", Toast.LENGTH_SHORT).show()

            Handler(Looper.getMainLooper()).postDelayed({
                val resultIntent = Intent()
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }, 1000)
        }

        // --- PREMIUM UI EFFECTS ---

        private fun performGlitchEffect(view: View) {
            ObjectAnimator.ofFloat(view, "translationX", 0f, 15f, -15f, 15f, -15f, 0f).apply {
                duration = 400
                interpolator = CycleInterpolator(2f)
                start()
            }
        }

        private fun triggerHapticFeedback(ms: Long) {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(ms)
            }
        }

        private fun showAppleAlert(message: String) {
            // Custom iOS Toast Style
            Toast.makeText(this, " $message", Toast.LENGTH_SHORT).show()
        }    }

