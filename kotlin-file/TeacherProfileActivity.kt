package com.example.akillidersasistani

import android.app.Activity
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.net.Uri
import android.os.*
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat.startActivityForResult
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.ContextCompat.startActivity
import com.example.akillidersasistani.databinding.ActivityTeacherProfileBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.database.FirebaseDatabase
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class TeacherProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTeacherProfileBinding
    private var selectedImageUri: Uri? = null
    private var isEditing: Boolean = false
    private val db = FirebaseDatabase.getInstance().reference
    private var selectedGender: String = "" // Bunu ekle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. ÖNCE BINDING NESNESINI OLUŞTUR (Hata buradaydı)
        binding = ActivityTeacherProfileBinding.inflate(layoutInflater)

        // 2. SONRA ROOT'U SET ET
        setContentView(binding.root)

        // 3. ŞİMDİ INTENT VERİLERİNİ ALABİLİRSİN
        isEditing = intent.getBooleanExtra("isEditing", false)

        // 4. SON OLARAK GÖRÜNÜMLERİ VE VERİLERİ YÜKLE
        initViews()
        loadExistingData()
    }    private fun loadExistingData() {
        val sharedPref = getSharedPreferences("TeacherProfile", Context.MODE_PRIVATE)
        val savedName = sharedPref.getString("teacher_name", "")
        val savedGender = sharedPref.getString("teacher_gender", "")
        val savedPhoto = sharedPref.getString("teacher_photo", null)

        // HATA ÇÖZÜMÜ: AnnotatedString hatası import silinince düzelecektir
        binding.edtTeacherNameEdit.setText(savedName)
        binding.txtTeacherNameHeader.text = if (savedName.isNullOrEmpty()) "Eğitmen" else savedName

        selectedGender = sharedPref.getString("teacher_gender", "") ?: ""
        // Cinsiyet Görünümünü Güncelle (Adım 6)
        if (savedGender == "erkek") {
            binding.txtGenderSelection.text = "Erkek 🟦"
            binding.txtGenderSelection.setTextColor(android.graphics.Color.parseColor("#007AFF"))
        } else if (savedGender == "kadin") {
            binding.txtGenderSelection.text = "Kadın 🟥"
            binding.txtGenderSelection.setTextColor(android.graphics.Color.parseColor("#FF2D55"))
        }

        // Fotoğraf Yükleme
        val photoPath = sharedPref.getString("teacher_photo", null)
        if (!photoPath.isNullOrEmpty()) {
            val file = File(photoPath)
            if (file.exists()) {
                binding.imgTeacherPhotoMain.setImageURI(Uri.fromFile(file))
            }
        }
    }

    private fun initViews() {
        // 1. Profil Resmi Seçme (Squircle Kart)
        binding.cardProfileImageContainer.setOnClickListener {
            triggerHapticFeedback(50)
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivityForResult(intent, 100)
        }
        binding.btnSaveProfile.setOnClickListener { saveProfileData() }
        binding.btnGenderRow.setOnClickListener {
            triggerHapticFeedback(40)
            showGenderPickerDialog()
        }
        binding.btnTeacherSecurity.setOnClickListener {
            val intent = Intent(this, TeacherChangePasswordActivity::class.java)
            startActivity(intent)
        }

        // 3. Geri Butonu
        binding.btnBackToDashboard.setOnClickListener {
            triggerHapticFeedback(20)
            finish()
        }


        // 5. Oturumu Kapat (Mükerrer kod silindi, tek blok yapıldı)
        binding.btnTeacherLogout.setOnClickListener {
            triggerHapticFeedback(100)
            // SharedPreferences temizleme ve Main'e dönüş
            getSharedPreferences("TeacherProfile", MODE_PRIVATE).edit().clear().apply()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun showGenderPickerDialog() {
        // R.style.TransparentDialogRes stilini kullanmanız, köşelerin beyaz kalmasını engeller
        val dialog = BottomSheetDialog(this, R.style.TransparentDialogRes)
        val view = layoutInflater.inflate(R.layout.dialog_gender_picker, null)
        dialog.setContentView(view)

        val btnMale = view.findViewById<View>(R.id.btnGenderMale)
        val btnFemale = view.findViewById<View>(R.id.btnGenderFemale)

        btnMale?.setOnClickListener {
            triggerHapticFeedback(50)
            selectedGender = "erkek"
            binding.txtGenderSelection.text = "Erkek 🟦"
            binding.txtGenderSelection.setTextColor(android.graphics.Color.parseColor("#007AFF"))
            dialog.dismiss()
        }

        btnFemale?.setOnClickListener {
            triggerHapticFeedback(50)
            selectedGender = "kadin"
            binding.txtGenderSelection.text = "Kadın 🟥"
            binding.txtGenderSelection.setTextColor(android.graphics.Color.parseColor("#FF2D55"))
            dialog.dismiss()
        }

        dialog.show()
    }
    private fun saveProfileData() {
        val name = binding.edtTeacherNameEdit.text.toString().trim()
        val branch = "" // Eğer branş edittext'in varsa buraya ekle: binding.edtTeacherBranch.text.toString()

        if (name.isEmpty()) {
            Toast.makeText(this, "Lütfen adınızı girin", Toast.LENGTH_SHORT).show()
            return
        }

        val sharedPref = getSharedPreferences("TeacherProfile", Context.MODE_PRIVATE)
        val username = sharedPref.getString("teacher_username", "") ?: ""

        // 1. ADIM: Yerel Hafızaya (SharedPreferences) Kaydet
        val editor = sharedPref.edit()
        editor.putString("teacher_name", name)
        editor.putString("teacher_gender", selectedGender)

        selectedImageUri?.let { uri ->
            val permanentPath = saveImageToInternalStorage(uri)
            if (permanentPath != null) {
                editor.putString("teacher_photo", permanentPath)
            }
        }
        editor.putBoolean("isProfileComplete", true)
        editor.apply()

        // 2. ADIM: Firebase Veritabanına Kaydet (Eğitmen adı burada güncellenir)
        if (username.isNotEmpty()) {
            val updates = mapOf(
                "name" to name,
                "gender" to selectedGender,
                "isProfileComplete" to true
            )

            db.child("teachers").child(username).updateChildren(updates)
                .addOnSuccessListener {
                    Toast.makeText(this, "Profil Güncellendi ✅", Toast.LENGTH_SHORT).show()

                    // 3. ADIM: Her zaman TeacherActivity'ye (Öğretmen Paneli) yönlendir
                    val intent = Intent(this, TeacherActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Veritabanı hatası!", Toast.LENGTH_SHORT).show()
                }
        } else {
            // Kullanıcı adı bulunamazsa bile ana ekrana dön
            startActivity(Intent(this, TeacherActivity::class.java))
            finish()
        }
    }
    private fun saveImageToInternalStorage(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val file = File(filesDir, "teacher_profile_${System.currentTimeMillis()}.jpg")
            val outputStream = FileOutputStream(file)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                selectedImageUri = uri
                binding.imgTeacherPhotoMain.setImageURI(uri)
            } catch (e: Exception) {
                selectedImageUri = uri
                binding.imgTeacherPhotoMain.setImageURI(uri)
            }
        }
    }

    private fun triggerHapticFeedback(ms: Long) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(ms)
        }
    }  }
