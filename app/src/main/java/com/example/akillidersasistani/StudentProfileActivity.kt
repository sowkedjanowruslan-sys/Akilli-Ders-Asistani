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
import com.google.android.material.card.MaterialCardView

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
        binding = ActivityStudentProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isFromMain = intent.getBooleanExtra("from_main", false)

        initViews()
        loadExistingData() // Önemli: Önce verileri yükle
    }

    private fun initViews() {
        binding.btnBackProfile.setOnClickListener { finish() }

        binding.cardProfileImageContainer.setOnClickListener {
            triggerHapticFeedback(40)
            openImagePicker()
        }

        binding.btnGenderRow.setOnClickListener { showGenderPickerDialog() }

        binding.btnStudentSave.setOnClickListener {
            saveStudentData()
        }

        // --- Dinamik UI Güncellemeleri ---
        binding.edtStudentName.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                binding.txtStudentNameDisplay.text = if (s.isNullOrEmpty()) "Kullanıcı Adı" else s.toString()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.edtStudentNo.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                binding.txtStudentIdDisplay.text = if (s.isNullOrEmpty()) "Öğrenci No: 0000" else "Öğrenci No: $s"
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

        val dialog = BottomSheetDialog(this, R.style.CustomBottomSheetDialogTheme)
        val view = layoutInflater.inflate(R.layout.dialog_gender_picker, null)
        dialog.setContentView(view)

        val btnMale = view.findViewById<View>(R.id.btnGenderMale)
        val btnFemale = view.findViewById<View>(R.id.btnGenderFemale)
        val btnOther = view.findViewById<MaterialCardView>(R.id.btnGenderOther) // YENİ EKLEDİĞİN

        btnMale?.setOnClickListener {
            selectedGender = "erkek"
            triggerHapticFeedback(30) // Hafif dokunuş hissi

            // Ana ekrandaki görseli güncelle
            binding.txtGenderSelection.text = "Erkek ♂️"
            binding.txtGenderSelection.setTextColor(Color.parseColor("#1E40AF")) // Premium Koyu Mavi

            dialog.dismiss()
        }
        btnOther?.setOnClickListener {
            selectedGender = "diğer"
            triggerHapticFeedback(30)

            // Ana ekrandaki görseli güncelle (Neural Studio İndigo Rengi)
            binding.txtGenderSelection.text = "Belirtilmedi ✨"
            binding.txtGenderSelection.setTextColor(Color.parseColor("#4F46E5")) // Neural İndigo

            // İstersen fotoğrafı da nötr yapabilirsin:
            // binding.imgAvatar.setImageResource(R.drawable.ic_anonymous_user)

            dialog.dismiss()
        }
        btnFemale?.setOnClickListener {
            selectedGender = "kadin"
            triggerHapticFeedback(30)

            // Ana ekrandaki görseli güncelle
            binding.txtGenderSelection.text = "Kadın ♀️"
            binding.txtGenderSelection.setTextColor(Color.parseColor("#9D174D")) // Premium Koyu Pembe/Vişne

            dialog.dismiss()
        }
        dialog.show()
    }

    private fun saveStudentData() {
        val name = binding.edtStudentName.text.toString().trim()
        val no = binding.edtStudentNo.text.toString().trim()
        val dept = binding.edtStudentDept.text.toString().trim()

        if (name.isEmpty() || no.isEmpty() || selectedGender.isEmpty()) {
            Toast.makeText(this, "Lütfen tüm alanları doldurun!", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = getSharedPreferences(PREFS_USER, Context.MODE_PRIVATE).edit()

        // KRİTİK: Buradaki anahtarlar (name, ogrenci_no, bolum)
        // StudentActivity'deki getString'ler ile BİREBİR aynı olmalı!
        prefs.putString("name", name)
        prefs.putString("ogrenci_no", no)
        prefs.putString("bolum", dept)
        prefs.putString("gender", selectedGender)

        selectedImageUri?.let { uri ->
            val permanentPath = saveImageToInternalStorage(uri)
            if (permanentPath != null) {
                prefs.putString("student_photo", permanentPath)
            }
        }
        prefs.apply()

        registerUniqueStudent(no)

        if (isFromMain) {
            finish()
        } else {
            val intent = Intent(this, StudentActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
    private fun loadExistingData() {
        val prefs = getSharedPreferences(PREFS_USER, Context.MODE_PRIVATE)

        // 1. Önce EditText'leri doldur
        val savedName = prefs.getString("name", "")
        val savedNo = prefs.getString("ogrenci_no", "")
        val savedDept = prefs.getString("bolum", "")

        binding.edtStudentName.setText(savedName)
        binding.edtStudentNo.setText(savedNo)
        binding.edtStudentDept.setText(savedDept)

        // 2. Şimdi üstteki kartvizit görünümlerini güncelle
        binding.txtStudentNameDisplay.text = if (savedName.isNullOrEmpty()) "Kullanıcı Adı" else savedName
        binding.txtStudentIdDisplay.text = if (savedNo.isNullOrEmpty()) "Öğrenci No: 0000" else "Öğrenci No: $savedNo"

        // Cinsiyet Ayarı
        selectedGender = prefs.getString("gender", "") ?: ""
        updateGenderUI(selectedGender)

        // Fotoğraf Ayarı
        val photoPath = prefs.getString("student_photo", null)
        if (!photoPath.isNullOrEmpty()) {
            binding.imgStudentPhoto.setImageURI(Uri.fromFile(File(photoPath)))
            binding.imgStudentPhoto.scaleType = ImageView.ScaleType.CENTER_CROP
        }
    }
    private fun updateGenderUI(gender: String) {
        if (gender == "erkek") {
            binding.txtGenderSelection.text = "Erkek 🟦"
            binding.txtGenderSelection.setTextColor(Color.parseColor("#007AFF"))
        } else if (gender == "kadin") {
            binding.txtGenderSelection.text = "Kadın 🟥"
            binding.txtGenderSelection.setTextColor(Color.parseColor("#FF2D55"))
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
