package com.example.akillidersasistani

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.*
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.akillidersasistani.databinding.ActivityTeacherRegisterBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import java.util.*

class TeacherRegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTeacherRegisterBinding
    private val db = FirebaseDatabase.getInstance().reference
    private var isBooting = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // KRİTİK DÜZELTME: ViewBinding başlatma
        binding = ActivityTeacherRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root) // R.layout... yerine binding.root gelmeli!

        startHackerEffect()
        setupListeners()

        // İlk açılışta bilgilendirme penceresini göster
        showWelcomeInstructions()
    }

    private fun setupListeners() {
        // Geri Butonu
        binding.btnBack.setOnClickListener {
            triggerHaptic(20)
            finish()
        }

        // Giriş Yap Linki
        binding.txtLoginLink.setOnClickListener {
            finish()
        }

        // Şifre Göster/Gizle (Göz İkonu)
        setupPasswordEye(binding.imgEye1, binding.edtRegisterPassword)

        // Kayıt Butonu
        binding.btnSaveAccount.setOnClickListener {
            if (isBooting) {
                Toast.makeText(this, "Sistem yükleniyor...", Toast.LENGTH_SHORT).show()
            } else {
                handleRegistration()
            }
        }
    }

    private fun showWelcomeInstructions() {
        // iOS Stili Bilgilendirme Diyaloğu
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_user_selection, null)
        val title = dialogView.findViewById<TextView>(R.id.txtDialogTitle) ?: TextView(this)

        MaterialAlertDialogBuilder(this)
            .setTitle("👨‍🏫 Eğitmen Kayıt Rehberi")
            .setMessage("Hoş geldiniz! Kayıt olmak için:\n\n" +
                    "1. Ad Soyadınızı girin.\n" +
                    "2. Uzmanlık alanınızı (Bölüm) belirtin.\n" +
                    "3. En az 8 karakterli güvenli bir şifre oluşturun.\n\n" +
                    "Bilgileriniz uçtan uca şifrelenerek Smart Class Connect sistemine kaydedilecektir.")
            .setPositiveButton("Anladım, Başla") { dialog, _ ->
                dialog.dismiss()
                triggerHaptic(40)
            }
            .setCancelable(false)
            .show()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupPasswordEye(eye: ImageView, editText: EditText) {
        eye.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    triggerHaptic(40)
                    editText.transformationMethod = HideReturnsTransformationMethod.getInstance()
                    eye.setColorFilter(Color.parseColor("#007AFF"))
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    editText.transformationMethod = PasswordTransformationMethod.getInstance()
                    eye.setColorFilter(Color.parseColor("#C7C7CC"))
                    true
                }
                else -> false
            }
        }
    }

    private fun handleRegistration() {
        val user = binding.edtRegisterUsername.text.toString().trim()
        val dept = binding.edtDepartment.text.toString().trim()
        val pass = binding.edtRegisterPassword.text.toString()

        if (user.isEmpty() || dept.isEmpty() || pass.length < 6) {
            Toast.makeText(this, "Lütfen tüm alanları doğru doldurun!", Toast.LENGTH_SHORT).show()
            return
        }

        val safeId = user.replace(".", "_").replace(" ", "_").lowercase(Locale.ROOT)

        binding.btnSaveAccount.isEnabled = false
        binding.btnSaveAccount.text = "KAYDEDİLİYOR..."

        val teacherData = mapOf(
            "username" to user,
            "department" to dept,
            "role" to "teacher",
            "created_at" to ServerValue.TIMESTAMP
        )

        db.child("teachers").child(safeId).child("profile").setValue(teacherData)
            .addOnSuccessListener {
                finalizeRegistration(user, pass, dept, safeId)
            }
            .addOnFailureListener {
                binding.btnSaveAccount.isEnabled = true
                binding.btnSaveAccount.text = "TEKRAR DENE"
                Toast.makeText(this, "Bağlantı Hatası!", Toast.LENGTH_SHORT).show()
            }
    }

    // Parametre sayısını 3'ten 4'e çıkardık (safeId eklendi)
    private fun finalizeRegistration(username: String, pass: String, dept: String, safeId: String) {
        val pref = getSharedPreferences("TeacherProfile", Context.MODE_PRIVATE)

        pref.edit().apply {
            putString("teacher_username", username)
            putString("teacher_safe_id", safeId) // Artık hata vermez
            putString("teacher_password", pass)
            putString("teacher_department", dept)
            putBoolean("isLoggedIn", true)
            apply()
        }

        val intent = Intent(this, TeacherProfileActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun startHackerEffect() {
        val text = "Yeni Kayıt\nOluşturun"
        binding.txtBigTitle.text = ""
        var i = 0
        val handler = Handler(Looper.getMainLooper())
        handler.post(object : Runnable {
            override fun run() {
                if (i <= text.length) {
                    binding.txtBigTitle.text = text.substring(0, i++)
                    handler.postDelayed(this, 50)
                } else {
                    isBooting = false
                }
            }
        })
    }

    private fun triggerHaptic(ms: Long) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(ms)
        }
    }
}
