package com.example.akillidersasistani

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.akillidersasistani.databinding.ActivityStudentProfileBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.database.*
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import android.text.Editable // Doğru olan bu
import android.text.TextWatcher // Bunu da kontrol et
class StudentProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentProfileBinding
    private val db = FirebaseDatabase.getInstance().reference

    private var selectedImageUri: Uri? = null
    private var isFromMain: Boolean = false
    private var selectedGender: String = "" // "erkek" veya "kadin"

    // Sabitler
    private companion object {
        const val PREFS_USER = "UserProfile"
        const val IMAGE_PICK_CODE = 100
        const val TAG = "StudentProfile"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. ViewBinding Kurulumu
        binding = ActivityStudentProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Giriş kaynağı kontrolü (Main'den mi gelindi yoksa Student Panel'den mi?)
        isFromMain = intent.getBooleanExtra("from_main", false)

        // 2. Arayüz Kurulumu
        initViews()

        // 3. Mevcut Verileri Yükle
        loadExistingData()
    }

    private fun initViews() {
        // iOS Style Geri Butonu
        binding.btnBackProfile.setOnClickListener {
            triggerHapticFeedback(20)
            finish()
        }

        // Apple Squircle Fotoğraf Seçici (cardProfileImageContainer XML'de MaterialCardView olmalı)
        binding.cardProfileImageContainer.setOnClickListener {
            triggerHapticFeedback(40)
            openImagePicker()
        }

        // ADIM 6: Profesyonel Cinsiyet Seçici (Satıra tıklandığında açılır)
        // Not: XML'de id: btnGenderRow olan bir RelativeLayout ve id: txtGenderSelection olan bir TextView olmalı
        binding.btnGenderRow.setOnClickListener {
            showGenderPickerDialog()
        }

        // Kaydetme Butonu (Step 5: Çökme Korumalı)
        binding.btnStudentSave.setOnClickListener {
            triggerHapticFeedback(60)
            saveStudentData()
        }
        // İsim anlık değişsin
        binding.edtStudentName.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                binding.txtStudentNameDisplay.text = if (s.isNullOrEmpty()) "Kullanıcı Adı" else s.toString()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

// Numara anlık değişsin
        // ... üst kısımlar aynı
        binding.edtStudentNo.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { // 'android.text.' ekledik
                binding.txtStudentIdDisplay.text = if (s.isNullOrEmpty()) {
                    "Öğrenci No: 0000"
                } else {
                    "Öğrenci No: $s"
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }
    private fun saveImageToInternalStorage(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val file = File(filesDir, "student_profile_${System.currentTimeMillis()}.jpg")
            val outputStream = FileOutputStream(file)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }
    private fun showGenderPickerDialog() {
        val dialog = BottomSheetDialog(this, R.style.TransparentDialogRes)
        val view = layoutInflater.inflate(R.layout.dialog_gender_picker, null)
        dialog.setContentView(view)

        val btnMale = view.findViewById<View>(R.id.btnGenderMale)
        val btnFemale = view.findViewById<View>(R.id.btnGenderFemale)

        btnMale?.setOnClickListener {
            selectedGender = "erkek"
            binding.txtGenderSelection.text = "Erkek 🟦"
            binding.txtGenderSelection.setTextColor(Color.parseColor("#007AFF"))
            dialog.dismiss()
        }

        btnFemale?.setOnClickListener {
            selectedGender = "kadin"
            binding.txtGenderSelection.text = "Kadın 🟥"
            binding.txtGenderSelection.setTextColor(Color.parseColor("#FF2D55"))
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun saveStudentData() {
        val name = binding.edtStudentName.text.toString().trim()
        val no = binding.edtStudentNo.text.toString().trim()
        val dept = binding.edtStudentDept.text.toString().trim()

        // 1. ADIM: Validasyon (Eksik veri kontrolü)
        if (name.isEmpty() || no.isEmpty() || selectedGender.isEmpty()) {
            triggerHapticFeedback(100) // Hata titreşimi
            Toast.makeText(this, "Lütfen isim, numara ve cinsiyeti eksiksiz girin!", Toast.LENGTH_SHORT).show()
            return
        }

        // 2. ADIM: Veri Hazırlığı ve Fotoğrafı Kalıcı Yapma (ADIM 4 & 6)
        val prefs = getSharedPreferences(PREFS_USER, Context.MODE_PRIVATE).edit()
        prefs.putString("name", name)
        prefs.putString("ogrenci_no", no)
        prefs.putString("bolum", dept)
        prefs.putString("gender", selectedGender)

        // Seçilen geçici URI'yi dahili hafızaya kopyalıyoruz (Uri izin kaybolma riskine karşı)
        selectedImageUri?.let { uri ->
            val permanentPath = saveImageToInternalStorage(uri)
            if (permanentPath != null) {
                // Artık SharedPreferences'ta URI değil, dosya yolu (String) saklanıyor
                prefs.putString("student_photo", permanentPath)
            }
        }
        prefs.apply()

        // 3. ADIM: Firebase Sayaç Mantığı (Unique Student Check)
        registerUniqueStudent(no)

        Toast.makeText(this, "✅ Profil Başarıyla Güncellendi", Toast.LENGTH_SHORT).show()

        // 4. ADIM: Sayfa Geçişi ve Stack Temizleme (ADIM 5 - Çökme Koruması)
        if (isFromMain) {
            // Eğer sadece profil güncellemek için ayarlardan gelindiyse
            finish()
        } else {
            // Eğer ilk kayıt/kurulum aşamasındaysa
            val intent = Intent(this, StudentActivity::class.java)
            // FLAG_ACTIVITY_NEW_TASK ve CLEAR_TASK çok önemli: Profil sayfasını tamamen öldürür
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun loadExistingData() {
        val prefs = getSharedPreferences(PREFS_USER, Context.MODE_PRIVATE)
// Fonksiyonun sonuna ekle
        binding.txtStudentNameDisplay.text = binding.edtStudentName.text.ifEmpty { "Kullanıcı Adı" }
        binding.txtStudentIdDisplay.text = if (binding.edtStudentNo.text.isEmpty()) "Öğrenci No: 0000" else "Öğrenci No: ${binding.edtStudentNo.text}"
        binding.edtStudentName.setText(prefs.getString("name", ""))
        binding.edtStudentNo.setText(prefs.getString("ogrenci_no", ""))
        binding.edtStudentDept.setText(prefs.getString("bolum", ""))
        binding.imgStudentPhoto.scaleType = ImageView.ScaleType.CENTER_CROP
        selectedGender = prefs.getString("gender", "") ?: ""
        if (selectedGender == "erkek") {
            binding.txtGenderSelection.text = "Erkek 🟦"
            binding.txtGenderSelection.setTextColor(Color.parseColor("#007AFF"))
        } else if (selectedGender == "kadin") {
            binding.txtGenderSelection.text = "Kadın 🟥"
            binding.txtGenderSelection.setTextColor(Color.parseColor("#FF2D55"))
        }

        val photo = prefs.getString("student_photo", null)
        if (!photo.isNullOrEmpty()) {
            try {
                selectedImageUri = Uri.parse(photo)
                binding.imgStudentPhoto.setImageURI(selectedImageUri)
                binding.imgStudentPhoto.scaleType = ImageView.ScaleType.CENTER_CROP
            } catch (e: Exception) {
                Log.e(TAG, "Fotoğraf yüklenemedi: ${e.message}")
                binding.imgStudentPhoto.setImageResource(android.R.drawable.ic_menu_camera)
            }
        }
    }

    private fun registerUniqueStudent(studentId: String) {
        // Firebase için güvenli ID (Nokta karakteri Firebase yollarında yasaktır)
        val safeId = studentId.replace(".", "_").replace(" ", "_")
        val uniqueRef = db.child("UniqueStudents").child(safeId)

        uniqueRef.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                // Öğrenci sistemde yok, yeni kaydet ve Global sayacı 1 artır
                uniqueRef.setValue(true)
                db.child("GlobalStats/totalStudents").runTransaction(object : Transaction.Handler {
                    override fun doTransaction(currentData: MutableData): Transaction.Result {
                        val currentVal = currentData.getValue(Int::class.java) ?: 0
                        currentData.value = currentVal + 1
                        return Transaction.success(currentData)
                    }
                    override fun onComplete(p0: DatabaseError?, p1: Boolean, p2: DataSnapshot?) {
                        Log.d(TAG, "Global öğrenci sayacı güncellendi.")
                    }
                })
            }
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivityForResult(intent, IMAGE_PICK_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMAGE_PICK_CODE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    selectedImageUri = uri
                    binding.imgStudentPhoto.setImageURI(uri)
                    binding.imgStudentPhoto.scaleType = ImageView.ScaleType.CENTER_CROP
                } catch (e: Exception) {
                    selectedImageUri = uri
                    binding.imgStudentPhoto.setImageURI(uri)
                }
            }
        }
    }

    private fun triggerHapticFeedback(ms: Long) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION") vibrator.vibrate(ms)
        }
    }
}
